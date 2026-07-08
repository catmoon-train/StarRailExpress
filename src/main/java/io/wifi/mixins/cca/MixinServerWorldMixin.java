package io.wifi.mixins.cca;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.bawnorton.mixinsquared.TargetHandler;

import io.wifi.utils.cca.CCAManager;
@Mixin(value = net.minecraft.server.level.ServerLevel.class, priority = 1500)
public class MixinServerWorldMixin {
    // 覆盖 CCA 的 tick 方法
    @TargetHandler(mixin = "org.ladysnake.cca.mixin.entity.common.MixinServerWorld", // 完整类名
            name = "tick", prefix = "handler")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void tickConditional(Entity entity, CallbackInfo originalCi, CallbackInfo ci) {
        // 你的条件判断 —— 此处示例：仅当 entity 为玩家时执行
        if (CCAManager.shouldBlockEntityCCAServerTick(entity)) {
            ci.cancel();
        }
        // 否则什么都不做
    }

    // 覆盖 CCA 的 tick 方法
    @TargetHandler(mixin = "org.ladysnake.cca.mixin.entity.common.MixinServerWorld", // 完整类名
            name = "tickRiding", prefix = "handler" // 目标方法名（最好带上描述符，但简单名也可以）
    )
    @Inject(method = "@MixinSquared:Handler", // 固定写法，表示由 @TargetHandler 指定的方法
            at = @At("HEAD"), // 这里用 HEAD，因为我们要在方法开头加判断
            cancellable = true)
    private void tickRidingConditional(Entity vehicle, Entity passenger,  CallbackInfo originalCi, CallbackInfo ci) {
        // 你的条件判断 —— 此处示例：仅当 entity 为玩家时执行
        if (CCAManager.shouldBlockEntityCCAServerTick(passenger)) {
            ci.cancel();
        }
        // 否则什么都不做
    }
}
