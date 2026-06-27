package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.detective.DetectivePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 探员 HUD Mixin
 * 
 * 显示探员的技能状态：
 * - 审查技能冷却时间
 * - 技能就绪提示
 */
public class DetectiveHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.AGENT_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取探员组件
            DetectivePlayerComponent detectiveComponent = DetectivePlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 120; // 距离右边缘
            int y = screenHeight - 30; // 距离底部

            Font textRenderer = client.font;

            if (detectiveComponent.cooldown > 0) {
                // 显示技能冷却
                float cdSeconds = detectiveComponent.getCooldownSeconds();
                Component cdText = Component.translatable("hud.noellesroles.detective.cooldown",
                        String.format("%.1f", cdSeconds));

                // 红色文字表示冷却中
                context.drawString(textRenderer, cdText, x, y, CommonColors.RED);

            } else {
                // 技能可用 - 显示金币消耗提示
                Component readyText = Component.translatable("hud.noellesroles.detective.ready_cost");
                context.drawString(textRenderer, readyText, x, y, CommonColors.GREEN);
            }
        });
    }
}