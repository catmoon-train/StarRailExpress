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

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 发包辅助方法。
 *
 * <p>统计不再由这里负责：所有收发都在 {@code Connection} 层统一拦截（见
 * {@code io.wifi.starrailexpress.mixin.network.NetworkStatisticsMixin}），因此走
 * {@code ServerPlayNetworking}、{@code PacketSender} 还是 {@code player.connection.send}
 * 都会被计入。原先这里手工记账 + 假大小计算（常量 64 字节）的做法会与底层拦截重复计数，
 * 已移除；本类退化为纯粹的转发封装，保留原有签名以免改动 40 多处调用点。
 */
public class PacketTracker {

    /** 向单个玩家发送数据包。 */
    public static <T extends CustomPacketPayload> void sendToClient(ServerPlayer player, T payload) {
        ServerPlayNetworking.send(player, payload);
    }

    /** 向多个玩家发送同一个数据包。 */
    public static <T extends CustomPacketPayload> void sendToClients(Iterable<ServerPlayer> players, T payload) {
        // 先物化一次：Iterable 可能只能遍历一次，也避免边发边取。
        List<ServerPlayer> targets = new ArrayList<>();
        players.forEach(targets::add);
        for (ServerPlayer player : targets) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * 手工记录一个收到的数据包。
     *
     * <p>正常情况下不需要调用：{@code Connection} 层已经覆盖了全部收发。仅当某个包无法
     * 经过 {@code Connection} 层（例如本地直接投递）时才用得上。标签由调用方给定，
     * 若同一个包同时也经过 {@code Connection} 层会被统计两次。
     */
    public static void recordReceivedPacket(ResourceLocation packetId, long size, PacketFlow flow) {
        recordReceivedPacket(packetId.toString(), size, flow, null);
    }

    /** 手工记录一个收到的数据包，并指定来源玩家。见 {@link #recordReceivedPacket(ResourceLocation, long, PacketFlow)}。 */
    public static void recordReceivedPacket(String packetId, long size, PacketFlow flow, String sourcePlayer) {
        NetworkStatistics stats = flow == PacketFlow.SERVERBOUND
                ? NetworkStatistics.getInstance()
                : NetworkStatistics.getClientInstance();
        if (!stats.isRecording()) {
            return;
        }
        stats.recordExternal(packetId, size, false,
                sourcePlayer != null ? stats.playerStatsFor(sourcePlayer) : null);
    }

    /** 手工记录一个收到的数据包。见 {@link #recordReceivedPacket(ResourceLocation, long, PacketFlow)}。 */
    public static void recordReceivedPacket(String packetId, long size, PacketFlow flow) {
        recordReceivedPacket(packetId, size, flow, null);
    }
}
