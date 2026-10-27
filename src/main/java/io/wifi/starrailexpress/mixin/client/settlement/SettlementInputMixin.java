package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 保留 GLFW Enter 读取与 ESC 菜单，不注册新按键；禁止演出期间走离观众落点。 */
@Mixin(KeyboardInput.class)
public abstract class SettlementInputMixin extends net.minecraft.client.player.Input {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void settlement$movement(boolean slow, float factor, CallbackInfo ci) {
        if (!SettlementClient.isActive()) return;
        leftImpulse = forwardImpulse = 0;
        up = down = left = right = jumping = shiftKeyDown = false;
        ci.cancel();
    }
}
