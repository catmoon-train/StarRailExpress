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
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.BounsRoles;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

import java.util.function.BiConsumer;

/**
 * NECROMANCER
 */
public class NecromancerHud {

    public static void register() {
        BiConsumer<FakeGuiGraphics, DeltaTracker> necoConsumer = (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();

            // 获取斗士组件
            var nc = NecromancerComponent.KEY.get(client.level);
            var pc = SREAbilityPlayerComponent.KEY.get(client.player);
            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 10; // 距离右边缘
            int y = screenHeight - 20; // 距离底部

            Font textRenderer = client.font;
            if (pc.cooldown > 0) {
                // 显示技能冷却
                int cdSeconds = (int) pc.getCooldownSeconds();
                Component cdText = Component.translatable("hud.necromancer.cooldown",
                        cdSeconds);

                // 红色文字表示冷却中
                context.drawString(textRenderer, cdText, x - textRenderer.width(cdText), y, CommonColors.RED);

            } else {
                // 技能就绪 - 显示绿色提示
                Component readyText = Component.translatable("hud.necromancer.available", nc.getAvailableRevives());
                context.drawString(textRenderer, readyText, x - textRenderer.width(readyText), y, CommonColors.GREEN);
            }
        };
        RoleHudRenderCallback.EVENT.register(BounsRoles.CAT_NECROMANCER.identifier(), necoConsumer);
        RoleHudRenderCallback.EVENT.register(SERoles.NECROMANCER.identifier(), necoConsumer);
    }
}