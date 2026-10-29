package org.agmas.noellesroles.game.halloween.ambience;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenAmbienceStateTest {
    @Test
    void requiresEnabledMapAndMurderModeActiveRoundAndConnectedPlayer() {
        for (int flags = 0; flags < 8; flags++) {
            assertEquals(flags == 7, HalloweenAmbienceState.enabled(
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0));
        }
    }

    @Test
    void entersOnceAndDoesNotReplayEachTick() {
        var state = new HalloweenAmbienceState();
        Object world = new Object();
        assertChange(state.update(world, false, true), false, false);
        assertChange(state.update(world, true, true), false, true);
        for (int tick = 0; tick < 200; tick++) assertChange(state.update(world, true, true), false, false);
    }

    @Test
    void roundEndOrLeavingActivityStopsOnce() {
        var state = new HalloweenAmbienceState();
        Object world = new Object();
        state.update(world, true, true);
        assertChange(state.update(world, false, true), true, false);
        assertChange(state.update(world, false, true), false, false);
        assertChange(state.update(world, true, true), false, true);
    }

    @Test
    void worldIdentityChangeRestartsEvenWhenWorldKeysAreEqual() {
        var state = new HalloweenAmbienceState();
        String first = new String("town");
        String second = new String("town");
        state.update(first, true, true);
        assertChange(state.update(second, true, true), true, true);
        assertChange(state.update(second, true, true), false, false);
    }

    @Test
    void disconnectStopsAndReconnectStartsFresh() {
        var state = new HalloweenAmbienceState();
        Object world = new Object();
        state.update(world, true, true);
        assertChange(state.update(null, true, true), true, false);
        state.reset();
        assertChange(state.update(null, false, true), false, false);
        assertChange(state.update(world, true, true), false, true);
    }

    @Test
    void mutedMusicOrMasterDoesNotPlayAndUnmuteRestartsOnce() {
        var state = new HalloweenAmbienceState();
        Object world = new Object();
        assertChange(state.update(world, true, false), false, false);
        assertChange(state.update(world, true, true), false, true);
        assertChange(state.update(world, true, false), true, false);
        assertChange(state.update(world, true, false), false, false);
        assertChange(state.update(world, true, true), false, true);
    }

    @Test
    void engineReloadInvalidatesOldStreamAndRestartsOnlyWhileActive() {
        var state = new HalloweenAmbienceState();
        Object world = new Object();
        state.update(world, true, true);
        state.reset(); // 客户端先停止旧声音，再清状态；模拟 F3+T / F3+S。
        assertChange(state.update(world, true, true), false, true);
        assertChange(state.update(world, true, true), false, false);
        state.reset();
        assertChange(state.update(world, false, true), false, false);
    }

    private static void assertChange(HalloweenAmbienceState.Change change, boolean stop, boolean start) {
        assertEquals(stop, change.stop());
        assertEquals(start, change.start());
    }
}
