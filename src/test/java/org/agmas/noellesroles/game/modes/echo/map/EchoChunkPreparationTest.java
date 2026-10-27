package org.agmas.noellesroles.game.modes.echo.map;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EchoChunkPreparationTest {
    private static final EchoMapConfig.Box AREA=new EchoMapConfig.Box(-64,60,-64,64,90,64);
    private static final class Access implements EchoChunkPreparation.Access {
        final Set<EchoChunkPreparation.Chunk> held=new HashSet<>(),ready=new HashSet<>();
        int requests,releases;
        boolean fail;
        public void request(EchoChunkPreparation.Chunk chunk) {
            requests++; held.add(chunk);
            if(fail) throw new IllegalStateException("backend failure");
        }
        public boolean loaded(EchoChunkPreparation.Chunk chunk) { return ready.contains(chunk); }
        public void release(EchoChunkPreparation.Chunk chunk) { releases++; held.remove(chunk); }
    }
    @Test void planIncludesNegativeChunkEdgesAndRejectsUnboundedAreaBeforeRequesting() {
        var plan=EchoChunkPreparation.plan(new EchoMapConfig.Box(0,0,0,16,10,16));
        assertEquals(9,plan.size());
        assertTrue(plan.contains(new EchoChunkPreparation.Chunk(-1,-1)));
        assertTrue(plan.contains(new EchoChunkPreparation.Chunk(1,1)));
        var backend=new Access();
        assertThrows(IllegalArgumentException.class,() -> new EchoChunkPreparation(
                new EchoMapConfig.Box(-30_000_000,0,-30_000_000,30_000_000,10,30_000_000),backend,0));
        assertEquals(0,backend.requests);
    }
    @Test void loadsIncrementallyWithBoundedPendingAndHoldsUntilClosed() {
        var backend=new Access(); var job=new EchoChunkPreparation(AREA,backend,0);
        assertFalse(job.advance(0));
        assertEquals(EchoChunkPreparation.REQUESTS_PER_TICK,backend.requests);
        for(int tick=1;tick<20;tick++) assertFalse(job.advance(tick));
        assertEquals(EchoChunkPreparation.MAX_PENDING,backend.requests);
        boolean ready=false;
        for(int tick=20;tick<100 && !ready;tick++) {
            backend.ready.addAll(backend.held);
            int before=backend.requests;
            ready=job.advance(tick);
            assertTrue(backend.requests-before<=EchoChunkPreparation.REQUESTS_PER_TICK);
        }
        assertTrue(ready); assertEquals(job.total(),job.loaded());
        assertEquals(job.total(),backend.held.size()); assertEquals(0,backend.releases);
        job.close(); job.close();
        assertEquals(job.total(),backend.releases); assertTrue(backend.held.isEmpty());
        assertThrows(IllegalStateException.class,() -> job.advance(100));
    }
    @Test void timeoutCancellationAndBackendFailureReleaseOnlyOwnRequests() {
        var timeout=new Access(); var job=new EchoChunkPreparation(AREA,timeout,10);
        job.advance(10);
        assertThrows(IllegalStateException.class,() -> job.advance(10+EchoChunkPreparation.LOAD_TTL));
        assertEquals(timeout.requests,timeout.releases); assertTrue(timeout.held.isEmpty());
        var cancel=new Access(); var cancelled=new EchoChunkPreparation(AREA,cancel,0);
        cancelled.advance(0); cancelled.close();
        assertEquals(cancel.requests,cancel.releases);
        var broken=new Access(); broken.fail=true;
        var failed=new EchoChunkPreparation(AREA,broken,0);
        assertThrows(IllegalStateException.class,() -> failed.advance(0));
        assertEquals(broken.requests,broken.releases);
        var other=new Access(); var independent=new EchoChunkPreparation(AREA,other,0);
        independent.advance(0);
        failed.close(); assertFalse(other.held.isEmpty()); independent.close();
    }
    @Test void backwardsClockFailsClosedAndUnusedLeaseClosesWithoutTickets() {
        var backend=new Access(); var job=new EchoChunkPreparation(AREA,backend,10);
        assertThrows(IllegalStateException.class,() -> job.advance(9));
        assertEquals(0,backend.requests); assertEquals(0,backend.releases);
        new EchoChunkPreparation(AREA,backend,0).close();
        assertEquals(0,backend.releases);
    }
}
