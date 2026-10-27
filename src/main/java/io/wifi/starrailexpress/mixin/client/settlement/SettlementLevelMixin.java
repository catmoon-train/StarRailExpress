package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在分发器之外再挡住现场实体，以及可能处于屏外的职业/方块实体渲染回调。 */
@Mixin(value = LevelRenderer.class, priority = 2500)
public abstract class SettlementLevelMixin {
    @Inject(method = {"renderEntity", "renderSnowAndRain", "renderClouds", "renderSky"},
            at = @At("HEAD"), cancellable = true)
    private void settlement$scene(CallbackInfo ci) { if (SettlementClient.isActive()) ci.cancel(); }
}
