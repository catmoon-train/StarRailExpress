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

import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.VolunteerOpenSelectScreen;
import io.wifi.starrailexpress.network.packet.VolunteerOpenSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;

public class VolunteerOpenClientReceiver {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(VolunteerOpenSyncS2CPacket.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(() -> {
                VolunteerOpenCache.updateFromPacket(payload);

                if (VolunteerOpenCache.canReOpen()) {
                    // 已经有了就不要再重建（否则每次同步都会把滚动位置/搜索框重置掉）
                    if (mc.screen instanceof VolunteerOpenSelectScreen) {
                        return;
                    }
                    // 开局运镜（开场动画）期间不抢屏，等它播完再打开
                    if (!io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleSelectionScreenFactory
                            .canShowNow()) {
                        return;
                    }
                    if (mc.screen == null || mc.screen instanceof ChatScreen || mc.screen instanceof PauseScreen) {
                        mc.setScreen(new VolunteerOpenSelectScreen());
                    }
                } else if (mc.screen instanceof VolunteerOpenSelectScreen) {
                    mc.setScreen(null);
                }
            });
        });
    }
}
