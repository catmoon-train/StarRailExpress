package org.agmas.noellesroles.mixin.client.roles.command_block;

import org.agmas.noellesroles.client.CommandBlockProgressClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.exmo.sre.camera.client.AdvancedCameraDirector;

/** 命令方块开场期间，后到的飞机聚焦不能再把镜头抢走。 */
@Mixin(AdvancedCameraDirector.class)
public class CommandBlockCameraMixin {
    @Inject(method = "startEntityFocus", at = @At("HEAD"), cancellable = true)
    private static void keepCommandBlockCamera(int entityId, int durationTicks, boolean blackBars, CallbackInfo ci) {
        if (CommandBlockProgressClient.isShowing() && AdvancedCameraDirector.isTrackActive()) {
            ci.cancel();
        }
    }
}
