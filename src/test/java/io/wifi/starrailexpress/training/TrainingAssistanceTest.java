package io.wifi.starrailexpress.training;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrainingAssistanceTest {
    private CompoundTag effect(int amplifier, int duration, boolean training) {
        var tag = new CompoundTag();
        tag.putString("id", "minecraft:speed");
        tag.putInt("amplifier", amplifier);
        tag.putInt("duration", duration);
        tag.putBoolean("ambient", training);
        tag.putBoolean("show_particles", !training);
        tag.putBoolean("show_icon", true);
        return tag;
    }

    @Test void ownershipRequiresExactLevelFlagsAndRemainingLease() {
        assertTrue(TrainingAssistance.matches(0, 40, true, false, true, 100, 60));
        assertTrue(TrainingAssistance.matches(0, 39, true, false, true, 100, 60));
        assertFalse(TrainingAssistance.matches(1, 40, true, false, true, 100, 60));
        assertFalse(TrainingAssistance.matches(0, 400, true, false, true, 100, 60));
        assertFalse(TrainingAssistance.matches(0, -1, true, false, true, 100, 60));
        assertFalse(TrainingAssistance.matches(0, 40, false, true, true, 100, 60));
        assertFalse(TrainingAssistance.matches(0, 0, true, false, true, 100, 100));
    }

    @Test void toggleOffRemovesOnlyOwnedEffect() {
        var own = effect(0, 40, true);
        assertNull(TrainingAssistance.stripOwned(own, 100, 60));
        assertEquals(40, own.getInt("duration"));
        var external = effect(0, 200, false);
        assertEquals(external, TrainingAssistance.stripOwned(external, 100, 60));
    }

    @Test void externalStrongerEffectSurvivesAndHiddenTrainingLeaseIsRemoved() {
        var external = effect(2, 300, false);
        external.put("hidden_effect", effect(0, 40, true));
        var remaining = TrainingAssistance.stripOwned(external, 100, 60);
        assertNotNull(remaining);
        assertEquals(2, remaining.getInt("amplifier"));
        assertEquals(300, remaining.getInt("duration"));
        assertFalse(remaining.contains("hidden_effect"));
        assertTrue(external.contains("hidden_effect"));
    }

    @Test void hiddenExternalEffectsKeepTheirOwnDurations() {
        var own = effect(0, 40, true);
        var external = effect(0, 20, false);
        external.put("hidden_effect", effect(0, -1, false));
        own.put("hidden_effect", external);
        assertEquals(external, TrainingAssistance.stripOwned(own, 100, 60));
    }

    @Test void foreignSameFlagsButDifferentExpiryCannotBeRemoved() {
        var external = effect(0, 240, true);
        assertEquals(external, TrainingAssistance.stripOwned(external, 100, 60));
        var infinite = effect(0, -1, true);
        assertEquals(infinite, TrainingAssistance.stripOwned(infinite, 100, 60));
    }
}
