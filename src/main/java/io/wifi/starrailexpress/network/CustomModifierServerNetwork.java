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

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.synccontent.ContentChannel;
import io.wifi.starrailexpress.synccontent.ContentSyncServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 自定义修饰符服务端同步（走统一的内容同步通道，见 {@link ContentSyncServer}）。
 */
public final class CustomModifierServerNetwork {

    private CustomModifierServerNetwork() {
    }

    /** 配置变更后调用：丢弃缓存并给所有在线玩家重新握手。 */
    public static void syncToAllPlayers(MinecraftServer server) {
        ContentSyncServer.invalidate(ContentChannel.CUSTOM_MODIFIER);
        ContentSyncServer.broadcastHandshake(server);
    }

    /** 玩家加入时调用：只发哈希。 */
    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        ContentSyncServer.handshakeTo(player);
    }

    /** 丢弃服务端缓存的内容与哈希。 */
    public static void clearCache() {
        ContentSyncServer.invalidate(ContentChannel.CUSTOM_MODIFIER);
    }
}
