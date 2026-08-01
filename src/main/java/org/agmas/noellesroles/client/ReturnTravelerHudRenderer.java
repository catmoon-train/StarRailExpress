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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.innocence.return_traveler.ReturnTravelerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.awt.Color;

public class ReturnTravelerHudRenderer {
    // 客户端平滑冷却倒计时状态：服务端按 200 tick 才同步一次，这里以服务端值为锚点逐帧递减
    private static float clientCdTicks = 0f;
    private static int lastServerCd = 0;
    private static long lastMs = 0L;

    public static void registerRendererEvent() {
        RoleHudRenderCallback.EVENT.register(ModRoles.RETURN_TRAVELER.identifier(), (graphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || !SREClient.gameComponent.isRole(client.player, ModRoles.RETURN_TRAVELER))
                return;
            if (!GameUtils.isPlayerAliveAndSurvival(client.player))
                return;

            var component = ReturnTravelerPlayerComponent.KEY.get(client.player);
            var font = client.font;

            // 客户端平滑倒计时：以服务端同步值为锚点，逐帧递减，避免每 200 tick 才跳变一次
            int srvCd = component.oldFerryCooldown;
            long nowMs = System.currentTimeMillis();
            if (lastMs == 0L) lastMs = nowMs;
            float dtTicks = (float) Math.min(10.0, (nowMs - lastMs) / 50.0);
            lastMs = nowMs;
            if (srvCd != lastServerCd) {
                clientCdTicks = srvCd;
                lastServerCd = srvCd;
            } else {
                clientCdTicks = Math.max(0f, clientCdTicks - dtTicks);
            }

            boolean oldFerry = component.currentMode == ReturnTravelerPlayerComponent.MODE_OLD_FERRY;

            // 两个技能各自独立显示状态，选中项用 ▶ 标记，整体右对齐
            int rightX = graphics.guiWidth() - 30;
            int footerY = graphics.guiHeight() - 30;
            int y = footerY - 40;
            int white = Color.WHITE.getRGB();

            // 标题
            Component title = Component.translatable("message.noellesroles.return_traveler.hud.title")
                    .withStyle(ChatFormatting.GRAY);
            graphics.drawString(font, title, rightX - font.width(title), y, white);
            y += font.lineHeight + 3;

            // 旧日渡口（独立冷却）
            Component ofState;
            if (clientCdTicks > 0) {
                int secs = (int) Math.ceil(clientCdTicks / 20.0f);
                ofState = Component.translatable("message.noellesroles.return_traveler.hud.cooldown", secs)
                        .withStyle(ChatFormatting.RED);
            } else {
                ofState = Component.translatable("message.noellesroles.return_traveler.hud.ready")
                        .withStyle(ChatFormatting.GREEN);
            }
            Component ofLine = Component.empty()
                    .append(Component.literal(oldFerry ? "▶ " : "   ")
                            .withStyle(oldFerry ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("message.noellesroles.return_traveler.hud.old_ferry")
                            .withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(": "))
                    .append(ofState);
            graphics.drawString(font, ofLine, rightX - font.width(ofLine), y, white);
            y += font.lineHeight + 3;

            // 末班车（一局一次，独立状态）
            Component ltState = component.lastTrainUsed
                    ? Component.translatable("message.noellesroles.return_traveler.hud.used").withStyle(ChatFormatting.RED)
                    : Component.translatable("message.noellesroles.return_traveler.hud.ready").withStyle(ChatFormatting.GREEN);
            Component ltLine = Component.empty()
                    .append(Component.literal(!oldFerry ? "▶ " : "   ")
                            .withStyle(!oldFerry ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("message.noellesroles.return_traveler.hud.last_train")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(": "))
                    .append(ltState);
            graphics.drawString(font, ltLine, rightX - font.width(ltLine), y, white);
        });
    }
}
