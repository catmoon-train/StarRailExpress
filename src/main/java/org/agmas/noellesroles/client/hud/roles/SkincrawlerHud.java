package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.skincrawler.SkincrawlerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class SkincrawlerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SKINCRAWLER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) return;
            var comp = SkincrawlerPlayerComponent.KEY.get(client.player);
            Font font = client.font;
            int sw = client.getWindow().getGuiScaledWidth();
            int sy = client.getWindow().getGuiScaledHeight();

            Component text;
            if (comp.stealCooldown > 0) {
                int sec = (comp.stealCooldown + 19) / 20;
                text = Component.translatable("hud.noellesroles.skincrawler.cooldown", sec).withStyle(ChatFormatting.GRAY);
            } else {
                text = Component.translatable("hud.noellesroles.skincrawler.ready").withStyle(ChatFormatting.GREEN);
            }
            context.drawString(font, text, sw - font.width(text) - 8, sy - 24, 0xFFFFFF);

            // 显示抵挡剩余次数
            Component blockText = Component.translatable("hud.noellesroles.skincrawler.block_charges", comp.blockCharges)
                    .withStyle(comp.blockCharges > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
            context.drawString(font, blockText, sw - font.width(blockText) - 8, sy - 36, 0xFFFFFF);
        });
    }
}
