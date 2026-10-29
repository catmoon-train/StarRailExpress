package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenLinkRulesTest {
    @Test void shieldPoolAddsWithoutDuplicatingOrOverflowing() {
        assertEquals(5, HalloweenLinkRules.sharedArmor(2, 3));
        assertEquals(3, HalloweenLinkRules.sharedArmor(-1, 3));
        assertEquals(Integer.MAX_VALUE, HalloweenLinkRules.sharedArmor(Integer.MAX_VALUE, 1));
    }
    @Test void consumeUsesOwnLayersBeforePartner() {
        assertEquals(2, HalloweenLinkRules.consumeOwn(2, 5));
        assertEquals(1, HalloweenLinkRules.consumeOwn(4, 1));
        assertEquals(0, HalloweenLinkRules.consumeOwn(4, -1));
    }
    @Test void tenBlocksIsInclusiveAndInvalidCoordinatesNeverPass() {
        assertTrue(HalloweenLinkRules.within(100, 10));
        assertFalse(HalloweenLinkRules.within(100.001, 10));
        assertFalse(HalloweenLinkRules.within(Double.NaN, 10));
        assertFalse(HalloweenLinkRules.within(1, Double.POSITIVE_INFINITY));
        assertFalse(HalloweenLinkRules.within(0, 0));
    }
}
