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

package org.agmas.noellesroles.client.hud.modifiers;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

public abstract class RefugeeHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((context, deltaTracker) -> {
            final var client = Minecraft.getInstance();
            if (client.player == null || client.level == null)
                return;

            // 检查玩家是否有难民modifier
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(client.level);
            if (!worldModifierComponent.isModifier(client.player.getUUID(), SEModifiers.REFUGEE)) {
                return;
            }

            // 检查玩家是否为旁观者模式
            if (!SREClient.isPlayerSpectator()) {
                return;
            }

            // 获取难民组件
            RefugeeComponent refugeeComponent = RefugeeComponent.KEY.get(client.level);
            if (refugeeComponent == null) {
                return;
            }

            // 计算剩余时间
            long currentTime = client.level.getGameTime();
            long revivalTime = refugeeComponent.getRevivalTime(client.player.getUUID());

            if (revivalTime == -1) {
                return;
            }

            long ticksRemaining = revivalTime - currentTime;
            int secondsRemaining = (int) ((ticksRemaining + 19) / 20);

            Component text = Component
                    .translatable("gui.stupid_express.refugee.revival",
                            Component.literal(secondsRemaining + "s").withStyle(ChatFormatting.RED))
                    .withStyle(ChatFormatting.YELLOW);
            Component text2 = Component.translatable("gui.stupid_express.refugee.tip",
                    SEModifiers.REFUGEE.getName(true).withStyle(ChatFormatting.BOLD)).withStyle(ChatFormatting.GOLD);
            Component text3 = Component.translatable("gui.stupid_express.refugee.tip.2",
                    NoellesrolesClient.roleIntroClientBind.getTranslatedKeyMessage().copy()
                            .withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GREEN);
            int color = 0xffffffff;

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int textWidth = client.font.width(text);

            // 右下角显示
            int x = screenWidth / 2;
            int y = screenHeight - 102;

            context.drawString(client.font, text, x - textWidth / 2, y, color);
            context.drawString(client.font, text2, x - client.font.width(text2) / 2, y - 12, color);
            context.drawString(client.font, text3, x - client.font.width(text3) / 2, y + 12, color);
        });
    }
}
