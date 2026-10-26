package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 较低注入优先级让最终遮罩位于其他普通尾注入之后；全帧只画这一处。 */
@Mixin(value = GameRenderer.class, priority = 500)
public abstract class SettlementFinalOverlayMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void settlement$finalOverlay(DeltaTracker delta, boolean tick, CallbackInfo ci) {
        SettlementOverlay.render(delta.getGameTimeDeltaPartialTick(false));
    }
}
