package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 防止结算中攻击或使用手持物品；不吞掉暂停/断线操作。 */
@Mixin(value = Minecraft.class, priority = 2500)
public abstract class SettlementInteractionMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void settlement$attack(CallbackInfoReturnable<Boolean> cir) {
        if (SettlementClient.isActive()) cir.setReturnValue(false);
    }
    @Inject(method = {"startUseItem", "continueAttack"}, at = @At("HEAD"), cancellable = true)
    private void settlement$use(CallbackInfo ci) { if (SettlementClient.isActive()) ci.cancel(); }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void settlement$dimension(net.minecraft.client.multiplayer.ClientLevel level,
                                      net.minecraft.client.gui.screens.ReceivingLevelScreen.Reason reason,
                                      CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (SettlementClient.isActive() && (level == null || mc.level == null
                || !mc.level.dimension().equals(level.dimension()))) SettlementClient.clear();
    }
}
