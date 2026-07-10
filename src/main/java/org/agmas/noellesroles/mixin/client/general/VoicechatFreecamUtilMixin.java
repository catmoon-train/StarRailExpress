package org.agmas.noellesroles.mixin.client.general;

import de.maxhenkel.voicechat.integration.freecam.FreecamUtil;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.TwoDimensionalCameraClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FreecamUtil.class, remap = false)
public class VoicechatFreecamUtilMixin {
    @Inject(method = "getReferencePoint", at = @At("HEAD"), cancellable = true, remap = false)
    private static void noellesroles$usePlayerAsVoiceReference(CallbackInfoReturnable<Vec3> cir) {
        Vec3 listener = TwoDimensionalCameraClientHandle.listenerPosition();
        if (listener != null) {
            cir.setReturnValue(listener);
        }
    }
}
