package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SreMixinCancellationPolicyTest {
    @Test
    void matchesOriginalWathePrefixesAndRatatouilleException() {
        var policy = new SreMixinCancellationPolicy(true, false);
        assertTrue(policy.cancels("dev.doctor4t.wathe.mixin.PlayerEntityMixin"));
        assertTrue(policy.cancels("dev.doctor4t.watheExtended"));
        assertTrue(policy.cancels("dev.doctor4t.ratatouille.mixin.client.WorldRendererMixin"));
        assertFalse(policy.cancels("dev.doctor4t.ratatouille.mixin.client.BlockRenderManagerAccessor"));
        assertFalse(policy.cancels("dev.doctor4t.ratatouille.mixin.client.BlockRenderManagerAccessor$Extra"));
        assertFalse(policy.cancels("dev.doctor4t.ratatouille.util.Helper"));
        assertFalse(policy.cancels(null));
    }

    @Test
    void matchesExactLightClassAndOriginalContainsRules() {
        var policy = new SreMixinCancellationPolicy(false, true);
        assertTrue(policy.cancels("me.flashyreese.mods.sodiumextra.mixin.light_updates.MixinLevelLightEngine"));
        assertFalse(policy.cancels("me.flashyreese.mods.sodiumextra.mixin.light_updates.MixinLevelLightEngine2"));
        assertTrue(policy.cancels("prefix.me.flashyreese.mods.sodiumextra.mixin.fog.MixinFog"));
        assertTrue(policy.cancels("me.flashyreese.mods.sodiumextra.mixin.render.entity.MixinEntity"));
        assertFalse(policy.cancels("me.flashyreese.mods.sodiumextra.mixin.render.block.MixinBlock"));
    }

    @Test
    void unverifiedRulesCannotCancelAnything() {
        for (String name : new String[]{"dev.doctor4t.wathe.mixin.Mixin", "dev.doctor4t.ratatouille.mixin.Mixin",
                "me.flashyreese.mods.sodiumextra.mixin.fog.Mixin"}) {
            assertFalse(new SreMixinCancellationPolicy(false, false).cancels(name));
        }
        assertFalse(new SreMixinCancellationPolicy(false, true).cancels("dev.doctor4t.wathe.mixin.Mixin"));
        assertFalse(new SreMixinCancellationPolicy(true, false).cancels("me.flashyreese.mods.sodiumextra.mixin.fog.Mixin"));
    }
}
