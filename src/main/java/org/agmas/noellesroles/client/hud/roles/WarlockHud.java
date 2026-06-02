package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class WarlockHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.WARLOCK_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) return;
            var comp = WarlockPlayerComponent.KEY.get(client.player);
            Font font = client.font;
            int sw = client.getWindow().getGuiScaledWidth();
            int sy = client.getWindow().getGuiScaledHeight();

            // 标记技能 — 右下角第一行
            Component markText;
            if (comp.markCooldown > 0) {
                int sec = (comp.markCooldown + 19) / 20;
                markText = Component.translatable("hud.noellesroles.warlock.mark_cd", sec).withStyle(ChatFormatting.GOLD);
            } else if (comp.markedTarget != null) {
                markText = Component.translatable("hud.noellesroles.warlock.marked").withStyle(ChatFormatting.YELLOW);
            } else {
                markText = Component.translatable("hud.noellesroles.warlock.mark_ready").withStyle(ChatFormatting.GREEN);
            }
            context.drawString(font, markText, sw - font.width(markText) - 8, sy - 36, 0xFFFFFF);

            // 咒杀技能 — 右下角第二行
            Component killText;
            if (comp.killCooldown > 0) {
                int sec = (comp.killCooldown + 19) / 20;
                killText = Component.translatable("hud.noellesroles.warlock.kill_cd", sec).withStyle(ChatFormatting.RED);
            } else if (comp.markedTarget != null) {
                killText = Component.translatable("hud.noellesroles.warlock.kill_ready").withStyle(ChatFormatting.DARK_PURPLE);
            } else {
                killText = Component.translatable("hud.noellesroles.warlock.kill_no_target").withStyle(ChatFormatting.GRAY);
            }
            context.drawString(font, killText, sw - font.width(killText) - 8, sy - 24, 0xFFFFFF);
        });
    }
}
