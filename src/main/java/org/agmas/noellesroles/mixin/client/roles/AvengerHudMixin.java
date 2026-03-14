package org.agmas.noellesroles.mixin.client.roles;

import org.agmas.noellesroles.component.AvengerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 复仇者 HUD Mixin
 * 在屏幕上显示绑定目标和激活状态
 */
@Mixin(RoleNameRenderer.class)
public abstract class AvengerHudMixin {

    @Inject(method = "renderHud", at = @At("HEAD"))
    private static void avengerHudRenderer(Font renderer, LocalPlayer player, GuiGraphics context,
            DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null)
            return;
        if (client.player.isSpectator())
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.player.level());

        // 只有复仇者角色才显示 HUD
        if (!gameWorld.isRole(client.player, ModRoles.AVENGER))
            return;
        if (!SREClient.isPlayerAliveAndInSurvival())
            return;

        AvengerPlayerComponent avengerComponent = AvengerPlayerComponent.KEY.get(client.player);

        context.pose().pushPose();

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int yOffset = screenHeight - 28; // 右下角
        int xOffset = screenWidth - 180; // 距离右边缘
        var refugeeC = RefugeeComponent.KEY.get(player.level());
        boolean isRefugeeAlive = false;
        if (refugeeC.isAnyRevivals) {
            isRefugeeAlive = true;
        }
        if (isRefugeeAlive) {
            Component waitingText = Component.translatable("tip.noellesroles.avenger.refugee_mode")
                    .withStyle(ChatFormatting.GRAY);
            context.drawString(renderer, waitingText, screenWidth - 18 - renderer.width(waitingText), yOffset,
                    CommonColors.GRAY);
        } else if (avengerComponent.activated) {
            // 复仇已激活 - 显示凶手信息
            Component statusText = Component.translatable("tip.noellesroles.avenger.activated",
                    avengerComponent.getKillerName().isEmpty() ? "???" : avengerComponent.getKillerName())
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

            context.drawString(renderer, statusText, xOffset, yOffset, CommonColors.RED);

            // 如果知道凶手，显示凶手头像
            final var playerInfo = client.player.connection.getPlayerInfo(avengerComponent.killerUuid);
            if (avengerComponent.killerUuid != null &&
                    playerInfo != null) {
                PlayerFaceRenderer.draw(context,
                        playerInfo.getSkin().texture(),
                        xOffset, yOffset - 14, 12);

                Component killerName = Component.literal(avengerComponent.getKillerName())
                        .withStyle(ChatFormatting.RED);
                context.drawString(renderer, killerName, xOffset + 16, yOffset - 12, CommonColors.RED);
            }
        } else if (avengerComponent.bound && avengerComponent.targetPlayer != null) {
            // 已绑定目标 - 显示保护目标
            if (client.player.connection.getPlayerInfo(avengerComponent.targetPlayer) != null) {
                // 显示目标头像
                PlayerFaceRenderer.draw(context,
                        client.player.connection.getPlayerInfo(avengerComponent.targetPlayer).getSkin().texture(),
                        xOffset, yOffset, 12);

                Component targetText = Component.translatable("tip.noellesroles.avenger.target",
                        avengerComponent.targetName).withStyle(ChatFormatting.GOLD);
                context.drawString(renderer, targetText, xOffset + 16, yOffset + 2, 0xFFAA00);
            }
        } else {
            // 等待绑定目标
            Component waitingText = Component.translatable("tip.noellesroles.avenger.waiting")
                    .withStyle(ChatFormatting.GRAY);
            context.drawString(renderer, waitingText, xOffset, yOffset, CommonColors.GRAY);
        }

        context.pose().popPose();
    }
}