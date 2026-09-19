package org.agmas.noellesroles.mixin.client.roles.priest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.agmas.noellesroles.client.PriestHeavenClient;
import org.agmas.noellesroles.client.screen.PriestChantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 咏诵 GUI 打开或俯视镜头期间清掉移动输入。
 */
@Mixin(KeyboardInput.class)
public abstract class PriestChantInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void noellesroles$priestStopMovingInGui(boolean isSneaking, float sneakSpeed, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        if (!(client.screen instanceof PriestChantScreen) && !PriestHeavenClient.isMovementLocked()) {
            return;
        }
        Input input = (Input) (Object) this;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        client.player.setSprinting(false);
    }
}
