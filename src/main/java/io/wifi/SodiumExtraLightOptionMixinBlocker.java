package io.wifi;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import io.wifi.starrailexpress.SRE;

import java.util.List;

public class SodiumExtraLightOptionMixinBlocker implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName.equals("me.flashyreese.mods.sodiumextra.mixin.light_updates.MixinLevelLightEngine")) {
            SRE.LOGGER.info("Blocked sodium-extra mixin: [MixinLevelLightEngine]");
            return true;
        }
        return false;
    }
}
