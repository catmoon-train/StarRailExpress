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

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.touhou.THMiscRoles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class THReimuHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(THMiscRoles.HAKUREI_REIMU_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator())
                return;
            Font font = client.font;

            int x = context.guiWidth() - 10;
            int y = context.guiHeight() - 20;
            var abilityCCA = SREAbilityPlayerComponent.KEY.get(client.player);
            Component text = Component.translatable("skill.noellesroles.reimu.tip",
                    NoellesrolesClient.abilityBind.getTranslatedKeyMessage()).withStyle(ChatFormatting.GREEN);
            if (abilityCCA.duration > 0) {
                text = Component.translatable("skill.noellesroles.reimu.duration", abilityCCA.duration / 20)
                        .withStyle(ChatFormatting.AQUA);
            } else if (abilityCCA.cooldown > 0) {
                text = Component.translatable("skill.noellesroles.reimu.cooldown", abilityCCA.cooldown / 20)
                        .withStyle(ChatFormatting.RED);
            }
            context.drawString(font, text, x - font.width(text), y, 0xFFFFFF);
        });
    }
}
