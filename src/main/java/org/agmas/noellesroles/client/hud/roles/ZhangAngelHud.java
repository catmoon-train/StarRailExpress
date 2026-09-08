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

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.ZhangAngelRoleData;

public final class ZhangAngelHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.ZHANG_ANGEL_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            var dataOpt = RoleData.getOptional(ZhangAngelRoleData.class, client.player);
            if (dataOpt.isEmpty()) {
                return;
            }
            ZhangAngelRoleData data = dataOpt.get();
            var font = client.font;
            int x = guiGraphics.guiWidth() - 10;
            int y = guiGraphics.guiHeight() - 30;

            Component swallowText = Component.translatable("hud.noellesroles.zhang_angel.swallows",
                    data.getSwallowCount(), ZhangAngelRoleData.SWALLOW_NEEDED)
                    .withStyle(data.getSwallowCount() >= ZhangAngelRoleData.SWALLOW_NEEDED
                            ? ChatFormatting.GOLD : ChatFormatting.AQUA);
            guiGraphics.drawString(font, swallowText, x - font.width(swallowText), y, 0xFFFFFF, true);

            if (data.isDarknessStealthActive()) {
                Component stealthText = Component.translatable("hud.noellesroles.zhang_angel.stealth")
                        .withStyle(ChatFormatting.GRAY);
                guiGraphics.drawString(font, stealthText, x - font.width(stealthText), y - font.lineHeight - 2,
                        0xAAAAAA, true);
            }
        });
    }
}
