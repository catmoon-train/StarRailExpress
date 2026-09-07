package org.agmas.noellesroles.game.fake_steve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeSteveApparitionLifecycleTest {

    @Test
    void apparitionMustBeObservedBeforeLookingAwayCanReplace() {
        FakeSteveApparitionLifecycle lifecycle = new FakeSteveApparitionLifecycle();

        lifecycle.tick(false, 3);
        assertEquals(FakeSteveApparitionLifecycle.Stage.UNSEEN, lifecycle.stage());

        lifecycle.tick(true, 5);
        assertEquals(FakeSteveApparitionLifecycle.Stage.OBSERVED, lifecycle.stage());

        lifecycle.tick(false, 3);
        assertEquals(FakeSteveApparitionLifecycle.Stage.LOOKED_AWAY, lifecycle.stage());
        assertTrue(lifecycle.shouldReplace());
    }

    @Test
    void staringForThirtySecondsEndsTheApparitionHarmlessly() {
        FakeSteveApparitionLifecycle lifecycle = new FakeSteveApparitionLifecycle();

        lifecycle.tick(true, FakeSteveApparitionLifecycle.TIMEOUT_TICKS);

        assertEquals(FakeSteveApparitionLifecycle.Stage.TIMED_OUT, lifecycle.stage());
        assertFalse(lifecycle.shouldReplace());
    }
}
