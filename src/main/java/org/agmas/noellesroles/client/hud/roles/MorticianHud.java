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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.innocence.MorticianRoleData;
import org.agmas.noellesroles.role.ModRoles;

public class MorticianHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MORTICIAN_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) {
                return;
            }
            if (!SREClient.isPlayerAliveAndInSurvival()) {
                return;
            }

            var component = RoleData.getOptional(MorticianRoleData.class, client.player);
            if (component.isEmpty()) {
                return;
            }

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            
            // 在金币下方显示冷却时间
            int x = screenWidth - 120;
            int y = screenHeight - 25;

            if (component.get().isCooldownReady()) {
                Component readyText = Component.translatable("hud.noellesroles.mortician.ready")
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(client.font, readyText, x, y, 0xFFFFFF);
            } else {
                Component cooldownText = Component.translatable("hud.noellesroles.mortician.cooldown", 
                        component.get().getRemainingCooldown())
                        .withStyle(ChatFormatting.RED);
                guiGraphics.drawString(client.font, cooldownText, x, y, 0xAAAAAA);
            }
        });
    }
}
