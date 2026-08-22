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

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;

public final class CakeMakerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.CAKE_MAKER_ID, (context, tickCounter) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            var comp = ModComponents.CAKE_MAKER.get(client.player);
            int x = context.guiWidth() - 10;
            int y = context.guiHeight() - 10 - client.font.lineHeight;
            var font = client.font;
            if (comp.cooldown > 0) {
                int seconds = (comp.cooldown + 19) / 20;
                var text = Component.translatable("hud.cake_maker.cooldown", seconds)
                        .withStyle(ChatFormatting.RED);
                context.drawString(font, text, x - font.width(text), y, 0xFF5555);
            } else {
                var text = Component.translatable("hud.cake_maker.ready")
                        .withStyle(ChatFormatting.GREEN);
                context.drawString(font, text, x - font.width(text), y, 0x55FF55);
            }
        });
    }
}
