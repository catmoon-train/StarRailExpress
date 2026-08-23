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
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.GreatDetectiveRoleData;

/**
 * 大侦探 HUD Mixin
 * 
 * 显示技能状态：
 * - 冷却时间
 */
public class GreatDetectiveHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.GREAT_DETECTIVE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取探员组件
            var detectiveOpt = RoleData.getOptional(GreatDetectiveRoleData.class, client.player);
            if (detectiveOpt.isEmpty())
                return;
            GreatDetectiveRoleData detectiveComponent = detectiveOpt.get();

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10; // 距离右边缘
            int y = screenHeight - 20; // 距离底部

            Font font = client.font;

            if (detectiveComponent.isInCooldown()) {
                long time = detectiveComponent.getCooldownLeftTime();
                // 显示技能冷却
                float cdSeconds = time / 20;
                Component cdText = Component.translatable("hud.noellesroles.great_detective.cooldown",
                        String.format("%.1f", cdSeconds));

                // 红色文字表示冷却中
                context.drawString(font, cdText, x - font.width(cdText), y, CommonColors.RED);

            } else {
                // 技能可用 - 显示金币消耗提示
                Component readyText = Component.translatable("hud.noellesroles.great_detective.ready");
                context.drawString(font, readyText, x - font.width(readyText), y, CommonColors.GREEN);
            }
        });
    }
}