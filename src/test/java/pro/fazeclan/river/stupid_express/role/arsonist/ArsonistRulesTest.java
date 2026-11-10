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

    @Test void twentyPulsesFillHeatWithoutOverflow() {
        int heat = 0;
        for (int i = 0; i < 19; i++) heat = ArsonistRules.heatAfterPulse(heat);
        assertEquals(95, heat);
        assertEquals(100, ArsonistRules.heatAfterPulse(heat));
        assertEquals(100, ArsonistRules.heatAfterPulse(100));
        assertEquals(2, ArsonistRules.PULSE_TICKS);
    }

    @Test void fuelUsesExactOneAndAHalfPercentAndClampsAtEmpty() {
        int fuel = ArsonistRules.FULL_FUEL;
        for (int i = 0; i < 66; i++) fuel = ArsonistRules.fuelAfterPulse(fuel);
        assertEquals(10, fuel);
        assertEquals(0, ArsonistRules.fuelAfterPulse(fuel));
        assertEquals(0, ArsonistRules.fuelAfterPulse(0));
        assertEquals(200, ArsonistRules.RELOAD_TICKS);
    }

    @Test void flameIsFrontHalfSphereWithFiveBlockBoundary() {
        assertTrue(ArsonistRules.inFlame(0, 0, 5, 0, 0, 1));
        assertTrue(ArsonistRules.inFlame(5, 0, 0, 0, 0, 1));
        assertTrue(ArsonistRules.inFlame(0, 0, 0, 0, 0, 1));
        assertFalse(ArsonistRules.inFlame(0, 0, 5.01, 0, 0, 1));
        assertFalse(ArsonistRules.inFlame(0, 0, -0.01, 0, 0, 1));
        assertFalse(ArsonistRules.inFlame(0, 5.01, 0, 0, 0, 1));
        assertTrue(ArsonistRules.inFlame(0, 4, 0, 0, 1, 0));
        assertFalse(ArsonistRules.inFlame(0, -4, 0, 0, 1, 0));
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
}
