package org.agmas.noellesroles.mixin.client.roles.admirer;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.component.AdmirerPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class AdmirerHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderAdmirerHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        if (Minecraft.getInstance().player.isSpectator())
            return;
        // 获取慕恋者组件
        AdmirerPlayerComponent admirerComp = AdmirerPlayerComponent.KEY.get(client.player);

        // 检查是否是慕恋者
        if (!admirerComp.isActiveAdmirer())
            return;

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(client.player))
            return;

        // 渲染位置 - 左下角
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = 10;
        int y = screenHeight - 70;

        Font textRenderer = client.font;

        // 角色名称
        Component roleText = Component.translatable("hud.noellesroles.admirer.title")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        context.drawString(textRenderer, roleText, x, y, 0xFFFFFF);
        y += 12;

        if (admirerComp.boundTargetName != null && !admirerComp.boundTargetName.isEmpty()) {
            Component targetText = Component
                    .translatable("hud.noellesroles.admirer.target", admirerComp.boundTargetName)
                    .withStyle(ChatFormatting.GOLD);
            context.drawString(textRenderer, targetText, x, y, 0xFFFFFF);
            y += 12;
        }

        // 能量条
        Component energyText = Component.translatable("hud.noellesroles.admirer.energy",
                admirerComp.energy, AdmirerPlayerComponent.MAX_ENERGY);
        int energyColor = getEnergyColor(admirerComp.getEnergyPercent());
        context.drawString(textRenderer, energyText, x, y, energyColor);
        y += 12;

        // 能量进度条
        int barWidth = 80;
        int barHeight = 6;
        int filledWidth = (int) (barWidth * admirerComp.getEnergyPercent());

        // 背景
        context.fill(x, y, x + barWidth, y + barHeight, 0x88000000);
        // 填充
        context.fill(x, y, x + filledWidth, y + barHeight, energyColor | 0xFF000000);
        y += 10;

        // 窥视状态
        if (admirerComp.isGazing) {
            Component gazingText;
            if (admirerComp.gazingTargetCount > 0) {
                gazingText = Component.translatable("hud.noellesroles.admirer.gazing_target")
                        .withStyle(ChatFormatting.GREEN);
            } else {
                gazingText = Component.translatable("hud.noellesroles.admirer.gazing_no_target")
                        .withStyle(ChatFormatting.YELLOW);
            }
            context.drawString(textRenderer, gazingText, x, y, 0xFFFFFF);
            y += 12;
        } else {
            // 提示按G开始窥视
            Component hintText = Component.translatable("hud.noellesroles.admirer.hint")
                    .withStyle(ChatFormatting.GRAY);
            context.drawString(textRenderer, hintText, x, y, 0xFFFFFF);
        }
    }

    /**
     * 根据能量百分比获取颜色
     */
    private int getEnergyColor(float percent) {
        if (percent >= 0.9f) {
            return 0xFF0000; // 红色 - 即将转化
        } else if (percent >= 0.6f) {
            return 0xFF8800; // 橙色
        } else if (percent >= 0.3f) {
            return 0xFFFF00; // 黄色
        } else {
            return 0xAA00FF; // 紫色 - 正常
        }
    }
}