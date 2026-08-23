/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.neutral.CuckooRoleData;
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

            var cuckooOpt = RoleData.getOptional(CuckooRoleData.class, client.player);
            if (cuckooOpt.isEmpty()) return;
            CuckooRoleData comp = cuckooOpt.get();

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
