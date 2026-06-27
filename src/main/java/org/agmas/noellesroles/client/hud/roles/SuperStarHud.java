package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.super_star.SuperStarPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 明星 HUD 显示
 *
 * 显示：
 * - 发光中状态（主动技能触发时）
 * - 主动技能冷却时间或就绪提示
 */
public class SuperStarHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SUPERSTAR_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            // 获取明星组件
            SuperStarPlayerComponent starComp = ModComponents.STAR.get(client.player);
            if (!starComp.isActive)
                return;

            Font textRenderer = client.font;
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();

            // 基础Y位置（屏幕中下方）
            int baseY = screenHeight - 80;

            // ==================== 显示角色名称 ====================
            Component titleText = Component.translatable("hud.noellesroles.star.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            int titleWidth = textRenderer.width(titleText);
            context.drawString(textRenderer, titleText,
                    (screenWidth - titleWidth) / 2, baseY - 20, 0xFFD700);

            // ==================== 显示发光状态（仅在发光时显示） ====================
            if (starComp.isGlowing) {
                Component glowText = Component.translatable("hud.noellesroles.star.glowing")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
                int glowWidth = textRenderer.width(glowText);
                context.drawString(textRenderer, glowText,
                        (screenWidth - glowWidth) / 2, baseY, 0xFFFF00);
            }

            // ==================== 显示技能状态 ====================
            int abilityY = starComp.isGlowing ? baseY + 12 : baseY;
            Component abilityText;
            if (starComp.abilityCooldown > 0) {
                // 冷却中
                abilityText = Component.translatable("hud.noellesroles.star.cooldown",
                        String.format("%.0f", starComp.getCooldownSeconds()))
                        .withStyle(ChatFormatting.RED);
            } else {
                // 就绪
                abilityText = Component.translatable("hud.noellesroles.star.ready")
                        .withStyle(ChatFormatting.GREEN);
            }
            int abilityWidth = textRenderer.width(abilityText);
            context.drawString(textRenderer, abilityText,
                    (screenWidth - abilityWidth) / 2, abilityY,
                    starComp.abilityCooldown > 0 ? 0xFF5555 : 0x55FF55);
        });
    }
}