package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouletteDevilRulesTest {
    @Test void sanThresholdIsStrictAndUsesMaximum() {
        assertTrue(RouletteDevilRules.lowSan(.299, 1));
        assertFalse(RouletteDevilRules.lowSan(.30, 1));
        assertFalse(RouletteDevilRules.lowSan(.31, 1));
        assertTrue(RouletteDevilRules.lowSan(.59, 2));
        assertFalse(RouletteDevilRules.lowSan(.60, 2));
    }
    @Test void invalidSanCannotCreateEligibility() {
        assertFalse(RouletteDevilRules.lowSan(Double.NaN, 1));
        assertFalse(RouletteDevilRules.lowSan(0, 0));
        assertFalse(RouletteDevilRules.lowSan(0, Double.POSITIVE_INFINITY));
    }
    @Test void populationTargetsUseFloorAndKeepMinimumAllowance() {
        int[] players = {1, 6, 7, 8, 13, 14, 15, 20, 21, 22, 27, 28};
        int[] wins = {1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5};
        int[] lives = {1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 3, 3};
        for (int i = 0; i < players.length; i++) {
            assertEquals(wins[i], RouletteDevilRules.requiredWins(players[i]));
            assertEquals(lives[i], RouletteDevilRules.defeatAllowances(players[i]));
        }
    }
    @Test void allowanceExhaustionKillsOnlyOnFollowingDefeat() {
        assertTrue(RouletteDevilRules.spareDefeat(16, 0));
        assertTrue(RouletteDevilRules.spareDefeat(16, 1));
        assertFalse(RouletteDevilRules.spareDefeat(16, 2));
        assertFalse(RouletteDevilRules.spareDefeat(16, 3));
        assertTrue(RouletteDevilRules.spareDefeat(2, 0));
        assertFalse(RouletteDevilRules.spareDefeat(2, 1));
    }
    @Test void onlyLegitimateSettlementMayKillAnActiveDevil() {
        assertFalse(RouletteDevilRules.allowDeath(true, false));
        assertTrue(RouletteDevilRules.allowDeath(true, true));
        assertTrue(RouletteDevilRules.allowDeath(false, false));
        assertTrue(RouletteDevilRules.allowDeath(false, true));
    }
    @Test void seedRequiresFullThirtySeconds() {
        assertFalse(RouletteDevilRules.mature(1000, 1599));
        assertTrue(RouletteDevilRules.mature(1000, 1600));
        assertTrue(RouletteDevilRules.mature(1000, 2600));
        assertFalse(RouletteDevilRules.mature(1000, 999));
    }
    @Test void eitherLowSanOrMatureSeedQualifiesRegardlessOfFaction() {
        assertFalse(RouletteDevilRules.eligible(false, false));
        assertTrue(RouletteDevilRules.eligible(true, false));
        assertTrue(RouletteDevilRules.eligible(false, true));
        assertTrue(RouletteDevilRules.eligible(true, true));
    }
    @Test void gazeDrainsTwoPointFivePercentOfMaximumPerSecond() {
        assertEquals(.025f, RouletteDevilRules.gazeSanLoss(1f), .0001f);
        assertEquals(.05f, RouletteDevilRules.gazeSanLoss(2f), .0001f);
        assertEquals(.0375f, RouletteDevilRules.gazeSanLoss(1.5f), .0001f);
        assertEquals(0f, RouletteDevilRules.gazeSanLoss(0f), .0001f);
        assertEquals(0f, RouletteDevilRules.gazeSanLoss(Float.NaN), .0001f);
        assertEquals(20, RouletteDevilRules.GAZE_INTERVAL_TICKS);
    }
    @Test void manifestationLastsTwentySecondsAndSeedStillTakesThirty() {
        assertEquals(400, RouletteDevilRules.MANIFEST_TICKS);
        assertEquals(600, RouletteDevilRules.SEED_TICKS);
    }
    @Test void sameTargetCooldownRunsThirtySecondsFromDuelEnd() {
        assertEquals(30, RouletteDevilRules.SAME_TARGET_COOLDOWN_SECONDS);
        assertEquals(600, RouletteDevilRules.SAME_TARGET_COOLDOWN_TICKS);
        assertTrue(RouletteDevilRules.cooling(1600, 1000));
        assertFalse(RouletteDevilRules.cooling(1600, 1600));
        assertFalse(RouletteDevilRules.cooling(1600, 2200));
        assertEquals(30, RouletteDevilRules.cooldownSeconds(1600, 1000));
        assertEquals(1, RouletteDevilRules.cooldownSeconds(1600, 1599));
        assertEquals(0, RouletteDevilRules.cooldownSeconds(0, 1000));
        assertEquals(0, RouletteDevilRules.cooldownSeconds(1600, 1600));
    }
    @Test void seatBoundsRejectTeleportAndNonFiniteCoordinates() {
        assertTrue(RouletteDevilRules.withinSeat(0, .8, 0));
        assertTrue(RouletteDevilRules.withinSeat(.2, .7, -.2));
        assertFalse(RouletteDevilRules.withinSeat(4, .8, 0));
        assertFalse(RouletteDevilRules.withinSeat(0, 40, 0));
        assertFalse(RouletteDevilRules.withinSeat(Double.NaN, 0, 0));
        assertFalse(RouletteDevilRules.withinSeat(0, 0, Double.POSITIVE_INFINITY));
    }
}
