package io.wifi.srepackcompat.mixin.cobblemon;

import io.wifi.srepackcompat.cobblemon.ResettableCobblemonKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.client.keybind.CobblemonBlockingKeyBinding", remap = false)
public class HeldKeyMixin implements ResettableCobblemonKey {
    @Shadow private boolean wasDown;
    @Shadow private float timeDown;

    @Override
    public void sre$resetHeldKey() {
        wasDown = false;
        timeDown = 0;
    }
}
