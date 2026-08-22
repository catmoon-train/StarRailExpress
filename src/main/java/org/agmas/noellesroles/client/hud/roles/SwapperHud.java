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
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;

public final class SwapperHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SWAPPER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator()) return;

            var swapper = ModComponents.SWAPPER.get(client.player);
            var ability = SREAbilityPlayerComponent.KEY.get(client.player);
            Font font = client.font;
            int sw = context.guiWidth();
            int sy = context.guiHeight();

            Component frontText = cooldownLine(
                    swapper.frontSwapCooldown,
                    "hud.noellesroles.swapper.front_cooldown",
                    "hud.noellesroles.swapper.front_ready");
            context.drawString(font, frontText, sw - font.width(frontText) - 8, sy - 36, 0xFFFFFF);

            Component inventoryText = cooldownLine(
                    ability.cooldown,
                    "hud.noellesroles.swapper.inventory_cooldown",
                    "hud.noellesroles.swapper.inventory_ready");
            context.drawString(font, inventoryText, sw - font.width(inventoryText) - 8, sy - 24, 0xFFFFFF);
        });
    }

    private static Component cooldownLine(int ticks, String cooldownKey, String readyKey) {
        if (ticks > 0) {
            int seconds = (ticks + 19) / 20;
            return Component.translatable(cooldownKey, seconds).withStyle(ChatFormatting.RED);
        }
        return Component.translatable(readyKey).withStyle(ChatFormatting.GREEN);
    }
}
