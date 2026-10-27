package org.agmas.noellesroles.mixin.client.echo;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.agmas.noellesroles.game.modes.echo.client.EchoRollClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 翻滚中只阻止新的移动加速和跳跃，不修改按键状态；落地后无需松键重按。 */
@Mixin(KeyboardInput.class)
public abstract class EchoRollInputMixin extends Input {
    @Inject(method="tick",at=@At("TAIL"))
    private void echo$rollInput(boolean slow,float multiplier,CallbackInfo ci) {
        if(!EchoRollClient.localRolling()) return;
        forwardImpulse=0; leftImpulse=0; jumping=false;
    }
}
