package io.wifi.srepackcompat.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SettingsPageVisibilityTest {
    @Test void hidesOnlyRequestedModGroups() {
        for (String id : Set.of("betterblockentities", "chloride", "gnetum", "lambdynlights", "lambdynlights_runtime",
                "reeses-sodium-options", "reeses_sodium_options", "cullify")) {
            assertTrue(SettingsPageVisibility.hideMod(id), id);
        }
        for (String id : Set.of("sodium", "sodium-extra", "iris", "other_mod")) {
            assertFalse(SettingsPageVisibility.hideMod(id), id);
        }
    }

    @Test void hidesThreeExtraPagesUsingStableOptionIdsNotTranslatedLabels() {
        for (String marker : Set.of("animations_all", "sky", "advanced_fog_settings")) {
            assertTrue(SettingsPageVisibility.hideExtraPage(Set.of(marker)));
        }
    }

    @Test void retainsExtraOtherPageAndUnknownFuturePages() {
        assertFalse(SettingsPageVisibility.hideExtraPage(Set.of("show_fps", "overlay_corner", "toasts", "panini_projection")));
        assertFalse(SettingsPageVisibility.hideExtraPage(Set.of("future_option")));
        assertFalse(SettingsPageVisibility.hideExtraPage(Set.of()));
        assertFalse(SettingsPageVisibility.hideExtraPage(Set.of("particles_all", "rain_splash_particles",
                "block_break_particles", "block_breaking_particles", "particle.options.particles.minecraft.smoke")));
    }
}
