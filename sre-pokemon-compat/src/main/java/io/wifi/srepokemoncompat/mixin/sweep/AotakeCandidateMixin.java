package io.wifi.srepokemoncompat.mixin.sweep;

import io.wifi.srepokemoncompat.sweep.SweepProtection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xin.vanilla.aotake.util.AotakeUtils", remap = false)
public class AotakeCandidateMixin {
    @Inject(method = "prepareSweepCandidate", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sre$protectCandidate(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SweepProtection.isProtected(entity)) cir.setReturnValue(false);
    }
}
