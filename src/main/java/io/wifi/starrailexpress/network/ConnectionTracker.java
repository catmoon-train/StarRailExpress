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

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 每条 {@code Connection} 一个实例，用于把数据包归集到正确的 {@link NetworkStatistics} 实例与玩家。
 *
 * <p>侧别逐个数据包判定（就是一次 {@code instanceof}，代价可忽略），不做缓存：集成服务器里
 * 同一条连接在不同阶段可能由不同侧的监听器接管，缓存侧别会把流量记到错误的一侧。
 *
 * <p>真正需要缓存的是「玩家身份」——把玩家名取成字符串是有分配的，所以这里按玩家对象引用判断，
 * 只有引用变了才重新解析名字与统计对象。
 */
public final class ConnectionTracker {

    private Object cachedOwner;
    private PlayerPacketStats cachedPlayerStats;

    /**
     * 记录一个自定义载荷。
     *
     * @param outbound {@code true} 表示本侧发出（{@code Connection.send}），
     *                 {@code false} 表示本侧收到（{@code Connection.channelRead0}）
     * @param listener 该连接当前的包监听器，用于判定侧别与玩家身份
     */
    public void record(CustomPacketPayload payload, boolean outbound, @Nullable PacketListener listener) {
        if (listener == null) {
            // 握手早期还没有监听器，此时也没有统计需求。
            return;
        }

        NetworkStatistics serverStats = NetworkStatistics.getInstance();
        NetworkStatistics clientStats = NetworkStatistics.getClientInstance();
        // 两侧都没在记录时尽早退出，避免为每条自定义载荷做后续工作。
        if (!serverStats.isRecording() && !clientStats.isRecording()) {
            return;
        }

        boolean serverSide = listener instanceof ServerCommonPacketListenerImpl;
        NetworkStatistics stats = serverSide ? serverStats : clientStats;
        if (!stats.isRecording()) {
            return;
        }

        NetworkStatistics.TypeCacheEntry entry = stats.cacheEntry(payload.type());
        NetworkStatistics.PacketTypeStats typeStats = entry.getStats();
        if (typeStats == null) {
            // 该类型由外部记账（CCA 组件同步要按组件 key 分列），跳过以免重复计数。
            return;
        }

        long size = NetworkUtils.measurePayloadSize(payload, stats);
        stats.record(typeStats, size, outbound, playerStatsFor(stats, listener, serverSide));
    }

    @Nullable
    private PlayerPacketStats playerStatsFor(NetworkStatistics stats, PacketListener listener, boolean serverSide) {
        // 服务端：只有进入 play 阶段才拿得到玩家身份，登录/配置阶段的载荷没有归属玩家。
        // 客户端：本地玩家对象由 SREClient 注入。
        Object owner = serverSide
                ? (listener instanceof ServerGamePacketListenerImpl gameListener ? gameListener.player : null)
                : stats.getLocalPlayer();

        if (owner == null) {
            return null;
        }
        if (owner != cachedOwner) {
            cachedOwner = owner;
            cachedPlayerStats = stats.playerStatsFor(((Player) owner).getName().getString());
        }
        return cachedPlayerStats;
    }
}
