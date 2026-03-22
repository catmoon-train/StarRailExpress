package io.wifi.starrailexpress.mixin.client.ui;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.ratatouille.client.lib.render.helpers.Easing;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.SansRenderer;
import io.wifi.starrailexpress.client.StaminaRenderer;
import io.wifi.starrailexpress.client.StatusBarHUD;
import io.wifi.starrailexpress.client.gui.*;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(Gui.class)
public class InGameHudMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Unique
    private static final ResourceLocation TMM_HOTBAR_TEXTURE = SRE.watheId("hud/hotbar");
    @Unique
    private static final ResourceLocation TMM_HOTBAR_SELECTION_TEXTURE = SRE.watheId("hud/hotbar_selection");

    @Inject(method = "renderHotbarAndDecorations", at = @At("TAIL"))
    private void tmm$renderHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (SREClient.isInLobby) {
            return;
        }
        if (SREClient.trainComponent != null && SREClient.trainComponent.hasHud()) {
            LocalPlayer player = this.minecraft.player;
            if (player == null)
                return;
            Font renderer = Minecraft.getInstance().font;
            MoodRenderer.renderHud(player, renderer, context, tickCounter);
            RoleNameRenderer.renderHud(renderer, player, context, tickCounter);
            RoundTextRenderer.renderHud(renderer, player, context, tickCounter.getRealtimeDeltaTicks());
            StatusBarHUD.getInstance().render(context, tickCounter.getRealtimeDeltaTicks());
            if (Minecraft.getInstance().screen == null)
                StoreRenderer.renderHud(renderer, player, context, tickCounter.getGameTimeDeltaPartialTick(true));
            TimeRenderer.renderHud(renderer, player, context, tickCounter.getGameTimeDeltaPartialTick(true));
            StaminaRenderer.renderHud(player, context, tickCounter.getGameTimeDeltaPartialTick(true));
            SansRenderer.instance.tick(player, context, tickCounter.getGameTimeDeltaPartialTick(true));
            LobbyPlayersRenderer.renderHud(renderer, player, context);
        }
    }

    @WrapMethod(method = "renderCrosshair")
    private void tmm$renderHud(GuiGraphics context, DeltaTracker tickCounter, Operation<Void> original) {
        if (SREClient.isInLobby || !SREClient.isPlayerAliveAndInSurvival()) {
            original.call(context, tickCounter);
            return;
        }
        LocalPlayer player = this.minecraft.player;
        if (player == null)
            return;
        CrosshairRenderer.renderCrosshair(this.minecraft, player, context, tickCounter);
    }

    @WrapMethod(method = "renderPlayerHealth")
    private void tmm$removeStatusBars(GuiGraphics context, Operation<Void> original) {

        if (SREClient.isInLobby || !SREClient.isPlayerAliveAndInSurvival()) {
            original.call(context);
        }
    }

    @WrapMethod(method = "renderExperienceBar")
    private void tmm$removeExperienceBar(GuiGraphics context, int x, Operation<Void> original) {
        if (SREClient.isInLobby || !SREClient.isPlayerAliveAndInSurvival()) {
            original.call(context, x);
        }
    }

    @WrapMethod(method = "renderTabList")
    private void tmm$removePlayerList(GuiGraphics context, DeltaTracker tickCounter, Operation<Void> original) {
        if (SREClient.isInLobby || !SREClient.isPlayerAliveAndInSurvival())
            original.call(context, tickCounter);
    }

    @WrapMethod(method = "renderExperienceLevel")
    private void tmm$removeExperienceLevel(GuiGraphics context, DeltaTracker tickCounter, Operation<Void> original) {
        if (SREClient.isInLobby || !SREClient.isPlayerAliveAndInSurvival()) {
            original.call(context, tickCounter);
        }
    }

    @WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 0))
    private void tmm$overrideHotbarTexture(GuiGraphics instance, ResourceLocation texture, int x, int y, int width,
            int height, @NotNull Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(instance, texture, x, y, width,
                    height);
            return;
        }
        original.call(instance, SREClient.isPlayerAliveAndInSurvival() ? TMM_HOTBAR_TEXTURE : texture, x, y, width,
                height);
    }

    @WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 1))
    private void tmm$overrideHotbarSelectionTexture(GuiGraphics instance, ResourceLocation texture, int x, int y,
            int width, int height, @NotNull Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(instance, texture, x, y, width,
                    height);
            return;
        }
        original.call(instance, SREClient.isPlayerAliveAndInSurvival() ? TMM_HOTBAR_SELECTION_TEXTURE : texture, x, y,
                width, height);
    }

    @WrapMethod(method = "renderCameraOverlays")
    private void tmm$moveSleepOverlayToUnderUI(GuiGraphics context, DeltaTracker tickCounter,
            Operation<Void> original) {
        // sleep overlay
        if (SREClient.isInLobby) {
            original.call(context, tickCounter);
            return;
        }
        if (this.minecraft.player != null && this.minecraft.player.getSleepTimer() > 0) {
            this.minecraft.getProfiler().push("sleep");

            float f = (float) this.minecraft.player.getSleepTimer();

            float g = Math.min(1, f / 30f);

            if (f > 100f) {
                g = 1 - (f - 100f) / 10f;
            }

            float fadeAlpha = Mth.lerp(Mth.clamp(Easing.SINE_IN.ease(g, 0, 1, 1), 0, 1), 0f, 1f);
            Color color = new Color(0.04f, 0f, 0.08f, fadeAlpha);
            context.fill(RenderType.guiOverlay(), 0, 0, context.guiWidth(), context.guiHeight(), color.getRGB());

            this.minecraft.getProfiler().pop();
        }
    }

    @WrapMethod(method = "renderSleepOverlay")
    private void tmm$removeSleepOverlayAndDoGameFade(GuiGraphics context, DeltaTracker tickCounter,
            Operation<Void> original) {
        if (SREClient.isInLobby) {
            original.call(context, tickCounter);
            return;
        }
        if (SREClient.gameComponent != null) {

            // game start / stop fade in / out
            float fadeIn = SREClient.gameComponent.getFade();
            if (fadeIn >= 0) {
                this.minecraft.getProfiler().push("tmmFade");
                float fadeAlpha = Mth.lerp(Math.min(fadeIn / GameConstants.FADE_TIME, 1), 0f, 1f);
                Color color = new Color(0f, 0f, 0f, fadeAlpha);

                context.fill(RenderType.guiOverlay(), 0, 0, context.guiWidth(), context.guiHeight(), color.getRGB());
                this.minecraft.getProfiler().pop();
            }
        }
    }
}
