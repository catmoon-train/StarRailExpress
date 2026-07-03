package io.wifi.starrailexpress.mixin.client.ui;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.StatusEffectRenderer;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.client.hud.CommonClientHudRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(value = Gui.class, priority = 1100)
public class RenderEffectMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderEffects", at = @At("HEAD"))
    private void sre$moveEffectPostion_head(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        customRenderEffect(guiGraphics, deltaTracker);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, CommonClientHudRenderer.effectStartY, 0);
    }

    @Inject(method = "renderEffects", at = @At(value = "RETURN"))
    private void sre$moveEffectPostion_tail(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        guiGraphics.pose().popPose();
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void tmm$hideStatusEffectWhenCameraEvent(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        // 运镜动画期间与原版 F1 一致，完全隐藏状态效果。
        if (AdvancedCameraDirector.shouldOverride()) {
            ci.cancel();
            return;
        }
    }

    @Redirect(method = "renderEffects", at = @At(value = "INVOKE", target = "Ljava/util/Collection;isEmpty()Z"))
    private boolean tmm$hideEffect(Collection<MobEffectInstance> collection) {
        // 原版 HUD 模式下保留原版药水图标显示。

        LocalPlayer player = this.minecraft.player;
        if (player == null || SREClient.gameComponent == null) {
            return collection.isEmpty();
        }
        if (SREClient.shouldRenderVanillaHud() || !SREClient.gameComponent.isRunning()) {
            return collection.isEmpty();
        }
        return true;
    }

    private void customRenderEffect(GuiGraphics context, DeltaTracker tickCounter) {
        // 原版 HUD 模式下保留原版药水图标显示。

        LocalPlayer player = this.minecraft.player;
        if (player == null || SREClient.gameComponent == null) {
            return;
        }
        if (SREClient.shouldRenderVanillaHud() || !SREClient.gameComponent.isRunning()) {
            return;
        }
        Screen var5 = this.minecraft.screen;
        if (var5 instanceof EffectRenderingInventoryScreen effectRenderingInventoryScreen) {
            if (effectRenderingInventoryScreen.canSeeEffects()) {
                return;
            }
        }
        // 自定义排版：图标 + 剩余时间，Shift 展开显示名称（换行）。
        // 使用原版GuiGraphic
        StatusEffectRenderer.render(this.minecraft, context, player);
    }
}
