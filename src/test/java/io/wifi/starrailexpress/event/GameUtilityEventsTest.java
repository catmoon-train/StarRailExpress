package io.wifi.starrailexpress.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameUtilityEventsTest {
    @Test
    void emptyAttemptListenersAllowBothOrdinaryAndForcedDeath() {
        var invoker = GameUtilityEvents.beforeKillInvoker(new GameUtilityEvents.BeforeKill[0]);
        assertTrue(invoker.allow(null, false, null, null, false));
        assertTrue(invoker.allow(null, true, null, null, true));
    }

    @Test
    void cancellationPreservesOrderAndStopsLaterSideEffectsEvenForForce() {
        for (boolean force : List.of(false, true)) {
            List<Integer> calls = new ArrayList<>();
            var invoker = GameUtilityEvents.beforeKillInvoker(new GameUtilityEvents.BeforeKill[] {
                    (victim, body, killer, reason, forced) -> {
                        assertEquals(force, forced);
                        assertTrue(body);
                        calls.add(1);
                        return true;
                    },
                    (victim, body, killer, reason, forced) -> {
                        calls.add(2);
                        return false;
                    },
                    (victim, body, killer, reason, forced) -> {
                        calls.add(3);
                        return true;
                    }
            });
            assertFalse(invoker.allow(null, true, null, null, force));
            assertEquals(List.of(1, 2), calls);
        }
    }

    @Test
    void allowingListenersRunOnceAndExceptionsAreNotSwallowed() {
        List<Integer> calls = new ArrayList<>();
        var allowed = GameUtilityEvents.beforeKillInvoker(new GameUtilityEvents.BeforeKill[] {
                (victim, body, killer, reason, force) -> { calls.add(1); return true; },
                (victim, body, killer, reason, force) -> { calls.add(2); return true; }
        });
        assertTrue(allowed.allow(null, false, null, null, false));
        assertEquals(List.of(1, 2), calls);
        var failure = new IllegalStateException("expected");
        var throwing = GameUtilityEvents.beforeKillInvoker(new GameUtilityEvents.BeforeKill[] {
                (victim, body, killer, reason, force) -> { throw failure; }
        });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> throwing.allow(null, false, null, null, true)));
    }
}
