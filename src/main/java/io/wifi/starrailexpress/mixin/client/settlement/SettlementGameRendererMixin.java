package io.wifi.starrailexpress.mixin.client.settlement;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 结算独占手、普通视场及屏幕抖动；最终叠层不受 HUD 取消影响。 */
@Mixin(value = GameRenderer.class, priority = 2500)
public abstract class SettlementGameRendererMixin {

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void settlement$fov(Camera camera, float partial, boolean useSetting, CallbackInfoReturnable<Double> cir) {
        if (!SettlementClient.isActive()) return;
        cir.setReturnValue(settlement$fovValue());
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void settlement$hand(CallbackInfo ci) { if (SettlementClient.isActive()) ci.cancel(); }

    @Inject(method = {"bobView", "bobHurt"}, at = @At("HEAD"), cancellable = true)
    private void settlement$bob(PoseStack pose, float partial, CallbackInfo ci) {
        if (SettlementClient.isActive()) ci.cancel();
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void settlement$outline(CallbackInfoReturnable<Boolean> cir) {
        if (SettlementClient.isActive()) cir.setReturnValue(false);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"))
    private void settlement$reassert(DeltaTracker delta, CallbackInfo ci) {
        if (SettlementClient.isActive()) SettlementClient.reassertCamera(
                net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    @Inject(method = "resetProjectionMatrix", at = @At("HEAD"))
    private void settlement$projection(org.joml.Matrix4f matrix, CallbackInfo ci) {
        if (!SettlementClient.isActive()) return;
        GameRenderer renderer = (GameRenderer) (Object) this;
        // 覆盖眩晕/重力反转等世界投影修改，保留原版透视构造；不影响最终 GUI 投影。
        matrix.set(renderer.getProjectionMatrix(settlement$fovValue()));
        matrix.rotateZ(SettlementClient.cameraRoll(net.minecraft.client.Minecraft.getInstance()
                .getTimer().getGameTimeDeltaPartialTick(false)));
    }

    private static double settlement$fovValue() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return SettlementClient.cameraFov(mc.getTimer().getGameTimeDeltaPartialTick(false));
    }
}
