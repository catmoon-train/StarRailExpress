package org.agmas.noellesroles.game.modes.echo.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EchoInventoryLayoutTest {
    @Test void toolsAndSuppliesStaySeparatedAtSupportedGuiSizes() {
        for (int[] screen : new int[][] {{320, 240}, {426, 240}, {640, 360}, {960, 540}, {1920, 1080}}) {
            var l = EchoInventoryLayout.of(screen[0], screen[1]);
            assertTrue(l.x() >= 0 && l.y() >= 0);
            assertTrue(l.x() + l.width() <= screen[0]);
            assertTrue(l.y() + l.height() <= screen[1]);
            assertTrue(l.slotSize() >= 24);
            for (int slot = 0; slot < 9; slot++) {
                assertTrue(l.slotX(slot) >= l.x() + 8);
                assertTrue(l.slotX(slot) + l.slotSize() <= l.x() + l.width() - 8);
                if (slot > 0) assertTrue(l.slotX(slot - 1) + l.slotSize() < l.slotX(slot));
            }
            assertTrue(l.slotX(3) - l.slotX(2) - l.slotSize() >= 14);
            assertTrue(l.slotY() + l.slotSize() < l.y() + l.height() - 13);
            assertTrue(l.contentY() + 50 <= l.detailBottom());
            assertTrue(l.detailBottom() < l.actionsY());
            assertTrue(l.actionsY() + 18 <= l.hintY());
            assertTrue(l.hintY() + 10 <= l.labelY());
            assertTrue(l.labelY() + 10 <= l.slotY());
            assertTrue(l.y() + 109 <= l.actionsY() - 8); // 窄屏候选名称与至少一行说明可见。
            for (int n = 0; n < 3; n++) {
                assertTrue(l.choiceX(n) >= l.x() + 8);
                assertTrue(l.choiceX(n) + l.choiceWidth() <= l.x() + l.width() - 8);
                if (n > 0) assertTrue(l.choiceX(n - 1) + l.choiceWidth() < l.choiceX(n));
            }
        }
    }
}
