package io.wifi.srepackcompat.cobblemon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CobblemonSpawnBudgetTest {
    public static class Config {
        public volatile boolean pokmen;
        public static Config current = new Config();
        public static Config instance() { return current; }
    }
    public static class MissingFlag {
        public static MissingFlag instance() { return new MissingFlag(); }
    }

    @BeforeEach void reset() {
        Config.current = new Config();
        CobblemonSpawnBudget.resetLoadBudget();
    }

    @Test void startsWithFortyPercentBudget() {
        assertEquals(0.4F, CobblemonSpawnBudget.scale(1F));
    }

    @Test void severeLoadRetainsTenPercentWithoutDisablingServerSwitch() throws Exception {
        Config.current.pokmen = true;
        var enabled = CobblemonSpawnBudget.bind(Config.class);
        for (int tick = 0; tick < 20; tick++) CobblemonSpawnBudget.observeAverageTickNanos(500_000_000L);
        assertEquals(0.1F, CobblemonSpawnBudget.scale(1F));
        assertEquals(0.2F, CobblemonSpawnBudget.scale(Math.max(1F, 2F)));
        assertTrue(enabled.getAsBoolean());
        Config.current.pokmen = false;
        assertFalse(enabled.getAsBoolean());
        CobblemonSpawnBudget.resetLoadBudget();
        assertEquals(0.4F, CobblemonSpawnBudget.scale(1F));
    }

    @Test void scalesConfiguredSpawnTimerAdvance() {
        assertEquals(0.8F, CobblemonSpawnBudget.scale(2F));
        assertEquals(0.1F, CobblemonSpawnBudget.scale(0.25F));
        assertEquals(0F, CobblemonSpawnBudget.scale(0F));
    }

    @Test void scalesEffectivePopulationAfterGlobalAndLocalMaximum() {
        assertEquals(2F, CobblemonSpawnBudget.scale(Math.max(2F, 5F)));
        assertEquals(2F, CobblemonSpawnBudget.scale(Math.max(5F, 2F)));
        assertEquals(0.8F, CobblemonSpawnBudget.scale(Math.max(2F, 2F)));
    }

    @Test void decreasesAttemptFrequencyRatherThanSpeedingItUp() {
        float original = 100F, limited = 100F;
        for (int tick = 0; tick < 100; tick++) {
            original -= 1F;
            limited -= CobblemonSpawnBudget.scale(1F);
        }
        assertEquals(0F, original);
        assertEquals(60F, limited, 0.001F);
    }

    @Test void serverSwitchStartsDisabledAndTracksLiveChanges() throws Exception {
        var enabled = CobblemonSpawnBudget.bind(Config.class);
        assertFalse(enabled.getAsBoolean());
        Config.current.pokmen = true;
        assertTrue(enabled.getAsBoolean());
        Config.current.pokmen = false;
        assertFalse(enabled.getAsBoolean());
    }

    @Test void followsReloadedConfigInstanceInsteadOfCachingOldObject() throws Exception {
        var enabled = CobblemonSpawnBudget.bind(Config.class);
        Config.current.pokmen = true;
        assertTrue(enabled.getAsBoolean());
        Config.current = new Config();
        assertFalse(enabled.getAsBoolean());
        Config.current.pokmen = true;
        assertTrue(enabled.getAsBoolean());
    }

    @Test void missingCoreApiCannotSilentlyBecomeEnabled() {
        assertThrows(NoSuchFieldException.class, () -> CobblemonSpawnBudget.bind(MissingFlag.class));
    }
}
