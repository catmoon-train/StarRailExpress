package io.wifi;

import java.util.List;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import net.minecraft.client.Options;

public class WatheMixinBlocker implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName.startsWith("dev.doctor4t.wathe"))
            return true;
        return false;
    }
}
