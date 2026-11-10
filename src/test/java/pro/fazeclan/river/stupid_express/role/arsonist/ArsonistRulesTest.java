package pro.fazeclan.river.stupid_express.role.arsonist;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArsonistRulesTest {
    @Test void ignitionRequiresEightyPercentRoundedUp() {
        assertEquals(1, ArsonistRules.requiredDoused(0));
        assertEquals(1, ArsonistRules.requiredDoused(1));
        assertEquals(4, ArsonistRules.requiredDoused(5));
        assertEquals(5, ArsonistRules.requiredDoused(6));
        assertEquals(8, ArsonistRules.requiredDoused(10));
        assertEquals(9, ArsonistRules.requiredDoused(11));
    }

    @Test void twentyTicksFillHeatInOneSecondWithoutOverflow() {
        int heat = 0;
        for (int i = 0; i < 19; i++) heat = ArsonistRules.heatAfterTick(heat);
        assertEquals(95, heat);
        assertEquals(100, ArsonistRules.heatAfterTick(heat));
        assertEquals(100, ArsonistRules.heatAfterTick(100));
        assertEquals(5, ArsonistRules.HEAT_PER_TICK);
    }

    @Test void fuelUsesExactOneAndAHalfPercentAndClampsAtEmpty() {
        int fuel = ArsonistRules.FULL_FUEL;
        for (int i = 0; i < 66; i++) fuel = ArsonistRules.fuelAfterPulse(fuel);
        assertEquals(10, fuel);
        assertEquals(0, ArsonistRules.fuelAfterPulse(fuel));
        assertEquals(0, ArsonistRules.fuelAfterPulse(0));
        assertEquals(130, ArsonistRules.RELOAD_TICKS);
        assertEquals(2, ArsonistRules.FUEL_PULSE_TICKS);
    }

    @Test void flameIsFrontConeWithFiveBlockBoundary() {
        assertEquals(5.0D, ArsonistRules.FLAME_RADIUS);
        assertEquals(35.0D, ArsonistRules.FLAME_HALF_ANGLE_DEGREES);
        assertTrue(ArsonistRules.inFlame(0, 0, 5, 0, 0, 1));
        assertTrue(ArsonistRules.inFlame(2, 0, 4, 0, 0, 1));
        assertTrue(ArsonistRules.inFlame(0, 0, 0, 0, 0, 1));
        // 90° 正侧面与 63.4° 斜前方都在 5 格半径内，但已超出 35° 锥
        assertFalse(ArsonistRules.inFlame(5, 0, 0, 0, 0, 1));
        assertFalse(ArsonistRules.inFlame(4, 0, 2, 0, 0, 1));
        assertFalse(ArsonistRules.inFlame(0, 0, 5.01, 0, 0, 1));
        assertFalse(ArsonistRules.inFlame(0, 0, -0.01, 0, 0, 1));
        assertFalse(ArsonistRules.inFlame(0, 5.01, 0, 0, 0, 1));
        assertTrue(ArsonistRules.inFlame(0, 4, 0, 0, 1, 0));
        assertFalse(ArsonistRules.inFlame(0, -4, 0, 0, 1, 0));
    }

    @Test void shieldsUseLivingPopulationInGroupsOfThreeWithFourLayerCap() {
        assertEquals(0, ArsonistRules.momentShields(-1));
        assertEquals(0, ArsonistRules.momentShields(0));
        assertEquals(0, ArsonistRules.momentShields(2));
        assertEquals(1, ArsonistRules.momentShields(3));
        assertEquals(1, ArsonistRules.momentShields(5));
        assertEquals(2, ArsonistRules.momentShields(6));
        assertEquals(3, ArsonistRules.momentShields(11));
        assertEquals(4, ArsonistRules.momentShields(12));
        assertEquals(4, ArsonistRules.momentShields(30));
    }

    @Test void heatTicksDoNotDoubleFuelConsumption() {
        int heat = 0;
        int fuel = ArsonistRules.FULL_FUEL;
        long lastFuel = Long.MIN_VALUE;
        for (int tick = 0; tick < 20; tick++) {
            heat = ArsonistRules.heatAfterTick(heat);
            if (ArsonistRules.shouldConsumeFuel(tick, lastFuel)) {
                fuel = ArsonistRules.fuelAfterPulse(fuel);
                lastFuel = tick;
            }
        }
        assertEquals(100, heat);
        assertEquals(850, fuel);
    }

    @Test void momentRequiresAllOpponentsDeadAndLastTickKillWins() {
        assertEquals(ArsonistRules.MomentResult.RUNNING, ArsonistRules.momentResult(1, 1));
        assertEquals(ArsonistRules.MomentResult.TIMEOUT, ArsonistRules.momentResult(0, 1));
        assertEquals(ArsonistRules.MomentResult.TIMEOUT, ArsonistRules.momentResult(-1, 5));
        assertEquals(ArsonistRules.MomentResult.VICTORY, ArsonistRules.momentResult(80, 0));
        assertEquals(ArsonistRules.MomentResult.VICTORY, ArsonistRules.momentResult(0, 0));
    }

    @Test void failedMomentSetsThirtySecondsForEachRemainingOpponent() {
        assertEquals(0, ArsonistRules.failedMomentTimeTicks(0));
        assertEquals(0, ArsonistRules.failedMomentTimeTicks(-1));
        assertEquals(30 * 20, ArsonistRules.failedMomentTimeTicks(1));
        assertEquals(90 * 20, ArsonistRules.failedMomentTimeTicks(3));
        assertEquals(360 * 20, ArsonistRules.failedMomentTimeTicks(12));
    }

    @Test void introCorpseAndReloadUseIndependentDurations() {
        assertEquals(120, ArsonistRules.INTRO_TICKS);
        assertEquals(60, ArsonistRules.BODY_BURN_TICKS);
        assertEquals(130, ArsonistRules.RELOAD_TICKS);
        assertEquals(1600, ArsonistRules.MOMENT_TICKS);
        assertEquals(76, ArsonistRules.TITLE_START_TICK);
        assertEquals(8, ArsonistRules.TITLE_STEP_TICKS);
        assertTrue(ArsonistRules.TITLE_START_TICK + ArsonistRules.TITLE_STEP_TICKS * 3 < ArsonistRules.INTRO_TICKS);
    }

    @Test void expiredFiniteEffectsNeverBecomeInfiniteAfterIntro() {
        assertEquals(-1, ArsonistRules.remainingEffectTicks(-1, 120));
        assertEquals(0, ArsonistRules.remainingEffectTicks(119, 120));
        assertEquals(0, ArsonistRules.remainingEffectTicks(120, 120));
        assertEquals(1, ArsonistRules.remainingEffectTicks(121, 120));
        assertEquals(60, ArsonistRules.remainingEffectTicks(60, -10));
    }

    @Test void economicsAndMomentMatchTheRoleSpecification() {
        assertEquals(10, ArsonistRules.TASK_COINS);
        assertEquals(35, ArsonistRules.MARK_COINS);
        assertEquals(280, ArsonistRules.OIL_CAN_PRICE);
        assertEquals(50, ArsonistRules.FIRE_GUN_PRICE);
        assertEquals(100, ArsonistRules.SHOT_COINS);
        assertEquals(600, ArsonistRules.SHOT_COOLDOWN_TICKS);
        assertEquals(1600, ArsonistRules.MOMENT_TICKS);
    }

    @Test void failedIgniteNeedsASecondClickWithinThreeSeconds() {
        assertEquals(60, ArsonistRules.FAILED_IGNITE_WINDOW_TICKS);
        assertFalse(ArsonistRules.withinFailedIgniteWindow(0, Long.MIN_VALUE));
        assertTrue(ArsonistRules.withinFailedIgniteWindow(0, 0));
        assertTrue(ArsonistRules.withinFailedIgniteWindow(60, 0));
        assertFalse(ArsonistRules.withinFailedIgniteWindow(61, 0));
        assertFalse(ArsonistRules.withinFailedIgniteWindow(59, 60));
    }
}
