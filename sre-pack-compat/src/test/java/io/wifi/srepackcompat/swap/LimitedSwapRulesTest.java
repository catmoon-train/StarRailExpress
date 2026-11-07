package io.wifi.srepackcompat.swap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LimitedSwapRulesTest {
    @Test void allVisibleHotbarPairsWorkExceptSelfSwap() {
        for (int selected = 0; selected < 9; selected++) {
            for (int target = 36; target <= 44; target++) {
                assertEquals(target != 36 + selected,
                        LimitedSwapRules.canSwap(target, selected, false, false));
            }
        }
    }

    @Test void hiddenInventoryEquipmentAndOffhandAreRejected() {
        for (int target = -1; target <= 46; target++) {
            if (target >= 36 && target <= 44) continue;
            assertFalse(LimitedSwapRules.canSwap(target, 0, false, false));
        }
    }

    @Test void blockedItemsOnEitherSideCannotBeMovedIncludingIntoEmptySlot() {
        for (int selected = 0; selected < 9; selected++) {
            for (int target = 36; target <= 44; target++) {
                assertFalse(LimitedSwapRules.canSwap(target, selected, true, false));
                assertFalse(LimitedSwapRules.canSwap(target, selected, false, true));
                assertFalse(LimitedSwapRules.canSwap(target, selected, true, true));
            }
        }
    }

    @Test void invalidSelectedHotbarIsRejected() {
        assertFalse(LimitedSwapRules.canSwap(36, -1, false, false));
        assertFalse(LimitedSwapRules.canSwap(36, 9, false, false));
        assertFalse(LimitedSwapRules.canSwap(36, 40, false, false));
    }
}
