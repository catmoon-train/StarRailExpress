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
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.NostalgistRoleData;

public class NostalgistHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.NOSTALGIST_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            if (client.player == null)
                return;

            final var compOpt = RoleData.getOptional(NostalgistRoleData.class, client.player);
            if (compOpt.isEmpty())
                return;
            final var comp = compOpt.get();

            MutableComponent content;
            if (comp.inBackWorld && !comp.converted) {
                content = Component.translatable("hud.noellesroles.nostalgist.back_world",
                        comp.aliveKillerCount);
            } else {
                content = Component.translatable("hud.noellesroles.nostalgist.manifest");
            }
            context.drawString(client.font, content,
                    context.guiWidth() - client.font.width(content) - 12,
                    context.guiHeight() - 20, ModRoles.NOSTALGIST.color());
        });
    }
}
