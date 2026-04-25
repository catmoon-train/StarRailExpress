package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.*;

public abstract class CuckooHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.CUCKOO_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            Font font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight;
            int xOffset = screenWidth - 10;

            CuckooPlayerComponent comp = CuckooPlayerComponent.KEY.get(client.player);
            if (comp == null) return;

            // 蛋进度：当前蛋数/目标蛋数
            Component eggText = Component.translatable("hud.noellesroles.cuckoo.eggs",
                    comp.survivingEggs, comp.requiredEggs).withStyle(ChatFormatting.GOLD);
            guiGraphics.drawString(font, eggText, xOffset - font.width(eggText), yOffset - font.lineHeight - 4,
                    Color.WHITE.getRGB());

            // 冷却时间
            if (comp.placeCooldown > 0) {
                Component cdText = Component.translatable("hud.noellesroles.cuckoo.cooldown",
                        comp.placeCooldown / 20).withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, cdText, xOffset - font.width(cdText), yOffset - font.lineHeight * 2 - 8,
                        Color.WHITE.getRGB());
            } else {
                Component readyText = Component.translatable("hud.noellesroles.cuckoo.ready").withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(font, readyText, xOffset - font.width(readyText), yOffset - font.lineHeight * 2 - 8,
                        Color.WHITE.getRGB());
            }
        });
    }
}
