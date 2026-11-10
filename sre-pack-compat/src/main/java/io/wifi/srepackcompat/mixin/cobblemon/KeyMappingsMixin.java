package io.wifi.srepackcompat.mixin.cobblemon;

import com.mojang.blaze3d.platform.InputConstants;
import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** 注册和持久化不变，仅从有效映射中排除关闭的按键，避免抢占同键列车操作。 */
@Mixin(KeyMapping.class)
public class KeyMappingsMixin {
    @Redirect(method = "resetMapping",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object sre$activeMapping(Map<Object, Object> map, Object key, Object value) {
        if (key instanceof InputConstants.Key && value instanceof KeyMapping binding
                && !CobblemonPolicy.enabled() && CobblemonPolicy.isKey(binding.getName())) return null;
        return map.put(key, value);
    }

    @Redirect(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 1))
    private Object sre$constructedMapping(Map<Object, Object> map, Object key, Object value) {
        return sre$activeMapping(map, key, value);
    }

    @Inject(method = {"isDown", "consumeClick", "matches", "matchesMouse"}, at = @At("HEAD"), cancellable = true)
    private void sre$ignoreDisabledKey(CallbackInfoReturnable<Boolean> cir) {
        if (!CobblemonPolicy.enabled() && CobblemonPolicy.isKey(((KeyMapping) (Object) this).getName())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void sre$ignoreHiddenConflict(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        if (!CobblemonPolicy.enabled() && (CobblemonPolicy.isKey(((KeyMapping) (Object) this).getName())
                || CobblemonPolicy.isKey(other.getName()))) cir.setReturnValue(false);
    }
}
