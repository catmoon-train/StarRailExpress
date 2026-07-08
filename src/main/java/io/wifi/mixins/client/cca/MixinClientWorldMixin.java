package io.wifi.mixins.client.cca;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.bawnorton.mixinsquared.TargetHandler;

import io.wifi.utils.cca.CCAManagerClient;

@Mixin(value = net.minecraft.client.multiplayer.ClientLevel.class, priority = 1500)
public class MixinClientWorldMixin {
    // 覆盖 CCA 的 tick 方法
    @TargetHandler(mixin = "org.ladysnake.cca.mixin.entity.client.MixinClientWorld", // 完整类名
            name = "tick", prefix = "handler")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void tickConditional(Entity entity, CallbackInfo originalCi, CallbackInfo ci) {
        // 你的条件判断 —— 此处示例：仅当 entity 为玩家时执行
        if (CCAManagerClient.shouldBlockEntityCCAClientTick(entity)) {
            ci.cancel();
        }
        // 否则什么都不做
    }

    // 覆盖 CCA 的 tick 方法
    @TargetHandler(mixin = "org.ladysnake.cca.mixin.entity.client.MixinClientWorld", // 完整类名
            name = "tickRiding", prefix = "handler"
    )
    @Inject(method = "@MixinSquared:Handler", // 固定写法，表示由 @TargetHandler 指定的方法
            at = @At("HEAD"), // 这里用 HEAD，因为我们要在方法开头加判断
            cancellable = true)
    private void tickRidingConditional(Entity vehicle, Entity passenger,  CallbackInfo originalCi, CallbackInfo ci) {
        // 你的条件判断 —— 此处示例：仅当 entity 为玩家时执行
        if (CCAManagerClient.shouldBlockEntityCCAClientTick(passenger)) {
            ci.cancel();
        }
        // 否则什么都不做
    }
}
