package io.wifi.sreperf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.sreperf.CaptureController;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    @WrapMethod(method = "tick")
    private void sreperf$tick(Operation<Void> original) {
        long token = CaptureController.begin("particle_tick");
        try { original.call(); }
        finally { CaptureController.end("particle_tick", token, 0); }
    }

    @WrapMethod(method = "render")
    private void sreperf$render(LightTexture light, Camera camera, float delta, Operation<Void> original) {
        long token = CaptureController.begin("particle_render");
        try { original.call(light, camera, delta); }
        finally { CaptureController.end("particle_render", token, 0); }
    }
}
