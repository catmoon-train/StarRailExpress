package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.Innocent.monitor.MonitorPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.util.UUID;

public class MonitorHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MONITOR_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            MonitorPlayerComponent monitorComponent = MonitorPlayerComponent.KEY.get(client.player);
            UUID target = monitorComponent.markedTarget;

            Component text;
            int color;

            if (monitorComponent.cooldown > 0) {
                int seconds = (monitorComponent.cooldown + 19) / 20;
                text = Component.translatable("gui.noellesroles.monitor.cooldown", seconds);
                color = 0xFF5555; // 红色
            } else {
                text = Component.translatable("gui.noellesroles.monitor.ready");
                color = 0x55FF55; // 绿色
            }

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int textWidth = client.font.width(text);

            // 右下角显示，留出一些边距
            int x = screenWidth - 20;
            int y = screenHeight - 30;
            if (target != null) {
                var player = client.player.connection.getPlayerInfo(target);
                var player_text = Component.translatable("gui.noellesroles.monitor.target_not_found")
                        .withStyle(ChatFormatting.YELLOW);
                if (player != null) {
                    Component display_player = Component.literal(player.getProfile().getName());
                    player_text = Component
                            .translatable("gui.noellesroles.monitor.target",
                                    Component.literal("").append(display_player).withStyle(ChatFormatting.GOLD))
                            .withStyle(ChatFormatting.AQUA);
                }
                context.drawString(client.font, player_text, x - client.font.width(player_text), y - 20, 0xffffff);

            }
            context.drawString(client.font, text, x - textWidth, y, color);
        });
    }
}