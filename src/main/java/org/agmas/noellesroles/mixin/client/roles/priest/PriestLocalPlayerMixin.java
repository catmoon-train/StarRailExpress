package org.agmas.noellesroles.mixin.client.roles.priest;

import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.game.roles.neutral.priest.PriestHeavenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 咏诵 GUI 打开时不强制移动。关闭 GUI 后，神父走路仍会自动进入冲刺以便加速。
 */
@Mixin(value = LocalPlayer.class, priority = 1200)
public abstract class PriestLocalPlayerMixin {

    @Inject(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V"))
    private void noellesroles$priestKeepRunning(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self.input == null) {
            return;
        }
        if (PriestHeavenManager.shouldAutoRun(self)) {
            self.input.up = true;
            self.input.down = false;
            self.input.forwardImpulse = 1.0F;
            self.setSprinting(true);
            return;
        }
        boolean moving = self.input.hasForwardImpulse() || Math.abs(self.input.leftImpulse) > 1.0E-4F;
        if (moving && PriestHeavenManager.movementSpeedMultiplier(self) > 1.001F) {
            self.setSprinting(true);
        }
    }
}
