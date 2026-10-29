package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenRewardRulesTest {
    @Test void independentDrawsCanAwardAllThreeOrNone() {
        assertEquals(new HalloweenRewardRules.TaskLoot(true, true, true), HalloweenRewardRules.taskLoot(0, 0, 0));
        assertEquals(new HalloweenRewardRules.TaskLoot(false, false, false), HalloweenRewardRules.taskLoot(.2, .1, .1));
        assertEquals(new HalloweenRewardRules.TaskLoot(true, false, true), HalloweenRewardRules.taskLoot(.1999, .1, .0999));
    }

    @Test void probabilityBoundariesAreExactAndInvalidRollsNeverAward() {
        int pumpkins = 0, candy = 0, bags = 0;
        for (int i = 0; i < 100; i++) {
            var loot = HalloweenRewardRules.taskLoot(i / 100.0, i / 100.0, i / 100.0);
            if (loot.pumpkin()) pumpkins++;
            if (loot.candy()) candy++;
            if (loot.bag()) bags++;
        }
        assertEquals(20, pumpkins); assertEquals(10, candy); assertEquals(10, bags);
        assertEquals(new HalloweenRewardRules.TaskLoot(false, false, false),
                HalloweenRewardRules.taskLoot(Double.NaN, -1, Double.POSITIVE_INFINITY));
    }

    @Test void threeDurabilityAndThirtySecondCooldownAreStable() {
        assertEquals(3, HalloweenRewardRules.BONE_DURABILITY);
        assertEquals(600, HalloweenRewardRules.BONE_COOLDOWN_TICKS);
        assertEquals(0, HalloweenRewardRules.migratedBoneDamage(0, 0));
        assertEquals(1, HalloweenRewardRules.migratedBoneDamage(0, 1));
        assertEquals(2, HalloweenRewardRules.migratedBoneDamage(2, 1));
        assertEquals(3, HalloweenRewardRules.migratedBoneDamage(0, 15));
        assertEquals(0, HalloweenRewardRules.migratedBoneDamage(-1, -1));
    }

    @Test void initializationResetKeepsOnlyThisTicksNewSession() {
        assertTrue(HalloweenRewardRules.keepsFreshSession(200L, 200));
        assertFalse(HalloweenRewardRules.keepsFreshSession(199L, 200));
        assertFalse(HalloweenRewardRules.keepsFreshSession(null, 200));
        assertFalse(HalloweenRewardRules.keepsFreshSession(201L, 200));
    }
}
