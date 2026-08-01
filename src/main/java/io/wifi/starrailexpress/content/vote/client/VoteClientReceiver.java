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

import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class VoteClientReceiver {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(VoteSyncS2CPacket.TYPE, (packet, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                ClientVoteCache.updateFromPacket(packet);
                if (packet.active()) {
                    if (client.screen instanceof VoteScreen screen) {
                        // 更新屏幕数据
                        screen.updateData(packet);
                    } else if (packet.hasOptions()) {
                        client.setScreen(new VoteScreen()); // 无参构造
                    }
                } else {
                    // 投票结束，关闭屏幕
                    if (client.screen instanceof VoteScreen) {
                        client.screen.onClose();
                    }
                }
            });
        });
    }
}
