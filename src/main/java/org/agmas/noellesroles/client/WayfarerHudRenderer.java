package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.WayfarerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.*;

public class WayfarerHudRenderer {
    public static void registerRendererEvent() {
        RoleHudRenderCallback.EVENT.register(ModRoles.WAYFARER.identifier(),(guiGraphics, deltaTracker) -> {
            // 渲染红尘客的提示
            var client = Minecraft.getInstance();
            if (client == null)
                return;
            if (client.player == null)
                return;
            if (SREClient.gameComponent == null
                    || !SREClient.gameComponent.isRole(client.player, ModRoles.WAYFARER)) {
                return;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(client.player))
                return;
            // int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            final int lineHeight = (font.lineHeight + 8);
            int yOffset = screenHeight - lineHeight * 4; // 左下角
            int xOffset = 30; // 距离左边缘
            var wayC = WayfarerPlayerComponent.KEY.get(client.player);
            Component phaseText = Component
                    .translatable("hud.noellesroles.wayfarer.phase." + wayC.phase + ".title")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            Component descText = Component
                    .translatable("hud.noellesroles.wayfarer.phase." + wayC.phase + ".desc")
                    .withStyle(ChatFormatting.WHITE);
            Component tipText = Component.translatable("hud.noellesroles.wayfarer.phase.0.tip")
                    .withStyle(ChatFormatting.YELLOW);

            if (wayC.phase == 1) {
                Component killerText = Component
                        .translatable("hud.noellesroles.wayfarer.phase.1.unknown_killer")
                        .withStyle(ChatFormatting.GRAY);
                if (wayC.killer != null) {
                    Player killer = client.level.getPlayerByUUID(wayC.killer);
                    if (killer != null) {
                        killerText = Component.literal(killer.getDisplayName().getString())
                                .withStyle(ChatFormatting.GOLD);
                    }
                }
                tipText = Component.translatable("hud.noellesroles.wayfarer.phase.1.tip", killerText)
                        .withStyle(ChatFormatting.YELLOW);
            } else if (wayC.phase == 2) {
                Component killerText = Component
                        .translatable("death_reason." + wayC.deathReason.toLanguageKey())
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
                tipText = Component.translatable("hud.noellesroles.wayfarer.phase.2.tip", killerText)
                        .withStyle(ChatFormatting.AQUA);
            } else if (wayC.phase == 3) {
                tipText = Component.translatable("hud.noellesroles.wayfarer.phase.3.tip")
                        .withStyle(ChatFormatting.GREEN);
            } else if (wayC.phase == 4) {
                tipText = Component.translatable("hud.noellesroles.wayfarer.phase.4.tip")
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
            }
            guiGraphics.drawString(font, phaseText, xOffset, yOffset,
                    Color.WHITE.getRGB());
            guiGraphics.drawString(font, descText, xOffset, yOffset + lineHeight,
                    Color.WHITE.getRGB());

            guiGraphics.drawString(font, tipText, xOffset, yOffset + lineHeight * 2,
                    Color.WHITE.getRGB());
            return;
        });
    }
}
