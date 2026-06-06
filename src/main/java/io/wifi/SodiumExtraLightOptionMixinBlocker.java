package io.wifi;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import io.wifi.starrailexpress.SRE;

import java.util.List;

public class SodiumExtraLightOptionMixinBlocker implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        List<String> bannedMixins = List.of(
                "me.flashyreese.mods.sodiumextra.mixin.light_updates.MixinLevelLightEngine", // 光照
                "me.flashyreese.mods.sodiumextra.mixin.render.entity.MixinItemFrameEntityRenderer", // 物品展示框
                "me.flashyreese.mods.sodiumextra.mixin.render.entity.MixinLivingEntityRenderer", // 盔甲架
                "me.flashyreese.mods.sodiumextra.mixin.render.entity.MixinPaintingEntityRenderer" // 画
        );
        // ItemFrameRenderer
        if (bannedMixins.contains(mixinClassName)) {
            SRE.LOGGER.info("Blocked sodium-extra mixin: [" + mixinClassName + "]");
            return true;
        }
        return false;
    }
}
