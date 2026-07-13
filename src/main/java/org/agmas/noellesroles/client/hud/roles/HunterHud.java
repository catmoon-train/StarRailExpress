package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.hunter.HunterPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class HunterHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.HUNTER_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            HunterPlayerComponent component = ModComponents.HUNTER.get(client.player);

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 130;
            int y = screenHeight - 60;

            // 显示杀敌进度：杀敌数/3
            int kills = component.killCount;
            int progress = kills % 3;

            MutableComponent killText = Component.translatable("hud.noellesroles.hunter.kill_progress", progress, 3);
            guiGraphics.drawString(client.font, killText, x, y, ModRoles.HUNTER.color());

            // 显示总杀敌数
            MutableComponent totalText = Component.translatable("hud.noellesroles.hunter.total_kills", kills);
            guiGraphics.drawString(client.font, totalText, x, y + 12, 0xFFFFFF);

            // 显示弓冷却状态
            if (client.player.getCooldowns().isOnCooldown(Items.BOW)) {
                float remaining = client.player.getCooldowns().getCooldownPercent(Items.BOW, 0f);
                int seconds = Math.round(remaining * 5.0f); // 5秒总冷却
                MutableComponent cooldownText = Component.translatable("hud.noellesroles.hunter.bow_cooldown", seconds);
                guiGraphics.drawString(client.font, cooldownText, x, y + 24, 0xFF5555);
            }
        });
    }
}
