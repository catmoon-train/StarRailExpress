package io.wifi.starrailexpress.training;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TrainingCampPoolTest {
    @ParameterizedTest
    @ValueSource(ints = {8, 9, 32})
    void simultaneousDifferentOwnersNeverShareASlot(int requests) throws Exception {
        TrainingCampPool pool = new TrainingCampPool();
        List<Callable<TrainingCampPool.Lease>> work = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            UUID owner = UUID.randomUUID();
            work.add(() -> pool.acquire(owner));
        }
        var leases = race(work);
        var slots = new HashSet<Integer>();
        int accepted = 0;
        for (var lease : leases) {
            if (lease == null) continue;
            accepted++;
            assertTrue(lease.slot() >= 0 && lease.slot() < 8);
            assertTrue(slots.add(lease.slot()), "Two real owners were given the same camp");
            assertTrue(pool.owns(lease.owner(), lease.slot()));
        }
        assertEquals(8, accepted);
        assertEquals(8, pool.size());
        assertEquals(8, TrainingCampPool.CAPACITY);
    }

    @Test
    void simultaneousSameOwnerReceivesIdenticalLease() throws Exception {
        TrainingCampPool pool = new TrainingCampPool();
        UUID owner = UUID.randomUUID();
        List<Callable<TrainingCampPool.Lease>> work = new ArrayList<>();
        for (int i = 0; i < 32; i++) work.add(() -> pool.acquire(owner));
        var leases = race(work);
        for (var lease : leases) assertSame(leases.getFirst(), lease);
        assertEquals(1, pool.size());
        assertSame(leases.getFirst(), pool.acquire(owner));
    }

    @Test
    void fullPoolStillReturnsItsExistingOwnersLease() {
        TrainingCampPool pool = new TrainingCampPool();
        var first = pool.acquire(UUID.randomUUID());
        for (int i = 1; i < 8; i++) assertNotNull(pool.acquire(UUID.randomUUID()));
        assertNull(pool.acquire(UUID.randomUUID()));
        assertSame(first, pool.acquire(first.owner()));
    }

    @Test
    void releaseIsIdempotentAndOldTokensCannotReleaseReassignedSlot() {
        TrainingCampPool pool = new TrainingCampPool();
        UUID owner = UUID.randomUUID();
        var old = pool.acquire(owner);
        assertTrue(pool.release(old));
        assertFalse(pool.release(old));
        var other = pool.acquire(UUID.randomUUID());
        assertEquals(old.slot(), other.slot());
        assertTrue(other.generation() > old.generation());
        assertFalse(pool.release(old));
        assertTrue(pool.owns(other.owner(), other.slot()));
        assertFalse(pool.owns(owner, other.slot()));
        assertTrue(pool.release(other));
        var sameOwnerAgain = pool.acquire(owner);
        assertEquals(old.slot(), sameOwnerAgain.slot());
        assertFalse(pool.release(old));
        assertEquals(1, pool.size());
        assertFalse(pool.release(null));
        assertFalse(pool.release(new TrainingCampPool.Lease(owner, -1, old.generation())));
        assertFalse(pool.release(new TrainingCampPool.Lease(owner, 8, old.generation())));
        assertFalse(pool.release(new TrainingCampPool.Lease(UUID.randomUUID(), sameOwnerAgain.slot(), sameOwnerAgain.generation())));
        assertFalse(pool.owns(owner, -1));
        assertFalse(pool.owns(owner, 8));
    }

    @Test
    void previousPoolTokensCannotAffectANewServerLifecycle() {
        UUID owner = UUID.randomUUID();
        TrainingCampPool previous = new TrainingCampPool();
        var old = previous.acquire(owner);
        previous.close();
        TrainingCampPool current = new TrainingCampPool();
        var lease = current.acquire(owner);
        assertNotEquals(old.generation(), lease.generation());
        assertFalse(current.release(old));
        assertTrue(current.owns(owner, lease.slot()));
    }

    @Test
    void closingRacesWithAcquireAndReleaseWithoutReopening() throws Exception {
        TrainingCampPool pool = new TrainingCampPool();
        var existing = pool.acquire(UUID.randomUUID());
        List<Callable<Boolean>> work = new ArrayList<>();
        work.add(() -> { pool.close(); return true; });
        work.add(() -> pool.release(existing));
        for (int i = 0; i < 30; i++) work.add(() -> pool.acquire(UUID.randomUUID()) != null);
        race(work);
        assertEquals(0, pool.size());
        assertFalse(pool.owns(existing.owner(), existing.slot()));
        assertFalse(pool.release(existing));
        for (int i = 0; i < 32; i++) assertNull(pool.acquire(UUID.randomUUID()));
        pool.close();
        assertEquals(0, pool.size());
    }

    @Test
    void arbitrarilyManySequentialOwnersUseOnlyEightResources() {
        TrainingCampPool pool = new TrainingCampPool();
        var slots = new HashSet<Integer>();
        for (int round = 0; round < 100; round++) {
            List<TrainingCampPool.Lease> leases = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                var lease = pool.acquire(UUID.randomUUID());
                assertNotNull(lease);
                slots.add(lease.slot());
                leases.add(lease);
            }
            for (var lease : leases) assertTrue(pool.release(lease));
            assertEquals(0, pool.size());
        }
        assertEquals(8, slots.size());
    }

    private static <T> List<T> race(List<Callable<T>> work) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(work.size());
        try (var executor = Executors.newFixedThreadPool(work.size())) {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (var action : work) futures.add(executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return action.call();
            }));
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        }
    }
}
