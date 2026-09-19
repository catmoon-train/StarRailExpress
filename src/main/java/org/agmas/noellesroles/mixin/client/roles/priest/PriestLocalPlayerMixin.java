package org.agmas.noellesroles.mixin.client.roles.priest;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.PriestHeavenClient;
import org.agmas.noellesroles.game.roles.neutral.priest.PriestHeavenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 俯视镜头期间锁死移动。关闭 GUI 后，神父走路仍会自动进入冲刺以便加速。
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
        if (PriestHeavenClient.isMovementLocked()) {
            self.input.up = false;
            self.input.down = false;
            self.input.left = false;
            self.input.right = false;
            self.input.jumping = false;
            self.input.forwardImpulse = 0.0F;
            self.input.leftImpulse = 0.0F;
            self.setSprinting(false);
            self.setDeltaMovement(Vec3.ZERO);
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
