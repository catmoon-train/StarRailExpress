/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
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

import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 无妄 HUD：显示当前护盾层数（最多5层）。
 */
public class AsatyaHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.ASATYA_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }

            // 护盾层数（护盾组件对本人同步）
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(client.player);
            int layers = armor.getArmor();

            var font = client.font;
            int x = guiGraphics.guiWidth() - 10;
            int y = guiGraphics.guiHeight() - 30;

            MutableComponent text = Component.translatable("hud.noellesroles.asatya.shields", layers);

            guiGraphics.drawString(font, text, x - font.width(text), y, 0xB39DDB, true);
        });
    }
}
