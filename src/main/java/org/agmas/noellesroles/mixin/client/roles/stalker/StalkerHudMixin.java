package org.agmas.noellesroles.mixin.client.roles.stalker;

import org.agmas.noellesroles.component.StalkerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 跟踪者 HUD Mixin
 * 
 * 显示跟踪者的状态：
 * - 当前阶段
 * - 能量值
 * - 击杀数（二阶段）
 * - 免疫状态（二阶段）
 * - 倒计时（三阶段）
 * - 窥视目标数
 * - 蓄力进度（三阶段）
 */
@Mixin(Gui.class)
public class StalkerHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderStalkerHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;
        if (client.player.isSpectator())
            return;
        if (SREClient.gameComponent == null)
            return;
        var role = SREClient.gameComponent.getRole(client.player);
        if (role == null)
            return;
        if (!role.identifier().getPath().equals(ModRoles.STALKER.identifier().getPath())) {
            return;
        }

        // 获取跟踪者组件
        StalkerPlayerComponent stalkerComp = StalkerPlayerComponent.KEY.get(client.player);
        // 检查是否是跟踪者
        if (!stalkerComp.isActiveStalker())
            return;

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(client.player))
            return;

        // 渲染位置 - 左下角
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = 10;
        int y = screenHeight - 80;

        Font textRenderer = client.font;

        // 阶段显示
        Component phaseText = switch (stalkerComp.phase) {
            case 1 -> Component.translatable("hud.noellesroles.stalker.phase1").withStyle(ChatFormatting.DARK_PURPLE);
            case 2 -> Component.translatable("hud.noellesroles.stalker.phase2").withStyle(ChatFormatting.RED);
            case 3 -> Component.translatable("hud.noellesroles.stalker.phase3").withStyle(ChatFormatting.DARK_RED);
            default -> Component.empty();
        };
        context.drawString(textRenderer, phaseText, x, y, 0xFFFFFF);
        y += 12;

        // 能量条
        int maxEnergy = stalkerComp.phase == 1 ? stalkerComp.getPhase1EnergyRequired()
                : stalkerComp.getPhase2EnergyRequired();
        Component energyText = Component.translatable("hud.noellesroles.stalker.energy", stalkerComp.energy, maxEnergy);
        context.drawString(textRenderer, energyText, x, y, 0xAAAAAA);
        y += 12;

        // 一阶段：显示盾牌状态
        if (stalkerComp.phase == 1) {
            Component immunityText = stalkerComp.immunityUsed
                    ? Component.translatable("hud.noellesroles.stalker.immunity_used").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("hud.noellesroles.stalker.immunity_available")
                            .withStyle(ChatFormatting.GREEN);
            context.drawString(textRenderer, immunityText, x, y, 0xFFFFFF);
            y += 12;
        }

        // 二阶段及以上：击杀数
        if (stalkerComp.phase >= 2) {
            Component killsText = Component.translatable("hud.noellesroles.stalker.kills",
                    stalkerComp.phase2Kills, stalkerComp.getPhase2KillsRequired());
            context.drawString(textRenderer, killsText, x, y, 0xFF6666);
            y += 12;
        }

        // 二阶段及以上：攻击冷却
        if (stalkerComp.phase >= 2 && stalkerComp.attackCooldown > 0) {
            String cooldownTime = String.format("%.1f", stalkerComp.getAttackCooldownSeconds());
            Component cooldownText = Component.translatable("hud.noellesroles.stalker.attack_cooldown", cooldownTime)
                    .withStyle(ChatFormatting.RED);
            context.drawString(textRenderer, cooldownText, x, y, 0xFF0000);
            y += 12;
        }

        // 三阶段：倒计时
        if (stalkerComp.phase == 3) {
            int seconds = stalkerComp.phase3Timer / 20;
            int minutes = seconds / 60;
            seconds %= 60;
            Component timerText = Component.translatable("hud.noellesroles.stalker.timer",
                    String.format("%d:%02d", minutes, seconds));
            int color = stalkerComp.phase3Timer < 600 ? 0xFF0000 : 0xFFAA00; // 30秒以下变红
            context.drawString(textRenderer, timerText, x, y, color);
            y += 12;
        }

        // 窥视状态
        if (stalkerComp.isGazing) {
            Component gazingText = Component
                    .translatable("hud.noellesroles.stalker.gazing", stalkerComp.gazingTargetCount)
                    .withStyle(ChatFormatting.YELLOW);
            context.drawString(textRenderer, gazingText, x, y, 0xFFFFFF);
            y += 12;
        }

        // 蓄力进度（三阶段）
        if (stalkerComp.isCharging) {
            float chargeSeconds = stalkerComp.getChargeSeconds();
            float maxSeconds = StalkerPlayerComponent.MAX_CHARGE_TIME / 20.0f;
            Component chargeText = Component.translatable("hud.noellesroles.stalker.charging",
                    String.format("%.1f", chargeSeconds), String.format("%.1f", maxSeconds));
            int chargeColor = chargeSeconds >= 1.0f ? 0x00FF00 : 0xFFFF00;
            context.drawString(textRenderer, chargeText, x, y, chargeColor);
        }

        // 突进状态
        if (stalkerComp.isDashing) {
            Component dashText = Component.translatable("hud.noellesroles.stalker.dashing")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            context.drawString(textRenderer, dashText, x, y, 0xFFFFFF);
        }
    }
}