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
import org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class EmbalmerHud {
    // 客户端平滑：服务端只在状态边界同步，这里自行逐帧递减，收到新值时以服务端为准纠正
    private static int clientTicksLeft = -1;
    private static int lastServerTicksLeft = -1;
    private static int clientCooldown = -1;
    private static int lastServerCooldown = -1;

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.EMBALMER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) return;
            var comp = EmbalmerPlayerComponent.KEY.get(client.player);
            Font font = client.font;
            int sw = client.getWindow().getGuiScaledWidth();
            int sy = client.getWindow().getGuiScaledHeight();

            // 激活剩余时间平滑
            if (comp.masqueradeTicksLeft != lastServerTicksLeft) {
                clientTicksLeft = comp.masqueradeTicksLeft;
                lastServerTicksLeft = comp.masqueradeTicksLeft;
            } else if (clientTicksLeft > 0) {
                clientTicksLeft--;
            }
            // 冷却剩余时间平滑
            if (comp.masqueradeCooldown != lastServerCooldown) {
                clientCooldown = comp.masqueradeCooldown;
                lastServerCooldown = comp.masqueradeCooldown;
            } else if (clientCooldown > 0) {
                clientCooldown--;
            }

            Component text;
            if (clientTicksLeft > 0) {
                int sec = (clientTicksLeft + 19) / 20;
                text = Component.translatable("hud.noellesroles.embalmer.active", sec).withStyle(ChatFormatting.LIGHT_PURPLE);
            } else if (clientCooldown > 0) {
                int sec = (clientCooldown + 19) / 20;
                text = Component.translatable("hud.noellesroles.embalmer.cooldown", sec).withStyle(ChatFormatting.GRAY);
            } else {
                text = Component.translatable("hud.noellesroles.embalmer.ready").withStyle(ChatFormatting.GREEN);
            }
            context.drawString(font, text, sw - font.width(text) - 8, sy - 24, 0xFFFFFF);
        });
    }
}
