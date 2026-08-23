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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role_data.neutral.NianShouRoleData;
import org.agmas.noellesroles.role.ModRoles;

public class NianShouHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.NIAN_SHOU_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取红包数据
            var nianShouData = RoleData.getOptional(NianShouRoleData.class, client.player);
            if (nianShouData.isEmpty())
                return;

            // 渲染红包数量
            int redPacketCount = nianShouData.get().getRedPacketCount();

            var font = client.font;
            int x = guiGraphics.guiWidth() - 10;
            int y = guiGraphics.guiHeight() - 30;

            MutableComponent text = net.minecraft.network.chat.Component
                    .translatable("hud.noellesroles.nianshou.red_packets", redPacketCount);

            guiGraphics.drawString(font, text, x - font.width(text), y, 0xFFD700, true);
        });
    }
}
