package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 关闭原版及挂在 Gui.render 上的普通 HUD；结算叠层在 GameRenderer 尾部另画一次。 */
@Mixin(value = Gui.class, priority = 2500)
public abstract class SettlementGuiMixin {
    @Inject(method = {"render", "renderSavingIndicator"}, at = @At("HEAD"), cancellable = true)
    private void settlement$hud(CallbackInfo ci) { if (SettlementClient.isActive()) ci.cancel(); }
}
