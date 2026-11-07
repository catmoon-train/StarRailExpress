package io.wifi.srepackcompat.mixin.particle;

import io.wifi.srepackcompat.particle.ParticleCoreTickSafety;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在首次 tick 前登记，避免先让并行 tick 破坏 WaterFoam 的共享水面缓存。 */
@Mixin(value = ParticleEngine.class, priority = 99999)
public abstract class ParticleCoreAsyncSafetyMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void srepack$registerSynchronousFoam(CallbackInfo ci) {
        ParticleCoreTickSafety.registerWaterFoam(getClass());
    }
}
