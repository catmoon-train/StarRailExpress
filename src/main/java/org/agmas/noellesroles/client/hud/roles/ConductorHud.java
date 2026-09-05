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
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.ConductorRoleData;

public final class ConductorHud {
    private ConductorHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.CONDUCTOR_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            ConductorRoleData data = RoleData.getNullable(ConductorRoleData.class, client.player);
            if (data == null) {
                return;
            }
            int x = context.guiWidth() - 10;
            int y = context.guiHeight() - 20;
            if (data.awaitingPick) {
                Component text = Component.translatable("hud.noellesroles.conductor.pick_door");
                context.drawString(client.font, text, x - client.font.width(text), y, 0xFFE6C37A);
                return;
            }
            if (data.windupEndGameTime <= 0) {
                return;
            }
            long remaining = data.windupEndGameTime - client.player.level().getGameTime();
            if (remaining <= 0) {
                return;
            }
            Component text = Component.translatable("hud.noellesroles.conductor.windup",
                    String.format("%.1f", remaining / 20.0));
            context.drawString(client.font, text, x - client.font.width(text), y, 0xFFE6C37A);
        });
    }
}
