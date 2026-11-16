package io.wifi.srepackcompat.mixin.cobblemon;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Mixin(KeyBindsList.class)
public class KeyBindsListMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;"))
    private KeyMapping[] sre$visibleKeys(KeyMapping[] keys) {
        if (CobblemonPolicy.enabled()) return keys;
        return Arrays.stream(keys).filter(key -> !CobblemonPolicy.isKey(key.getName())).toArray(KeyMapping[]::new);
    }
}
