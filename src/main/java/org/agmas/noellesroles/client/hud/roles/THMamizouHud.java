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

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.touhou.THMiscRoles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.util.SREClientUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class THMamizouHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(THMiscRoles.MAMIZOU_ID, (context, deltaTracker) -> {
            var client = Minecraft.getInstance();
            var cca = SREAbilityPlayerComponent.KEY.get(client.player);
            final var font = client.font;

            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = 10;
            int y = screenHeight - 20;
            if (cca.targetUUID != null) {
                String name = SREClientUtils.getPlayerNameByUid(cca.targetUUID);
                if (name == null)
                    return;
                final var killsText = Component.translatable("hud.noellesroles.mamizou_select", name)
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(font, killsText, x, y, 0xffffffff);
            }

        });
    }
}
