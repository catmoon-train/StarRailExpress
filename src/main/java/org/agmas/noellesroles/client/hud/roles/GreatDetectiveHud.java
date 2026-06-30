package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.great_detective.GreatDetectivePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 大侦探 HUD Mixin
 * 
 * 显示技能状态：
 * - 冷却时间
 */
public class GreatDetectiveHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GREAT_DETECTIVE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取探员组件
            GreatDetectivePlayerComponent detectiveComponent = GreatDetectivePlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10; // 距离右边缘
            int y = screenHeight - 20; // 距离底部

            Font font = client.font;

            if (detectiveComponent.isInCooldown()) {
                long time = detectiveComponent.cooldown - client.level.getGameTime();
                // 显示技能冷却
                float cdSeconds = time / 20;
                Component cdText = Component.translatable("hud.noellesroles.great_detective.cooldown",
                        String.format("%.1f", cdSeconds));

                // 红色文字表示冷却中
                context.drawString(font, cdText, x - font.width(cdText), y, CommonColors.RED);

            } else {
                // 技能可用 - 显示金币消耗提示
                Component readyText = Component.translatable("hud.noellesroles.great_detective.ready_cost");
                context.drawString(font, readyText, x - font.width(readyText), y, CommonColors.GREEN);
            }
        });
    }
}