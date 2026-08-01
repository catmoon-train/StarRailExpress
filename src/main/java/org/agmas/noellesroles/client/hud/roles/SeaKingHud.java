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

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;

public class SeaKingHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SEA_KING_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.gameComponent == null) {
                return;
            }
            if (!SREClient.isPlayerAliveAndInSurvival()) {
                return;
            }

            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(client.player);
            if (ability == null) {
                return;
            }

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 120;
            int y = screenHeight - 40;

            if (ability.cooldown > 0) {
                int seconds = (ability.cooldown + 19) / 20;
                Component cooldownText = Component.translatable("hud.noellesroles.sea_king.skill_cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
                guiGraphics.drawString(client.font, cooldownText, x, y, 0xFFFFFF);
            } else {
                Component readyText = Component.translatable("hud.noellesroles.sea_king.skill_ready")
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(client.font, readyText, x, y, 0xFFFFFF);
            }
        });
    }
}
