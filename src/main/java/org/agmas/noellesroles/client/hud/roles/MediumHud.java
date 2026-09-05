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
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.MediumRoleData;

public final class MediumHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MEDIUM_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator() || client.player == null) {
                return;
            }
            MediumRoleData data = RoleData.getNullable(MediumRoleData.class, client.player);
            if (data == null) {
                return;
            }

            int drawY = context.guiHeight();
            Component line;
            int color = ModRoles.MEDIUM.color();
            long remaining = data.sessionEndTick - SREClient.getTicksFromGameStart();
            if (remaining > 0) {
                int seconds = (int) ((remaining + 19) / 20);
                line = Component.translatable("hud.noellesroles.medium.remaining", seconds);
                if (data.lastAnswerId >= 0) {
                    MediumRoleData.SeanceAnswer answer = MediumRoleData.SeanceAnswer.fromId(data.lastAnswerId);
                    if (answer != null) {
                        Component last = Component.translatable("hud.noellesroles.medium.last_answer",
                                answer.translatable());
                        drawY -= client.font.wordWrapHeight(last, 999999);
                        context.drawString(client.font, last,
                                context.guiWidth() - client.font.width(last), drawY, 0xFFD4AF37);
                    }
                }
            } else {
                line = Component.translatable("hud.noellesroles.medium.cost", MediumRoleData.SEANCE_COST);
            }
            drawY -= client.font.wordWrapHeight(line, 999999);
            context.drawString(client.font, line,
                    context.guiWidth() - client.font.width(line), drawY, color);
        });
    }
}
