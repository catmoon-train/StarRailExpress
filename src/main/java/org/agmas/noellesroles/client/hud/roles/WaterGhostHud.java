package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.water_ghost.WaterGhostPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class WaterGhostHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.WATER_GHOST_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) {
                return;
            }

            // 检查玩家是否存活
            if (!SREClient.isPlayerAliveAndInSurvival()) {
                return;
            }

            WaterGhostPlayerComponent component = WaterGhostPlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 120;
            int y = screenHeight - 40;

            // 显示干涸死亡倒计时
            int dryDeathRemaining = component.getDryDeathRemaining();
            if (dryDeathRemaining > 0) {
                ChatFormatting color = ChatFormatting.WHITE;
                if (dryDeathRemaining <= 30) {
                    color = ChatFormatting.RED;
                } else if (dryDeathRemaining <= 60) {
                    color = ChatFormatting.YELLOW;
                }

                Component dryDeathText = Component
                        .translatable("hud.noellesroles.water_ghost.dry_death", dryDeathRemaining)
                        .withStyle(color);
                guiGraphics.drawString(client.font, dryDeathText, x, y, 0xFFFFFF);
            }

            // 显示技能冷却时间
            int cooldownRemaining = component.getSkillCooldownRemaining();
            if (cooldownRemaining > 0) {
                Component cooldownText = Component
                        .translatable("hud.noellesroles.water_ghost.skill_cooldown", cooldownRemaining)
                        .withStyle(ChatFormatting.RED);
                guiGraphics.drawString(client.font, cooldownText, x, y + 12, 0xFFFFFF);
            } else {
                Component readyText = Component.translatable("hud.noellesroles.water_ghost.skill_ready")
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(client.font, readyText, x, y + 12, 0xFFFFFF);
            }
        });
    }
}
