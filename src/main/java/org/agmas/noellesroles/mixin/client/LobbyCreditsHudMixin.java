package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import org.agmas.noellesroles.Noellesroles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在游戏开始前的右下角添加扩展职业内容制作者信息
 */
@Mixin(Gui.class)
public class LobbyCreditsHudMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderHotbarAndDecorations", at = @At("TAIL"))
    private void mifan$renderExtendedRolesCredits(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player == null)
            return;

        // 检查是否有HUD组件
        GameWorldComponent game = GameWorldComponent.KEY.get(player.level());

        // 只在游戏未开始时显示（大厅阶段）
        if (!game.isRunning()) {
            Font renderer = this.minecraft.font;

            // 扩展职业内容提示信息
            // 从上往下
            Component infoLine4 = Component.translatable("hud.lobby.hint.line4").withStyle(ChatFormatting.WHITE);
            Component infoLine3 = Component.translatable("hud.lobby.hint.line3").withStyle(ChatFormatting.GRAY);
            Component infoLine1 = Component.translatable("hud.lobby.hint.line1",
                    Component.keybind("key." + Noellesroles.MOD_ID + ".role_intro").withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GREEN);
            Component infoLine2 = Component.translatable("hud.lobby.hint.line2",
                    Component.keybind("key." + Noellesroles.MOD_ID + ".guess_role_note").withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.AQUA);

            // context.pose().pushPose();
            // context.pose().scale(0.8f, 0.8f, 1f);
            // 计算右下角位置
            // context.pose().wid
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            // context.pose().translate(0.2 * screenWidth, 0.2 * screenHeight, 0);

            // 文字颜色 - 使用白色
            int color = 0xFFFFFFFF;
            // 在右下角绘制文字
            int rightPadding = 10;
            int bottomPadding = 30;
            int lineHeight = (renderer.lineHeight + 4);
            // 显示提示信息
            int infoWidth1 = renderer.width(infoLine1);
            context.drawString(renderer, infoLine1,
                    screenWidth - infoWidth1 - rightPadding,
                    screenHeight - bottomPadding - lineHeight * 1,
                    color);
            int infoWidth2 = renderer.width(infoLine2);
            context.drawString(renderer, infoLine2,
                    screenWidth - infoWidth2 - rightPadding,
                    screenHeight - bottomPadding,
                    color);

            int infoWidth3 = renderer.width(infoLine3);
            context.drawString(renderer, infoLine3,
                    screenWidth - infoWidth3 - rightPadding,
                    screenHeight - bottomPadding - lineHeight * 3,
                    color);
            int infoWidth4 = renderer.width(infoLine4);
            context.drawString(renderer, infoLine4,
                    screenWidth - infoWidth4 - rightPadding,
                    screenHeight - bottomPadding - lineHeight * 4,
                    color);
            // context.pose().popPose();
        }
    }
}