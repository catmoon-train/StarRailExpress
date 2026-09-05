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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.GreatDetectiveRoleData;

/**
 * 大侦探 HUD：勘察冷却 / 施法倒计时。
 */
public class GreatDetectiveHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GREAT_DETECTIVE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            var detectiveOpt = RoleData.getOptional(GreatDetectiveRoleData.class, client.player);
            if (detectiveOpt.isEmpty())
                return;
            GreatDetectiveRoleData detectiveComponent = detectiveOpt.get();

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10;
            int y = screenHeight - 20;

            Font font = client.font;

            if (detectiveComponent.isChanneling()) {
                float left = detectiveComponent.getChannelLeftTime() / 20f;
                Component text = Component.translatable("hud.noellesroles.great_detective.channeling",
                        String.format("%.1f", left));
                context.drawString(font, text, x - font.width(text), y, 0xFFEEDDAA);
            } else if (detectiveComponent.isInCooldown()) {
                float cdSeconds = detectiveComponent.getCooldownLeftTime() / 20f;
                Component cdText = Component.translatable("hud.noellesroles.great_detective.cooldown",
                        String.format("%.1f", cdSeconds));
                context.drawString(font, cdText, x - font.width(cdText), y, CommonColors.RED);
            } else {
                Component readyText = Component.translatable("hud.noellesroles.great_detective.ready");
                context.drawString(font, readyText, x - font.width(readyText), y, CommonColors.GREEN);
            }
        });
    }
}
