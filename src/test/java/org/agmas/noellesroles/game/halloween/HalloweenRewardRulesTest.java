package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenRewardRulesTest {
    @Test void pumpkinBoostIsThirtyPercentInExpectationAndSafelyRounded() {
        for (int count : new int[]{1, 2, 3, 10, 63, 64, 4096}) {
            long total = 0;
            for (int roll = 0; roll < 10; roll++) total += HalloweenRewardRules.boostedPumpkins(count, roll);
            assertEquals(13L * count, total);
        }
        assertEquals(2, HalloweenRewardRules.boostedPumpkins(1, 2));
        assertEquals(1, HalloweenRewardRules.boostedPumpkins(1, 3));
        assertEquals(3, HalloweenRewardRules.boostedPumpkins(2, 5));
        assertEquals(2, HalloweenRewardRules.boostedPumpkins(2, 6));
        assertEquals(13, HalloweenRewardRules.boostedPumpkins(10, 9));
        assertEquals(0, HalloweenRewardRules.boostedPumpkins(0, 0));
        assertEquals(0, HalloweenRewardRules.boostedPumpkins(-1, 0));
        assertEquals(Integer.MAX_VALUE, HalloweenRewardRules.boostedPumpkins(Integer.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> HalloweenRewardRules.boostedPumpkins(1, -1));
        assertThrows(IllegalArgumentException.class, () -> HalloweenRewardRules.boostedPumpkins(1, 10));
    }

    @Test void pumpkinBoostPrecedesCivilianReductionAndIsNotRepeatedForLinkCopies() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenPumpkinRewards.java"));
        assertTrue(source.indexOf("HalloweenRewardRules.boostedPumpkins(")
                < source.indexOf("HalloweenRewardRules.pumpkinReward("));
        assertTrue(source.contains("getBoolean(BOOSTED)"));
        String mirror = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenLinkItems.java"));
        assertFalse(mirror.contains("HalloweenPumpkinRewards.adjust"));
    }

    @Test void candyShieldHasTenPercentChanceAndOtherEffectsEachHaveThirty() {
        int[] counts = new int[4];
        for (int roll = 0; roll < 100; roll++) counts[HalloweenRewardRules.candyEffect(roll)]++;
        assertArrayEquals(new int[]{10, 30, 30, 30}, counts);
        assertEquals(0, HalloweenRewardRules.candyEffect(9));
        assertEquals(1, HalloweenRewardRules.candyEffect(10));
        assertThrows(IllegalArgumentException.class, () -> HalloweenRewardRules.candyEffect(-1));
        assertThrows(IllegalArgumentException.class, () -> HalloweenRewardRules.candyEffect(100));
    }

    @Test void civilianPumpkinsAreExactlyHalfInExpectationWithRandomOddRounding() {
        for (int count : new int[]{1, 2, 3, 63, 64, Integer.MAX_VALUE}) {
            int down = HalloweenRewardRules.pumpkinReward(count, true, false);
            int up = HalloweenRewardRules.pumpkinReward(count, true, true);
            assertEquals(count, (long) down + up);
            assertEquals(count, HalloweenRewardRules.pumpkinReward(count, false, false));
            assertEquals(count, HalloweenRewardRules.pumpkinReward(count, false, true));
        }
        assertEquals(0, HalloweenRewardRules.pumpkinReward(1, true, false));
        assertEquals(1, HalloweenRewardRules.pumpkinReward(1, true, true));
        assertEquals(1, HalloweenRewardRules.pumpkinReward(2, true, false));
        assertEquals(1, HalloweenRewardRules.pumpkinReward(2, true, true));
        assertEquals(0, HalloweenRewardRules.pumpkinReward(-1, true, true));
    }

    @Test void civilianReductionHappensOnlyForFreshRewardsBeforeAccounting() throws Exception {
        var root = java.nio.file.Path.of("src/main/java/org/agmas/noellesroles");
        String handler = java.nio.file.Files.readString(root.resolve("game/halloween/pumpkin/PumpkinSpiritHandler.java"));
        assertTrue(handler.contains("recordExistingStack(p, p.getInventory().getItem(i))"));
        assertTrue(handler.contains("HalloweenPumpkinRewards.adjust(recipient, stack)"));
        String reducer = java.nio.file.Files.readString(root.resolve("game/halloween/HalloweenPumpkinRewards.java"));
        assertTrue(reducer.contains("RoleTeam.of(RoleUtils.getPlayerRole(player)) == RoleTeam.CIVILIAN"));
        assertTrue(reducer.contains("getBoolean(ADJUSTED)) return"));
        String drop = java.nio.file.Files.readString(root.resolve("mixin/halloween/pumpkin/PumpkinPlayerMixin.java"));
        assertTrue(drop.contains("PumpkinSpiritHandler.recordExistingStack(sp, stack)"));
        assertFalse(drop.contains("PumpkinSpiritHandler.onRewardStack(sp, stack)"));
        String task = java.nio.file.Files.readString(root.resolve("game/halloween/HalloweenTaskRewards.java"));
        assertTrue(task.indexOf("HalloweenPumpkinRewards.adjust(player, reward)") < task.indexOf("new ItemEntity"));
    }

    @Test void tasksAlwaysAwardPumpkinsAndOtherDropsRemainIndependent() {
        assertEquals(new HalloweenRewardRules.TaskLoot(1, true, true), HalloweenRewardRules.taskLoot(0, 0, 0));
        assertEquals(new HalloweenRewardRules.TaskLoot(1, false, false), HalloweenRewardRules.taskLoot(.2, .1, .1));
        assertEquals(new HalloweenRewardRules.TaskLoot(1, false, true), HalloweenRewardRules.taskLoot(.4999, .1, .0999));
        assertEquals(new HalloweenRewardRules.TaskLoot(2, false, false), HalloweenRewardRules.taskLoot(.5, .1, .1));
        assertEquals(2, HalloweenRewardRules.taskLoot(Math.nextDown(1.0), .9, .9).pumpkinCount());
    }

    @Test void pumpkinCountsAreUniformAndOtherProbabilityBoundariesUnchanged() {
        int onePumpkin = 0, twoPumpkins = 0, candy = 0, bags = 0;
        for (int i = 0; i < 100; i++) {
            var loot = HalloweenRewardRules.taskLoot(i / 100.0, i / 100.0, i / 100.0);
            assertTrue(loot.pumpkinCount() >= 1 && loot.pumpkinCount() <= 2);
            if (loot.pumpkinCount() == 1) onePumpkin++;
            if (loot.pumpkinCount() == 2) twoPumpkins++;
            if (loot.candy()) candy++;
            if (loot.bag()) bags++;
        }
        assertEquals(50, onePumpkin); assertEquals(50, twoPumpkins);
        assertEquals(10, candy); assertEquals(10, bags);
        assertEquals(new HalloweenRewardRules.TaskLoot(2, false, false),
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
