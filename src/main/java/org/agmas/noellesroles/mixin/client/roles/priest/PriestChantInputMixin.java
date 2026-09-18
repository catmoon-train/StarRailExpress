package org.agmas.noellesroles.mixin.client.roles.priest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.agmas.noellesroles.client.screen.PriestChantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 咏诵 GUI 打开时清掉移动输入，避免打字时角色自己往前跑。
 */
@Mixin(KeyboardInput.class)
public abstract class PriestChantInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void noellesroles$priestStopMovingInGui(boolean isSneaking, float sneakSpeed, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || !(client.screen instanceof PriestChantScreen)) {
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
