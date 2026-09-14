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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一条传输通道（HTTP / SQL）的流量统计。
 *
 * <p>与数据包统计一样，一次请求/响应记一次，因此 {@link PacketStats#getCount()} 是交互次数、
 * {@link PacketStats#getTotalSize()} 是字节数，最大/最小/均值也随之得到。
 * 端点维度（HTTP 的 {@code 方法 + host + 路径}、SQL 的 {@code 语句类型 + 表名}）另开一张表分列，
 * 便于看出流量集中在哪个接口或哪张表上。
 *
 * <p>写入方分布在网络线程与 {@code sre-mysql-sync-*} / {@code sre-match-record-*} 等后台线程上，
 * 因此累加器与明细表都必须线程安全。
 */
public final class ChannelTrafficStats {

    private final String id;

    private final PacketStats outbound = new PacketStats();
    private final PacketStats inbound = new PacketStats();

    /** 端点 -> 该端点本侧发出的统计。 */
    private final ConcurrentHashMap<String, PacketStats> outboundByEndpoint = new ConcurrentHashMap<>();
    /** 端点 -> 该端点对端发来的统计。 */
    private final ConcurrentHashMap<String, PacketStats> inboundByEndpoint = new ConcurrentHashMap<>();

    ChannelTrafficStats(String id) {
        this.id = id;
    }

    /** 通道 id，例如 {@code http} / {@code sql}。 */
    public String getId() {
        return id;
    }

    /**
     * 记录一次交互。
     *
     * @param endpoint      分类标签，见 {@link TrafficRecorder} 的归一化规则
     * @param outboundBytes 本侧发出的字节数
     * @param inboundBytes  对端发来的字节数，没有响应体时传 0（次数仍然计入）
     */
    public void record(String endpoint, long outboundBytes, long inboundBytes) {
        outbound.update(outboundBytes);
        inbound.update(inboundBytes);
        outboundByEndpoint.computeIfAbsent(endpoint, k -> new PacketStats()).update(outboundBytes);
        inboundByEndpoint.computeIfAbsent(endpoint, k -> new PacketStats()).update(inboundBytes);
    }

    public void reset() {
        outbound.reset();
        inbound.reset();
        outboundByEndpoint.clear();
        inboundByEndpoint.clear();
    }

    public PacketStats getOutbound() {
        return outbound;
    }

    public PacketStats getInbound() {
        return inbound;
    }

    public long getOutboundCount() {
        return outbound.getCount();
    }

    public long getOutboundBytes() {
        return outbound.getTotalSize();
    }

    public long getInboundCount() {
        return inbound.getCount();
    }

    public long getInboundBytes() {
        return inbound.getTotalSize();
    }

    /** 收发字节合计，用于全局总计。 */
    public long getTotalBytes() {
        return getOutboundBytes() + getInboundBytes();
    }

    public long getTotalCount() {
        return getOutboundCount() + getInboundCount();
    }

    /** 是否有任何数据（含只有次数没有字节的纯写语句）。 */
    public boolean hasData() {
        return getTotalCount() > 0L;
    }

    /** 已出现的端点数。 */
    public int getEndpointCount() {
        return Math.max(outboundByEndpoint.size(), inboundByEndpoint.size());
    }

    /**
     * 端点明细排行。
     *
     * <p>按字节排序时会过滤掉 0 字节的端点，避免纯写 SQL（只计次数、字节为 0）把榜单排满；
     * 但如果这个方向本来就一个字节都没有（例如 SQL 的接收方向），就退回按次数排，
     * 否则有数据却会显示成「暂无数据」。
     */
    public List<Map.Entry<String, PacketStats>> topEndpoints(boolean outbound, boolean byBytes, int limit) {
        Map<String, PacketStats> source = outbound ? outboundByEndpoint : inboundByEndpoint;
        List<Map.Entry<String, PacketStats>> entries = new ArrayList<>(source.entrySet());
        boolean sortByBytes = byBytes && entries.stream().anyMatch(e -> e.getValue().getTotalSize() > 0L);
        if (sortByBytes) {
            entries.removeIf(e -> e.getValue().getTotalSize() == 0L);
        }
        Comparator<Map.Entry<String, PacketStats>> comparator = sortByBytes
                ? Comparator.comparingLong((Map.Entry<String, PacketStats> e) -> e.getValue().getTotalSize())
                : Comparator.comparingLong((Map.Entry<String, PacketStats> e) -> e.getValue().getCount());
        entries.sort(comparator.reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }
}
