package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 屏蔽现场残留粒子，舞台高亮使用独立几何，不依赖粒子引擎。 */
@Mixin(value = ParticleEngine.class, priority = 2500)
public abstract class SettlementParticleMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void settlement$particles(CallbackInfo ci) { if (SettlementClient.isActive()) ci.cancel(); }
}
