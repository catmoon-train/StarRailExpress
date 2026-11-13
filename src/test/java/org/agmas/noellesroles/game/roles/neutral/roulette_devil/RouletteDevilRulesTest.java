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
        int[] players = {1, 4, 5, 7, 8, 15, 16, 24};
        int[] wins = {1, 1, 2, 2, 2, 4, 4, 5};
        int[] lives = {1, 1, 1, 1, 1, 1, 2, 3};
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
    @Test void plusFiveHundredPercentMultipliesExistingDrainBySix() {
        assertEquals(6, RouletteDevilRules.manifestationDrain(1, true));
        assertEquals(4.2f, RouletteDevilRules.manifestationDrain(.7f, true), .0001f);
        assertEquals(0, RouletteDevilRules.manifestationDrain(0, true));
        assertEquals(.7f, RouletteDevilRules.manifestationDrain(.7f, false));
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
