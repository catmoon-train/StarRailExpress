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
import org.agmas.noellesroles.role_data.neutral.PelicanRoleData;
import org.agmas.noellesroles.role.ModRoles;

public class PelicanHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.PELICAN_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator())
                return;

            PelicanRoleData comp = RoleData.getNullable(PelicanRoleData.class, client.player);
            if (comp == null) return;

            int guiWidth = context.guiWidth();
            int guiHeight = context.guiHeight();

            // 右下角：吞噬进度 (已吞噬数/目标数)
            Component progressLine = Component.translatable("hud.noellesroles.pelican.progress",
                    comp.eatenCount, comp.requiredEaten);
            int progressX = guiWidth - client.font.width(progressLine) - 10;
            int progressY = guiHeight - 30;
            context.drawString(client.font, progressLine, progressX, progressY, ModRoles.PELICAN.color(), true);

            // 冷却信息（在进度上方显示）
            int cooldownTicks = comp.getRemainingCooldownTicks();
            if (cooldownTicks > 0) {
                Component cooldownLine = Component.translatable("tip.noellesroles.cooldown",
                        cooldownTicks / 20);
                int cooldownX = guiWidth - client.font.width(cooldownLine) - 10;
                int cooldownY = progressY - client.font.lineHeight - 2;
                context.drawString(client.font, cooldownLine, cooldownX, cooldownY, 0xFF5555, true);
            }
        });
    }
}
