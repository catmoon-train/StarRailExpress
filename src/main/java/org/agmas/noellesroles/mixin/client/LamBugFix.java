package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 使用 @Pseudo 标记表示这是一个伪 Mixin，当目标类不存在时不会导致编译失败
@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.LambDynLights")
public class LamBugFix {
    @Inject(method = "getLivingEntityLuminanceFromItems", at = @At("HEAD"), cancellable = true)
    private static void getLivingEntityLuminanceFromItems(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof PlayerBodyEntity) {
            cir.setReturnValue(3);
            cir.cancel();
        }
    }
}