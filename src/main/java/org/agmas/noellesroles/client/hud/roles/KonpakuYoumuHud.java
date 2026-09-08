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

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public class KonpakuYoumuHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(THMiscRoles.KONPAKU_YOUMU.identifier(), (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            var comp = SREAbilityPlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = 10; // 距离左边缘
            int y = screenHeight - 20; // 距离底部

            Font textRenderer = client.font;

            if (comp.status >= 1) {
                Component cdText = Component
                        .translatable("hud.noellesroles.konpaku_youmu.ghost.tip", comp.duration / 20)
                        .withStyle(ChatFormatting.AQUA);
                context.drawString(textRenderer, cdText, x , y, 0xffffffff);
            }
        });
    }
}
