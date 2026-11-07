package io.wifi.srepackcompat.swap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwapGestureTest {
    @Test void shortPressKeepsInventoryOpen() {
        SwapGesture g = new SwapGesture();
        g.press(1_000_000_000L);
        assertFalse(g.release(1_199_999_999L, 200));
        assertFalse(g.isHeld());
    }

    @Test void exactThresholdIsLongPress() {
        SwapGesture g = new SwapGesture();
        g.press(1_000_000_000L);
        assertTrue(g.release(1_200_000_000L, 200));
        assertFalse(g.release(2_000_000_000L, 200));
    }

    @Test void repeatDoesNotRestartTimer() {
        SwapGesture g = new SwapGesture();
        g.press(0);
        g.press(190_000_000L);
        assertTrue(g.release(200_000_000L, 200));
    }

    @Test void focusLossCancelsGesture() {
        SwapGesture g = new SwapGesture();
        g.press(0);
        g.reset();
        assertFalse(g.release(1_000_000_000L, 200));
        g.press(1_000_000_000L);
        assertFalse(g.release(1_001_000_000L, 200));
    }

    @Test void thresholdRangeAndNanoTimeWrap() {
        for (int ms : new int[]{50, 200, 1000}) {
            SwapGesture g = new SwapGesture();
            long start = Long.MAX_VALUE - 1_000_000L;
            g.press(start);
            assertTrue(g.release(start + ms * 1_000_000L, ms));
        }
    }

    @Test void creativeMenuMappingCoversEveryMainInventorySlotExactlyOnce() {
        var seen = new java.util.HashSet<Integer>();
        for (int i = 0; i < 36; i++) {
            int menu = SwapGesture.inventoryMenuSlot(i);
            assertTrue(menu >= 9 && menu <= 44);
            assertTrue(seen.add(menu));
            assertEquals(i < 9 ? i + 36 : i, menu);
        }
        assertEquals(36, seen.size());
        assertThrows(IllegalArgumentException.class, () -> SwapGesture.inventoryMenuSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> SwapGesture.inventoryMenuSlot(36));
    }
}
