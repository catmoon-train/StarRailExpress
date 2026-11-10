package io.wifi.srepackcompat.mixin.cobblemon;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.client.render.camera.MountedCameraRenderer", remap = false)
public class MountedCameraMixin {
    @Inject(method = "getCameraPosition", at = @At("HEAD"), cancellable = true)
    private void sre$vanillaPosition(CallbackInfoReturnable<Vec3> cir) {
        if (!CobblemonPolicy.enabled()) cir.setReturnValue(null);
    }

    @Inject(method = "setRotation", at = @At("HEAD"), cancellable = true)
    private void sre$vanillaRotation(CallbackInfoReturnable<Boolean> cir) {
        if (!CobblemonPolicy.enabled()) cir.setReturnValue(false);
    }

    @Inject(method = "applyCameraRotation", at = @At("HEAD"), cancellable = true)
    private void sre$noRoll(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}
