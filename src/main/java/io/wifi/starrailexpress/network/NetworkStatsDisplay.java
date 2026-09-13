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

    public static String format(double value) {
        return String.format("%.2f", value);
    }
}
