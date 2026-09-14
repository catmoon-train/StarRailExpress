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
 * 自定义列车物品服务端同步。
 *
 * <p>
 * 传输已统一到 {@link ContentSyncServer}：「先发哈希，客户端缓存未命中才下全文（deflate 压缩）」。
 * 旧实现是「把 JSON 切成 30000 字符的分块、无条件推给每个玩家，并且服务端自己维护
 * 每个玩家的哈希表」，改为统一的握手协议后，加入服务器从「N 个分块包」降到 1 个小包，
 * 配置没变的客户端一个字节都不用发。
 */
public final class CustomItemServerNetwork {

    private CustomItemServerNetwork() {
    }

    /** 配置变更后调用：丢弃缓存并给所有在线玩家重新握手。 */
    public static void syncToAllPlayers(MinecraftServer server) {
        ContentSyncServer.invalidate(ContentChannel.CUSTOM_ITEM);
        ContentSyncServer.broadcastHandshake(server);
    }

    /** 玩家加入时调用：只发哈希（几十字节）。 */
    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        ContentSyncServer.handshakeTo(player);
    }

    /** 丢弃服务端缓存的内容与哈希（下次握手重新读文件计算）。 */
    public static void clearCache() {
        ContentSyncServer.invalidate(ContentChannel.CUSTOM_ITEM);
    }
}
