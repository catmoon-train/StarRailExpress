package org.agmas.noellesroles.game.modes.echo.inventory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.agmas.noellesroles.game.modes.echo.inventory.EchoHotbarSlots.*;

class EchoHotbarSlotsTest {
    @Test void threeToolsAndSixResourcesHaveExactBoundaries() {
        assertEquals(9, TOTAL);
        for (int slot = 0; slot < 3; slot++) assertEquals(-1, resourceSlot(slot));
        for (int resource = 0; resource < 6; resource++) {
            assertEquals(resource + 3, inventorySlot(resource));
            assertEquals(resource, resourceSlot(inventorySlot(resource)));
        }
        assertEquals(-1, resourceSlot(-1)); assertEquals(-1, resourceSlot(9));
        assertThrows(IllegalArgumentException.class, () -> inventorySlot(-1));
        assertThrows(IllegalArgumentException.class, () -> inventorySlot(6));
        assertFalse(protectedSlot(-1)); assertFalse(protectedSlot(9));
    }
    @Test void allNineSlotsIncludingEmptyToolsRejectEveryVanillaClick() {
        for (int slot = 0; slot < TOTAL; slot++) for (Click click : Click.values())
            assertTrue(blockClick(true, slot, 0, click, false), slot + ":" + click);
    }
    @Test void numberKeysAndOffhandCannotSwapIntoFixedSlotsFromOtherContainers() {
        for (int button = 0; button < TOTAL; button++) assertTrue(blockClick(true, -1, button, Click.SWAP, false));
        assertTrue(blockClick(true, -1, 40, Click.SWAP, false));
    }
    @Test void indirectMovesAndProjectionCursorCannotBypassProtection() {
        for (Click click : new Click[]{Click.QUICK_MOVE, Click.QUICK_CRAFT, Click.PICKUP_ALL, Click.CLONE})
            assertTrue(blockClick(true, 12, 0, click, false));
        assertTrue(blockClick(true, -999, 0, Click.THROW, true));
        assertTrue(blockClick(true, 12, 0, Click.PICKUP, true));
        assertFalse(blockClick(true, 12, 0, Click.PICKUP, false));
    }
    @Test void ordinaryModesAreUnaffected() {
        for (Click click : Click.values()) for (int slot = -1; slot <= 40; slot++)
            assertFalse(blockClick(false, slot, 0, click, true));
    }
}
