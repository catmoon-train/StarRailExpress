package io.wifi.starrailexpress.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DepartureCurtainTest {
    @Test
    void gameStartDoesNotCloseGuiBeforeBlackHasBeenRendered() {
        var curtain = new DepartureCurtain();
        curtain.tick(0, true); // Early start packet, before local world fade catches up.
        assertFalse(curtain.canHandoff(true));
        for (int tick = 0; tick < 12; tick++) curtain.tick(0, true);
        assertFalse(curtain.canHandoff(true), "Ticks alone must never close an undrawn GUI");
        curtain.frameRendered(1);
        assertTrue(curtain.canHandoff(true));
        curtain.release();
        assertEquals(1, curtain.opacity(0));
        curtain.tick(0, true);
        assertTrue(curtain.opacity(0.5F) < 1);
        assertFalse(curtain.canHandoff(true));
    }

//    @Test
//    void holdsBlackUntilServerOrRoleSelectionIsReady() {
//        var curtain = new DepartureCurtain();
//        for (int tick = 0; tick < 15; tick++) curtain.tick(1, false);
//        curtain.frameRendered(1);
//        assertFalse(curtain.canHandoff(false));
//        curtain.tick(0, false); // World component may reset its fade during map startup.
//        assertEquals(1, curtain.opacity(1));
//        assertTrue(curtain.canHandoff(true));
//        curtain.clear();
//        assertFalse(curtain.isVisible());
//        assertFalse(curtain.canHandoff(true));
//    }
}
