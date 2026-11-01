package io.wifi.starrailexpress.client;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.ParticleStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ClientOptionPolicyTest {
    @Test void jumpBrightnessDarknessAndVisualRulesAreFixed() {
        assertEquals(false, ClientOptionPolicy.lockedValue("options.autoJump"));
        assertEquals(0.0D, ClientOptionPolicy.lockedValue("options.gamma"));
        assertEquals(1.0D, ClientOptionPolicy.lockedValue("options.darknessEffectScale"));
        assertEquals(false, ClientOptionPolicy.lockedValue("options.showSubtitles"));
        assertEquals(CloudStatus.OFF, ClientOptionPolicy.lockedValue("options.renderClouds"));
        assertEquals(ParticleStatus.ALL, ClientOptionPolicy.lockedValue("options.particles"));
        assertEquals(true, ClientOptionPolicy.lockedValue("options.ao"));
    }

    @ParameterizedTest @ValueSource(strings = {"weather", "block", "hostile", "neutral", "player", "ambient", "voice"})
    void gameplaySoundCategoriesStayAtFullVolume(String source) {
        assertEquals(1.0D, ClientOptionPolicy.lockedValue("soundCategory." + source));
    }

    @Test void originalMusicAndRecordLevelsArePreserved() {
        assertEquals(0.0D, ClientOptionPolicy.lockedValue("soundCategory.music"));
        assertEquals(0.1D, ClientOptionPolicy.lockedValue("soundCategory.record"));
    }

    @ParameterizedTest @ValueSource(strings = {"soundCategory.master", "options.soundDevice", "options.renderDistance", "options.framerateLimit", "options.fullscreen", "options.fov"})
    void userVolumeDevicesPerformanceAndComfortOptionsRemainAvailable(String key) {
        assertFalse(ClientOptionPolicy.isLocked(key));
        assertNull(ClientOptionPolicy.lockedValue(key));
    }
}
