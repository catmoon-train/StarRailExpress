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

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 命令里展示网络统计的公共部分，服务端与客户端命令共用，保证两侧输出一致。
 */
public final class NetworkStatsDisplay {

    /** 排行的度量维度。 */
    public enum RankingMode {
        COUNT("包数量", "count"),
        BYTES("总字节数", "bytes"),
        AVG_SIZE("平均包大小", "avg_size");

        private final String label;
        private final String argument;

        RankingMode(String label, String argument) {
            this.label = label;
            this.argument = argument;
        }

        /** 中文标签，例如「平均包大小」。 */
        public String label() {
            return label;
        }

        /** 命令里使用的字面量，例如 {@code avg_size}。 */
        public String argument() {
            return argument;
        }
    }

    private NetworkStatsDisplay() {
    }

    /**
     * 生成某一方向、某一维度的排行文本行。
     *
     * @param outbound {@code true} 排本端发出的，{@code false} 排本端收到的
     */
    public static List<Component> rankingLines(NetworkStatistics stats, boolean outbound,
                                               RankingMode mode, int limit) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("=== 本端" + (outbound ? "发出" : "接收") + "排行 · 按" + mode.label()
                + " (前" + limit + "名) ===").withStyle(ChatFormatting.BOLD));

        List<NetworkStatistics.PacketTypeStats> entries = switch (mode) {
            case COUNT -> stats.topBy(outbound, false, limit);
            case BYTES -> stats.topBy(outbound, true, limit);
            case AVG_SIZE -> stats.topByAverageSize(outbound, limit);
        };

        if (entries.isEmpty()) {
            lines.add(Component.literal("暂无数据（是否还没执行 start）").withStyle(ChatFormatting.GRAY));
            return lines;
        }

        for (int i = 0; i < entries.size(); i++) {
            NetworkStatistics.PacketTypeStats entry = entries.get(i);
            PacketStats counters = outbound ? entry.getOutbound() : entry.getInbound();
            lines.add(Component.literal((i + 1) + ". " + entry.getId() + " — " + describe(counters, mode)));
        }
        return lines;
    }

    /** 以当前维度为主指标，并附带另外两个维度的数值，便于一次看清楚。 */
    private static String describe(PacketStats stats, RankingMode mode) {
        String count = stats.getCount() + " 包";
        String total = stats.getTotalSize() + " 字节";
        String average = format(stats.getAverageSize()) + " 字节/包";
        return switch (mode) {
            case COUNT -> count + "（共 " + total + ", 均 " + average + "）";
            case BYTES -> total + "（" + count + ", 均 " + average + "）";
            case AVG_SIZE -> average + "（" + count + ", 共 " + total + "）";
        };
    }

    /** 玩家维度的明细行：一份数据本就不大，直接把三个数值都列出来。 */
    public static Component typeLine(String packetId, PacketStats stats, String indent) {
        return Component.literal(indent + packetId + ": " + stats.getCount() + " 包, "
                + stats.getTotalSize() + " 字节, 均 " + format(stats.getAverageSize()) + " 字节/包");
    }

    // ------------------------------------------------------------------ HTTP / SQL 通道

    /**
     * HTTP 与 SQL 各自的汇总行，用在 {@code global} 末尾。通道没数据时也照常显示，
     * 以免看起来像统计丢了。
     */
    public static List<Component> channelSummaryLines(NetworkStatistics stats) {
        List<Component> lines = new ArrayList<>();
        for (TrafficChannel channel : TrafficChannel.values()) {
            lines.add(channelSummaryLine(channel, stats));
        }
        lines.add(Component.literal("合计（含数据包）: " + stats.getTotalBytes() + " 字节")
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static Component channelSummaryLine(TrafficChannel channel, NetworkStatistics stats) {
        ChannelTrafficStats traffic = channel.stats(stats);
        boolean recording = channel.isRecording(stats);
        String state = recording ? "记录中" : "未开启";
        String body = traffic.hasData()
                ? traffic.getOutboundCount() + " 次 · 发出 " + traffic.getOutboundBytes()
                        + " 字节 · 接收 " + traffic.getInboundBytes() + " 字节"
                : "暂无数据";
        return Component.literal(channel.label() + ": " + state + " · " + body)
                .withStyle(recording ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    /**
     * 单条通道的明细：汇总 + 按字节倒序的端点排行。
     *
     * @param sideLabel 侧别，例如 {@code 服务端}
     * @param outbound  {@code true} 排本端发出的，{@code false} 排本端收到的
     */
    public static List<Component> channelDetailLines(TrafficChannel channel, NetworkStatistics stats,
                                                     String sideLabel, boolean outbound, int limit) {
        ChannelTrafficStats traffic = channel.stats(stats);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("=== " + channel.label() + " 流量 (" + sideLabel + ") ===")
                .withStyle(ChatFormatting.BOLD));

        if (outbound) {
            lines.add(Component.literal("发出: " + traffic.getOutboundCount() + " 次 / "
                    + traffic.getOutboundBytes() + " 字节 · 均 "
                    + format(traffic.getOutbound().getAverageSize()) + " 字节/次"));
        } else {
            lines.add(Component.literal("接收: " + traffic.getInboundCount() + " 次 / "
                    + traffic.getInboundBytes() + " 字节 · 均 "
                    + format(traffic.getInbound().getAverageSize()) + " 字节/次"));
        }
        lines.add(Component.literal("端点: " + traffic.getEndpointCount() + " 个").withStyle(ChatFormatting.GRAY));

        lines.add(Component.literal("--- 本端" + (outbound ? "发出" : "接收") + "排行 · 按字节 (前"
                + limit + "名) ---").withStyle(ChatFormatting.BOLD));

        List<Map.Entry<String, PacketStats>> entries = traffic.topEndpoints(outbound, true, limit);
        if (entries.isEmpty()) {
            lines.add(Component.literal("暂无数据（该方向是否还没产生流量）").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, PacketStats> entry = entries.get(i);
            PacketStats counters = entry.getValue();
            lines.add(Component.literal((i + 1) + ". " + entry.getKey() + " — "
                    + counters.getTotalSize() + " 字节（" + counters.getCount() + " 次, 均 "
                    + format(counters.getAverageSize()) + " 字节/次）"));
        }
        return lines;
    }

    /** 端点标签里 {@code {uuid}} / {@code {token}} 占位符的含义，跟在明细后面输出。 */
    public static Component endpointPlaceholderNote() {
        return Component.literal("注: 标签里的 {uuid}/{token} 是路径中的玩家 UUID 与随机令牌占位符，"
                + "查询串不计入；字节为载荷下界，不含请求头、TLS 与协议开销").withStyle(ChatFormatting.GRAY);
    }

    public static String format(double value) {
        return String.format("%.2f", value);
    }
}
