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

package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleRotationScreen;
import io.wifi.starrailexpress.network.packet.RoleRotationSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;

public class RoleRotationClientReceiver {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(RoleRotationSyncS2CPacket.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(() -> {

                // 更新客户端缓存
                RoleRotationCache.updateFromPacket(payload);

                if (RoleRotationCache.isSelecting() || RoleRotationCache.getConfirmCountdown() > 0) {
                    if ((mc.screen == null) || mc.screen instanceof ChatScreen || mc.screen instanceof PauseScreen) {
                        mc.setScreen(new RoleRotationScreen());
                    }
                } else {
                    if (mc.screen instanceof RoleRotationScreen) {
                        mc.setScreen(null);
                    }
                }
            });
        });
    }
}
