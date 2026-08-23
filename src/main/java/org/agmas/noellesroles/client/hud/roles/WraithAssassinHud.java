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
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.WraithAssassinRoleData;

public class WraithAssassinHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.WRAITH_ASSASSIN_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator() || !SREClient.isPlayerAliveAndInSurvival()) {
                return;
            }

            var compOpt = RoleData.getOptional(WraithAssassinRoleData.class, client.player);
            if (compOpt.isEmpty())
                return;
            WraithAssassinRoleData comp = compOpt.get();
            Font font = client.font;
            int x = 10;
            int y = client.getWindow().getGuiScaledHeight() - 88;

            Component title = Component.translatable("announcement.star.role.wraith_assassin")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
            context.drawString(font, title, x, y, 0xFFFFFF);
            y += 12;

            int energyColor = getEnergyColor(comp.getEnergyPercent());
            Component energy = Component.translatable("hud.noellesroles.wraith_assassin.energy",
                    comp.energy, WraithAssassinRoleData.MAX_ENERGY);
            context.drawString(font, energy, x, y, energyColor);
            y += 12;

            int barWidth = 96;
            int barHeight = 6;
            int filled = Mth.floor(barWidth * comp.getEnergyPercent());
            context.fill(x, y, x + barWidth, y + barHeight, 0x88000000);
            context.fill(x, y, x + filled, y + barHeight, energyColor | 0xFF000000);
            y += 10;

            Component costs = Component.translatable("hud.noellesroles.wraith_assassin.costs",
                    WraithAssassinRoleData.ASSAULT_COST,
                    WraithAssassinRoleData.WAIL_COST,
                    WraithAssassinRoleData.MANIFEST_COST).withStyle(ChatFormatting.GRAY);
            context.drawString(font, costs, x, y, 0xFFFFFF);
            y += 12;

            if (comp.drainCooldownTicks > 0) {
                Component cooldown = Component.translatable("hud.noellesroles.wraith_assassin.drain_cooldown",
                        Mth.ceil(comp.drainCooldownTicks / 20.0f)).withStyle(ChatFormatting.RED);
                context.drawString(font, cooldown, x, y, 0xFFFFFF);
                y += 12;
            }

            if (comp.isManifested()) {
                Component manifesting = Component.translatable("hud.noellesroles.wraith_assassin.manifesting",
                        Mth.ceil(comp.manifestTicks / 20.0f)).withStyle(ChatFormatting.GOLD);
                context.drawString(font, manifesting, x, y, 0xFFFFFF);
            }
        });
    }

    private static int getEnergyColor(float percent) {
        if (percent >= 0.8f) {
            return 0xD86BFF;
        }
        if (percent >= 0.45f) {
            return 0x8A7DFF;
        }
        return 0x74D7FF;
    }
}
