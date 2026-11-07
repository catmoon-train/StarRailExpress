package io.wifi.starrailexpress.client;

import net.minecraft.client.CloudStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ClientOptionPolicyTest {
    @Test void remainingVisualRulesAndEntityDistanceAreFixed() {
        assertEquals(false, ClientOptionPolicy.lockedValue("options.autoJump"));
        assertEquals(0.0D, ClientOptionPolicy.lockedValue("options.gamma"));
        assertEquals(1.0D, ClientOptionPolicy.lockedValue("options.darknessEffectScale"));
        assertEquals(false, ClientOptionPolicy.lockedValue("options.showSubtitles"));
        assertEquals(CloudStatus.OFF, ClientOptionPolicy.lockedValue("options.renderClouds"));
        assertEquals(1.0D, ClientOptionPolicy.lockedValue("options.entityDistanceScaling"));
    }

    @ParameterizedTest @ValueSource(strings = {"master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice"})
    void everySoundCategoryIsAdjustable(String source) {
        assertFalse(ClientOptionPolicy.isLocked("soundCategory." + source));
        assertNull(ClientOptionPolicy.lockedValue("soundCategory." + source));
    }

    @ParameterizedTest @ValueSource(strings = {"options.particles", "options.ao", "options.soundDevice", "options.renderDistance", "options.framerateLimit", "options.fullscreen", "options.fov"})
    void unlockedVisualDevicePerformanceAndComfortOptionsRemainAvailable(String key) {
        assertFalse(ClientOptionPolicy.isLocked(key));
        assertNull(ClientOptionPolicy.lockedValue(key));
    }
}
