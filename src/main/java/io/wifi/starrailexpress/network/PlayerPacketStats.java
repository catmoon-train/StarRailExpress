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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 单个玩家（服务端即连接对应的玩家，客户端即本地玩家）方向的收发统计。
 *
 * <p>数值字段同样是线程安全的累加器；{@code packetTypeStats} 的 key 使用
 * {@link NetworkStatistics} 里按包类型缓存的同一个字符串实例，因此热路径上的
 * {@code computeIfAbsent} 只做一次哈希与身份比较。
 */
public final class PlayerPacketStats {

    private final LongAdder outboundPackets = new LongAdder();
    private final LongAdder outboundBytes = new LongAdder();
    private final LongAdder inboundPackets = new LongAdder();
    private final LongAdder inboundBytes = new LongAdder();

    private final ConcurrentHashMap<String, PacketStats> packetTypeStats = new ConcurrentHashMap<>();

    /** 本侧发往该玩家的数据包。 */
    public void recordOutbound(String packetId, long size) {
        outboundPackets.increment();
        outboundBytes.add(size);
        packetTypeStats.computeIfAbsent(packetId, k -> new PacketStats()).update(size);
    }

    /** 该玩家发往本侧的数据包。 */
    public void recordInbound(String packetId, long size) {
        inboundPackets.increment();
        inboundBytes.add(size);
        packetTypeStats.computeIfAbsent(packetId, k -> new PacketStats()).update(size);
    }

    public long getOutboundPackets() {
        return outboundPackets.sum();
    }

    public long getOutboundBytes() {
        return outboundBytes.sum();
    }

    public long getInboundPackets() {
        return inboundPackets.sum();
    }

    public long getInboundBytes() {
        return inboundBytes.sum();
    }

    /** 该玩家的按包类型明细，key 为包类型 id。 */
    public Map<String, PacketStats> getPacketTypeStats() {
        return packetTypeStats;
    }
}
