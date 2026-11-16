package io.wifi.srepokemoncompat.mixin.mega;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** 不冻结服务端总 tick：只在关闭时收尾 Mega 自己的缩放任务，防止残留巨型实体。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.gimmick.MaxGimmick", remap = false)
public abstract class MegaScalingMixin {
    @Shadow @Final private static Map<Object, ?> ACTIVE_SCALING_ANIMATIONS;

    @Inject(method = "startGradualScaling", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sre$noNewScaling(CallbackInfo ci) {
        if (!CobblemonMechanics.enabled()) ci.cancel();
    }

    @Inject(method = "updateScalingAnimations", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sre$finishScalingWhenDisabled(CallbackInfo ci) {
        if (CobblemonMechanics.enabled()) return;
        for (var pokemon : ACTIVE_SCALING_ANIMATIONS.keySet()) {
            // 三个非混淆的 Cobblemon getter/setter 只在收尾时调用，不引入可选库编译依赖。
            try {
                var type = pokemon.getClass();
                var data = (CompoundTag) type.getMethod("getPersistentData").invoke(pokemon);
                float original = data.getFloat("orignal_size");
                if (Float.isFinite(original) && original > 0.0F) {
                    type.getMethod("setScaleModifier", float.class).invoke(pokemon, original);
                }
                var entity = (Entity) type.getMethod("getEntity").invoke(pokemon);
                if (entity != null) entity.setGlowingTag(false);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Mega Showdown scaling cleanup API changed", exception);
            }
        }
        ACTIVE_SCALING_ANIMATIONS.clear();
        ci.cancel();
    }
}
