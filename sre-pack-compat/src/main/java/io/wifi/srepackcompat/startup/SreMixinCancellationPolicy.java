package io.wifi.srepackcompat.startup;

/** 只复现已校验核心取消器的名称规则，不扩展原来禁用的范围。 */
record SreMixinCancellationPolicy(boolean watheRule, boolean sodiumExtraRule) {
    boolean cancels(String name) {
        if (name == null) return false;
        return watheRule && (name.startsWith("dev.doctor4t.wathe")
                || name.startsWith("dev.doctor4t.ratatouille.mixin")
                && !name.startsWith("dev.doctor4t.ratatouille.mixin.client.BlockRenderManagerAccessor"))
                || sodiumExtraRule && (name.equals("me.flashyreese.mods.sodiumextra.mixin.light_updates.MixinLevelLightEngine")
                || name.contains("me.flashyreese.mods.sodiumextra.mixin.fog")
                || name.contains("me.flashyreese.mods.sodiumextra.mixin.render.entity"));
    }
}
