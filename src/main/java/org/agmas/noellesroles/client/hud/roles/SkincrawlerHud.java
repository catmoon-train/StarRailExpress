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

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.skincrawler.SkincrawlerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class SkincrawlerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.SKINCRAWLER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) return;
            var comp = SkincrawlerPlayerComponent.KEY.get(client.player);
            Font font = client.font;
            int sw = client.getWindow().getGuiScaledWidth();
            int sy = client.getWindow().getGuiScaledHeight();

            Component text;
            if (comp.stealCooldown > 0) {
                int sec = (comp.stealCooldown + 19) / 20;
                text = Component.translatable("hud.noellesroles.skincrawler.cooldown", sec).withStyle(ChatFormatting.GRAY);
            } else {
                text = Component.translatable("hud.noellesroles.skincrawler.ready").withStyle(ChatFormatting.GREEN);
            }
            context.drawString(font, text, sw - font.width(text) - 8, sy - 24, 0xFFFFFF);

            // 显示抵挡剩余次数
            Component blockText = Component.translatable("hud.noellesroles.skincrawler.block_charges", comp.blockCharges)
                    .withStyle(comp.blockCharges > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
            context.drawString(font, blockText, sw - font.width(blockText) - 8, sy - 36, 0xFFFFFF);
        });
    }
}
