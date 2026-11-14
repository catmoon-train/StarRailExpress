package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SackGhostRulesTest {
    @Test void generationUsesFixedOpeningPopulationWithTenSecondFloor() {
        assertEquals(43, SackGhostRules.generationCooldownSeconds(12));
        assertEquals(31, SackGhostRules.generationCooldownSeconds(24));
        assertEquals(15, SackGhostRules.generationCooldownSeconds(40));
        assertEquals(10, SackGhostRules.generationCooldownSeconds(50));
        assertEquals(10, SackGhostRules.generationCooldownSeconds(128));
        assertEquals(55, SackGhostRules.generationCooldownSeconds(-5));
    }

    @Test void fourStagesAdvanceAtActualSanThresholds() {
        assertEquals(180, SackGhostRules.stageThreshold(2, 1));
        assertEquals(440, SackGhostRules.stageThreshold(3, 1));
        assertEquals(800, SackGhostRules.stageThreshold(4, 1));
        assertEquals(384, SackGhostRules.stageThreshold(2, 24));
        assertEquals(864, SackGhostRules.stageThreshold(3, 24));
        assertEquals(1536, SackGhostRules.stageThreshold(4, 24));
        assertEquals(1, SackGhostRules.stageForFear(383.99, 24));
        assertEquals(2, SackGhostRules.stageForFear(384, 24));
        assertEquals(2, SackGhostRules.stageForFear(863.99, 24));
        assertEquals(3, SackGhostRules.stageForFear(864, 24));
        assertEquals(3, SackGhostRules.stageForFear(1535.99, 24));
        assertEquals(4, SackGhostRules.stageForFear(1536, 24));
        assertEquals(4, SackGhostRules.stageForFear(100000, 24));
        assertEquals(1, SackGhostRules.stageForFear(Double.NaN, 24));
    }

    @Test void emptySanAndLockedMoodCannotFarmFearAndBonusMaxSanStillCounts() {
        assertEquals(0, SackGhostRules.actualSanLoss(0, 0));
        assertEquals(0, SackGhostRules.actualSanLoss(1, 1));
        assertEquals(0, SackGhostRules.actualSanLoss(.3, .5));
        assertEquals(5, SackGhostRules.actualSanLoss(.05, 0), 1e-8);
        assertEquals(20, SackGhostRules.actualSanLoss(1.5, 1.3), 1e-8);
        assertEquals(10, SackGhostRules.actualSanLoss(.1, -.5), 1e-8);
        assertEquals(0, SackGhostRules.actualSanLoss(Double.NaN, 0));
        assertEquals(0, SackGhostRules.actualSanLoss(1, Double.NEGATIVE_INFINITY));
        assertEquals(1.3, SackGhostRules.EROSION_MULTIPLIER);
    }

    @Test void stagesUnlockDeterministicArtPoolsWithoutRealm() {
        assertTrue(GhostArt.unlocked(0).isEmpty());
        assertTrue(GhostArt.drawPool(0, true).isEmpty());
        assertEquals(List.of(GhostArt.VEIL, GhostArt.PUSH, GhostArt.SHROUD), GhostArt.unlocked(1));
        assertEquals(5, GhostArt.unlocked(2).size());
        assertEquals(7, GhostArt.unlocked(3).size());
        assertEquals(List.of(GhostArt.values()), GhostArt.unlocked(4));
        for (int stage = 1; stage <= 4; stage++) {
            int current = stage;
            assertTrue(GhostArt.unlocked(stage).stream().allMatch(art -> art.stage <= current));
            assertTrue(GhostArt.unlocked(stage).stream().noneMatch(art -> art.id.contains("realm")));
            assertEquals(GhostArt.unlocked(stage), GhostArt.drawPool(stage, false));
            var afterShroud = GhostArt.drawPool(stage, true);
            assertEquals(GhostArt.unlocked(stage).size() - 1, afterShroud.size());
            assertFalse(afterShroud.contains(GhostArt.SHROUD));
            assertTrue(afterShroud.containsAll(List.of(GhostArt.VEIL, GhostArt.PUSH)));
        }
    }

    @Test void capCountsIndividualItemsIncludingDuplicatesAndRejectsOverflow() {
        assertTrue(SackGhostRules.canReceiveArts(0, 1));
        assertTrue(SackGhostRules.canReceiveArts(3, 1));
        assertTrue(SackGhostRules.canReceiveArts(0, 4));
        assertFalse(SackGhostRules.canReceiveArts(4, 1));
        assertFalse(SackGhostRules.canReceiveArts(3, 2));
        assertFalse(SackGhostRules.canReceiveArts(-1, 1));
        assertFalse(SackGhostRules.canReceiveArts(0, 0));
        assertFalse(SackGhostRules.canReceiveArts(0, Integer.MAX_VALUE));
        assertEquals(60, SackGhostRules.CAST_INTERVAL);
        assertEquals(200, SackGhostRules.REALM_CAST_INTERVAL);
        assertEquals(60, SackGhostRules.castInterval(false));
        assertEquals(200, SackGhostRules.castInterval(true));
    }

    @Test void relicRevelationIncludesTwentyBlockBoundaryAndRequiresBothPermissions() {
        assertTrue(SackGhostRules.relicCanReveal(true, true, 19.9 * 19.9));
        assertTrue(SackGhostRules.relicCanReveal(true, true, 400));
        assertFalse(SackGhostRules.relicCanReveal(true, true, 20.1 * 20.1));
        assertFalse(SackGhostRules.relicCanReveal(false, true, 1));
        assertFalse(SackGhostRules.relicCanReveal(true, false, 1));
        assertFalse(SackGhostRules.relicCanReveal(true, true, -1));
        assertFalse(SackGhostRules.relicCanReveal(true, true, Double.NaN));
        assertFalse(SackGhostRules.relicCanReveal(true, true, Double.POSITIVE_INFINITY));
    }

    @Test void chaseAndRescueHaveIndependentBudgets() {
        assertEquals(50, SackGhostRules.realmAttackSan(3));
        assertEquals(70, SackGhostRules.realmAttackSan(4));
        assertEquals(600, SackGhostRules.realmDuration(3));
        assertEquals(800, SackGhostRules.realmDuration(4));
        assertEquals(20, SackGhostRules.REALM_ATTACK_INTERVAL);
        assertEquals(2400, SackGhostRules.REALM_COOLDOWN);
        assertEquals(175, SackGhostRules.RELIC_BREAK_COST);
        assertEquals(900, SackGhostRules.RELIC_WARD_COOLDOWN);
        assertEquals(1200, SackGhostRules.RELIC_BREAK_COOLDOWN);
        assertEquals(120, SackGhostRules.WARD_DURATION);
    }

    @Test void cooldownsUseEffectiveClockAndNeverBecomeNegative() {
        assertEquals(120, SackGhostRules.remaining(100, 220));
        assertEquals(120, SackGhostRules.remaining(100, 220));
        assertEquals(1, SackGhostRules.remaining(219, 220));
        assertEquals(0, SackGhostRules.remaining(220, 220));
        assertEquals(0, SackGhostRules.remaining(221, 220));
        assertEquals(Integer.MAX_VALUE, SackGhostRules.remaining(0, Long.MAX_VALUE));
    }
    @Test void pausedEffectsRenewWithoutChangingTheirEffectiveDeadline() {
        long deadline = 140, activeClock = 100;
        int duration = 40;
        for (int pausedTick = 0; pausedTick < 100; pausedTick++) {
            duration--;
            int left = SackGhostRules.remaining(activeClock, deadline);
            int refresh = SackGhostRules.effectRefreshDuration(left, duration);
            if (refresh > 0) duration = refresh;
            assertTrue(duration >= 40);
            assertEquals(40, left);
        }
        assertEquals(0, SackGhostRules.remaining(deadline, deadline));
        assertEquals(0, SackGhostRules.effectRefreshDuration(0, duration));
        assertEquals(0, SackGhostRules.effectRefreshDuration(120, -1));
        assertEquals(Integer.MAX_VALUE, SackGhostRules.effectRefreshDuration(Integer.MAX_VALUE, 0));
    }

    @Test void externalEffectBaselineLosesElapsedTimeAndNeverTurnsInfiniteOnExpiry() {
        assertEquals(-1, SackGhostRules.remainingEffectTicks(-1, 500));
        assertEquals(80, SackGhostRules.remainingEffectTicks(120, 40));
        assertEquals(0, SackGhostRules.remainingEffectTicks(120, 120));
        assertEquals(0, SackGhostRules.remainingEffectTicks(120, 121));
        assertEquals(120, SackGhostRules.remainingEffectTicks(120, -5));
    }

    @Test void reusableShroudAndRealmSpellsKeepSeparateConsumptionRules() {
        for (GhostArt art : GhostArt.values()) {
            assertEquals(art != GhostArt.SHROUD, SackGhostRules.consumesOnCast(art, false));
            assertFalse(SackGhostRules.consumesOnCast(art, true));
        }
        assertEquals(3000, SackGhostRules.ESCAPE_COOLDOWN);
        assertEquals(240, SackGhostRules.ESCAPE_DURATION);
        assertEquals(1300, SackGhostRules.SHROUD_COOLDOWN);
        assertEquals(300, SackGhostRules.SHROUD_DURATION);
        assertEquals(160, SackGhostRules.ECHO_WINDOW);
        assertTrue(SackGhostRules.ECHO_WINDOW < SackGhostRules.REALM_CAST_INTERVAL);
    }

    @Test void parasiteExecutesOnlyZeroSanOrTheExplicitRobotException() {
        assertTrue(SackGhostRules.parasiteShouldExecute(false, 0));
        assertTrue(SackGhostRules.parasiteShouldExecute(false, -0.01));
        assertFalse(SackGhostRules.parasiteShouldExecute(false, 0.001));
        assertFalse(SackGhostRules.parasiteShouldExecute(false, Double.NaN));
        assertFalse(SackGhostRules.parasiteShouldExecute(false, Double.NEGATIVE_INFINITY));
        assertTrue(SackGhostRules.parasiteShouldExecute(true, 1));
        assertTrue(SackGhostRules.parasiteShouldExecute(true, Double.NaN));
    }

    @Test void neutralAndFakeSanTargetsUseVirtualHealthWithScaledDamage() {
        assertFalse(SackGhostRules.usesVirtualHealth(false, false));
        assertTrue(SackGhostRules.usesVirtualHealth(true, false));
        assertTrue(SackGhostRules.usesVirtualHealth(false, true));
        assertTrue(SackGhostRules.usesVirtualHealth(true, true));
        assertEquals(10, SackGhostRules.realmAttackVirtual(3));
        assertEquals(14, SackGhostRules.realmAttackVirtual(4));
        assertEquals(10, SackGhostRules.toVirtualDamage(SackGhostRules.realmAttackSan(3)));
        assertEquals(14, SackGhostRules.toVirtualDamage(SackGhostRules.realmAttackSan(4)));
        assertTrue(SackGhostRules.toVirtualDamage(0) > 0);
    }

    @Test void gunWarningsRespectAllegianceAndFifteenBlockBoundary() {
        assertEquals(0, SackGhostRules.gunThreat(false, 1, true, true));
        assertEquals(0, SackGhostRules.gunThreat(true, 1, false, false));
        assertEquals(1, SackGhostRules.gunThreat(true, 225, true, false));
        assertEquals(2, SackGhostRules.gunThreat(true, 225, true, true));
        assertEquals(0, SackGhostRules.gunThreat(true, 225.01, true, true));
        assertEquals(0, SackGhostRules.gunThreat(true, -1, true, true));
        assertEquals(0, SackGhostRules.gunThreat(true, Double.NaN, true, true));
        assertEquals(0, SackGhostRules.gunThreat(true, Double.POSITIVE_INFINITY, true, true));
    }

    @Test void finalArtNumbersAndGradualWailMatchConfirmedDesign() {
        assertEquals(32, SackGhostRules.VEIL_SAN);
        assertEquals(60, SackGhostRules.VEIL_DURATION);
        assertEquals(7, SackGhostRules.PUSH_RADIUS);
        assertEquals(9, SackGhostRules.PUSH_DISTANCE);
        assertEquals(35, SackGhostRules.EFFIGY_SAN);
        assertEquals(12, SackGhostRules.ECHO_DISTANCE);
        assertEquals(24, SackGhostRules.ECHO_SAN);
        assertEquals(40, SackGhostRules.WAIL_DURATION / 20 * SackGhostRules.WAIL_SAN);
        assertEquals(75, SackGhostRules.PARASITE_DURATION / 20 * SackGhostRules.PARASITE_SAN);
        assertEquals(30, SackGhostRules.SEIZE_SAN);
        assertEquals(0, SackGhostRules.wailSlowAmplifier(1));
        assertEquals(1, SackGhostRules.wailSlowAmplifier(40));
        assertEquals(2, SackGhostRules.wailSlowAmplifier(80));
        assertEquals(2, SackGhostRules.wailSlowAmplifier(1000));
    }
}
