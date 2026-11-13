package io.wifi.starrailexpress.api;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoleSkillInitialCooldownTest {
    private RoleSkill.Builder builder() {
        return RoleSkill.skill(ResourceLocation.fromNamespaceAndPath("test", "initial_cooldown"), "test.skill", context -> true);
    }
    @Test void legacySkillsRemainImmediatelyReady() {
        assertEquals(0, builder().cooldownSeconds(65).build().initialCooldownTicks());
    }
    @Test void initialAndRepeatCooldownsAreIndependent() {
        var definition = builder().initialCooldownSeconds(80).cooldownSeconds(65).build();
        assertEquals(1600, definition.initialCooldownTicks());
        assertEquals(1300, definition.cooldownTicks());
    }
    @Test void negativeInitialCooldownIsClampedToZero() {
        assertEquals(0, builder().initialCooldownTicks(-20).build().initialCooldownTicks());
    }
}
