package io.wifi.srepackcompat.render;

import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbandonedRenderTargetsTest {
    private final AbandonedRenderTargets tracker = new AbandonedRenderTargets();
    private final List<AbandonedRenderTargets.Handles> released = new ArrayList<>();

    @Test void liveTargetsAreNotReleased() {
        Object target = new Object();
        var registration = tracker.track(target, new AbandonedRenderTargets.Handles(1, 2, 3));
        assertNotNull(registration);
        assertEquals(1, tracker.trackedCount());
        assertEquals(0, tracker.drain(20, released::add));
        assertTrue(released.isEmpty());
        registration.close();
        assertEquals(0, tracker.trackedCount());
        Reference.reachabilityFence(target);
    }

    @Test void abandonedResourcesReleaseOnlyOnceAfterReferenceIsEnqueued() {
        Object target = new Object();
        var ids = new AbandonedRenderTargets.Handles(11, 12, 13);
        var registration = tracker.track(target, ids);
        assertTrue(registration.enqueue());
        assertEquals(1, tracker.drain(20, released::add));
        assertEquals(List.of(ids), released);
        assertEquals(0, tracker.trackedCount());
        assertEquals(0, tracker.drain(20, released::add));
        assertFalse(registration.enqueue());
        Reference.reachabilityFence(target);
    }

    @Test void normalDestructionCancelsEvenAlreadyEnqueuedCleanup() {
        Object target = new Object();
        var registration = tracker.track(target, new AbandonedRenderTargets.Handles(1, 2, 3));
        registration.enqueue();
        registration.close();
        registration.close();
        assertEquals(0, tracker.drain(20, released::add));
        assertTrue(released.isEmpty());
        assertEquals(0, tracker.trackedCount());
        Reference.reachabilityFence(target);
    }

    @Test void resizeAndIdReuseDoNotDeleteNewLiveAllocation() {
        Object target = new Object();
        var old = tracker.track(target, new AbandonedRenderTargets.Handles(1, 2, 3));
        old.enqueue();
        old.close();
        var current = tracker.track(target, new AbandonedRenderTargets.Handles(1, 2, 3));
        assertEquals(0, tracker.drain(20, released::add));
        assertEquals(1, tracker.trackedCount());
        current.enqueue();
        assertEquals(1, tracker.drain(20, released::add));
        assertEquals(1, released.size());
        Reference.reachabilityFence(target);
    }

    @Test void batchBudgetIsTwentyReferencesNotUnboundedWork() {
        var targets = new ArrayList<Object>();
        for (int i = 0; i < 45; i++) {
            Object target = new Object();
            targets.add(target);
            tracker.track(target, new AbandonedRenderTargets.Handles(i, -1, i)).enqueue();
        }
        assertEquals(20, tracker.drain(20, released::add));
        assertEquals(25, tracker.trackedCount());
        assertEquals(20, tracker.drain(20, released::add));
        assertEquals(5, tracker.drain(20, released::add));
        assertEquals(45, released.size());
        assertEquals(0, tracker.trackedCount());
        Reference.reachabilityFence(targets);
    }

    @Test void emptyHandlesHaveNoRegistrationAndPartialAllocationsAreTracked() {
        Object target = new Object();
        assertNull(tracker.track(target, new AbandonedRenderTargets.Handles(-1, -1, -1)));
        var ids = new AbandonedRenderTargets.Handles(-1, 42, -1);
        tracker.track(target, ids).enqueue();
        assertEquals(1, tracker.drain(20, released::add));
        assertEquals(List.of(ids), released);
        assertFalse(ids.containsTexture(-1));
        assertTrue(ids.containsTexture(42));
        Reference.reachabilityFence(target);
    }

    @Test void zeroBudgetLeavesQueueUntouched() {
        Object target = new Object();
        tracker.track(target, new AbandonedRenderTargets.Handles(1, 2, 3)).enqueue();
        assertEquals(0, tracker.drain(0, released::add));
        assertEquals(1, tracker.drain(20, released::add));
        Reference.reachabilityFence(target);
    }
}
