package io.wifi;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;

public class WatheMixinBlocker implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName.startsWith("dev.doctor4t.wathe"))
            return true;
        return false;
    }
}
