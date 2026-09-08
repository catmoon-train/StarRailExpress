/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
