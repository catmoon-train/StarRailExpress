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
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.innocence.AdventurerRoleData;
import org.agmas.noellesroles.role.ModRoles;

public final class AdventurerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.ADVENTURER_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            var adv = RoleData.getOptional(AdventurerRoleData.class, player);
            if (adv.isEmpty()) return;

            int x = context.guiWidth() - 180;
            int y = context.guiHeight() - 40;
            var font = Minecraft.getInstance().font;

            context.drawString(font,
                    Component.translatable("hud.noellesroles.adventurer.immunities"),
                    x, y, 0x55FF55);

            if (adv.get().waypointCooldown > 0) {
                int sec = (adv.get().waypointCooldown + 19) / 20;
                context.drawString(font,
                        Component.translatable("hud.noellesroles.adventurer.waypoint_cd", sec),
                        x, y + 11, 0xAAAAAA);
            } else {
                context.drawString(font,
                        Component.translatable("hud.noellesroles.adventurer.waypoint_ready"),
                        x, y + 11, 0xFFD700);
            }
        });
    }
}
