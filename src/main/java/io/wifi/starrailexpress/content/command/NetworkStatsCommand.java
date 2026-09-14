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

package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.network.ChannelTrafficStats;
import io.wifi.starrailexpress.network.NetworkStatsDisplay;
import io.wifi.starrailexpress.network.NetworkStatsDisplay.RankingMode;
import io.wifi.starrailexpress.network.NetworkStatsExporter;
import io.wifi.starrailexpress.network.NetworkStatistics;
import io.wifi.starrailexpress.network.PacketStats;
import io.wifi.starrailexpress.network.PlayerPacketStats;
import io.wifi.starrailexpress.network.TrafficChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 服务端网络统计命令 {@code /tmm:netstats}。
 *
 * <p>统计需要先 {@code start} 才会写入内存；{@code start} 会清空上一次的数据，开始新的统计会话。
 * 数据是本服务端的流量（本服发出与本服收到），导出到服务端运行目录下的 {@code netstats/}。
 * 客户端的对应命令是 {@code /tmm:netstatsc}。
 *
 * <p>除自定义载荷包外，还有 HTTP 与 SQL 两条通道各自统计，用 {@code http} / {@code sql}
 * 子命令独立 start/stop/show；主 {@code start} 会联动开启两条通道，{@code http stop} 之类可以单独停。
 */
public class NetworkStatsCommand {

    private static final int DEFAULT_RANKING_LIMIT = 10;
    private static final int DEFAULT_EXPORT_LIMIT = 200;
    private static final int MAX_LIMIT = 5000;
    /** 聊天里每个玩家最多列几种包类型，完整数据看导出文件。 */
    private static final int MAX_TYPES_PER_PLAYER = 8;

    private static final DateTimeFormatter TIME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:netstats")
                .requires(source -> source.hasPermission(2))
                .executes(NetworkStatsCommand::showGlobalStats)
                .then(Commands.literal("start").executes(NetworkStatsCommand::startRecording))
                .then(Commands.literal("stop").executes(NetworkStatsCommand::stopRecording))
                .then(Commands.literal("status").executes(NetworkStatsCommand::showStatus))
                .then(Commands.literal("reset").executes(NetworkStatsCommand::resetStats))
                .then(Commands.literal("global").executes(NetworkStatsCommand::showGlobalStats))
                .then(rankingBranch("rankings", null))
                .then(rankingBranch("outbound_rankings", Boolean.TRUE))
                .then(rankingBranch("inbound_rankings", Boolean.FALSE))
                // 旧名别名：原名里的 server/client 指的是包的方向，容易误解，保留仅为兼容。
                .then(rankingBranch("server_rankings", Boolean.TRUE))
                .then(rankingBranch("client_rankings", Boolean.FALSE))
                .then(Commands.literal("byplayer").executes(NetworkStatsCommand::showStatsByPlayer))
                .then(Commands.literal("player")
                        .executes(ctx -> showPlayerStats(ctx, ctx.getSource().getPlayer()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> showPlayerStats(ctx, EntityArgument.getPlayer(ctx, "target")))))
                // HTTP / SQL 流量各成一条通道，能被单独 start/stop/show。
                .then(channelBranch(TrafficChannel.HTTP))
                .then(channelBranch(TrafficChannel.SQL))
                .then(Commands.literal("export")
                        .executes(ctx -> exportStats(ctx, DEFAULT_EXPORT_LIMIT))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                .executes(ctx -> exportStats(ctx,
                                        IntegerArgumentType.getInteger(ctx, "limit")))))
        );
    }

    private static NetworkStatistics stats() {
        return NetworkStatistics.getInstance();
    }

    private static void reply(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    // ------------------------------------------------------------------ 记录开关

    private static int startRecording(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        stats().startRecording();
        reply(source, Component.literal("已开始记录网络统计（已清空上一次的数据）").withStyle(ChatFormatting.GREEN));
        reply(source, Component.literal("统计自定义载荷包 + HTTP + SQL；原版 Minecraft 包不计入")
                .withStyle(ChatFormatting.GRAY));
        reply(source, Component.literal("只想记录其中一项时，用 /tmm:netstats http stop 或 sql stop 单独停")
                .withStyle(ChatFormatting.GRAY));
        reply(source, Component.literal("用 /tmm:netstats export 导出到服务端 netstats/ 目录").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int stopRecording(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        NetworkStatistics stats = stats();
        // 主 stop 联动三项，只要有一项在记录就算「停到了东西」。
        boolean wasRecording = stats.isRecording() || stats.isHttpRecording() || stats.isSqlRecording();
        stats.stopRecording();
        reply(source, Component.literal(wasRecording
                ? "已停止记录（数据包、HTTP、SQL 三项都已停），数据仍保留在内存中"
                : "当前本来就没有在记录").withStyle(wasRecording ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        return 1;
    }

    private static int resetStats(CommandContext<CommandSourceStack> context) {
        stats().resetStats();
        reply(context.getSource(), Component.literal("已清空网络统计数据（含 HTTP 与 SQL）").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        NetworkStatistics stats = stats();

        reply(source, Component.literal("=== 网络统计状态 (服务端) ===").withStyle(ChatFormatting.BOLD));
        reply(source, Component.literal("记录中: " + (stats.isRecording() ? "是" : "否"))
                .withStyle(stats.isRecording() ? ChatFormatting.GREEN : ChatFormatting.RED));
        long startedAt = stats.getRecordingStartedAt();
        reply(source, Component.literal("本次开始时间: "
                + (startedAt > 0L ? formatTime(startedAt) : "尚未开始")));
        reply(source, Component.literal("已跟踪包类型: " + stats.getTrackedTypeCount()));
        reply(source, Component.literal("字节数来源: 实测 " + stats.getMeasuredSizeCount()
                + " 包，回退估算 " + stats.getFallbackSizeCount() + " 包"));
        for (TrafficChannel channel : TrafficChannel.values()) {
            reply(source, Component.literal(channel.label() + " 记录中: "
                    + (channel.isRecording(stats) ? "是" : "否")
                    + " · 交互 " + channel.stats(stats).getOutboundCount() + " 次")
                    .withStyle(channel.isRecording(stats) ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        reply(source, Component.literal("导出目录: " + NetworkStatsExporter.exportDirectory()));
        return 1;
    }

    // ------------------------------------------------------------------ 展示

    private static int showGlobalStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        NetworkStatistics stats = stats();

        reply(source, Component.literal("=== 全局网络统计 (服务端) ===").withStyle(ChatFormatting.BOLD));
        reply(source, Component.literal("记录中: " + (stats.isRecording() ? "是" : "否"))
                .withStyle(stats.isRecording() ? ChatFormatting.GREEN : ChatFormatting.RED));
        reply(source, Component.literal("本端发出: " + stats.getOutboundPackets() + " 包 / "
                + stats.getOutboundBytes() + " 字节"));
        reply(source, Component.literal("本端接收: " + stats.getInboundPackets() + " 包 / "
                + stats.getInboundBytes() + " 字节"));
        reply(source, Component.literal("平均包大小: " + NetworkStatsDisplay.format(stats.getAveragePacketSize()) + " 字节"));
        NetworkStatsDisplay.channelSummaryLines(stats).forEach(line -> reply(source, line));
        return 1;
    }

    /**
     * 构造一条排行分支：{@code <名字> [count|bytes|avg_size] [limit]}。
     *
     * <p>不带维度时默认按包数量；{@code <名字> <limit>} 这种旧写法也仍然有效。
     *
     * @param outbound {@code null} 表示分支同时显示收发两个方向
     */
    private static LiteralArgumentBuilder<CommandSourceStack> rankingBranch(String name,
                                                                           @Nullable Boolean outbound) {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal(name)
                .executes(ctx -> showRankings(ctx, DEFAULT_RANKING_LIMIT, outbound, RankingMode.COUNT));
        for (RankingMode mode : RankingMode.values()) {
            branch.then(Commands.literal(mode.argument())
                    .executes(ctx -> showRankings(ctx, DEFAULT_RANKING_LIMIT, outbound, mode))
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                            .executes(ctx -> showRankings(ctx,
                                    IntegerArgumentType.getInteger(ctx, "limit"), outbound, mode))));
        }
        branch.then(Commands.argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                .executes(ctx -> showRankings(ctx,
                        IntegerArgumentType.getInteger(ctx, "limit"), outbound, RankingMode.COUNT)));
        return branch;
    }

    private static int showRankings(CommandContext<CommandSourceStack> context, int limit,
                                    @Nullable Boolean outbound, RankingMode mode) {
        CommandSourceStack source = context.getSource();
        NetworkStatistics stats = stats();

        if (outbound == null || outbound) {
            NetworkStatsDisplay.rankingLines(stats, true, mode, limit)
                    .forEach(line -> reply(source, line));
        }
        if (outbound == null || !outbound) {
            NetworkStatsDisplay.rankingLines(stats, false, mode, limit)
                    .forEach(line -> reply(source, line));
        }
        return 1;
    }

    // ------------------------------------------------------------------ HTTP / SQL 通道

    /** 构造 {@code http} / {@code sql} 分支：{@code <通道> [start|stop|status|reset|show]}。 */
    private static LiteralArgumentBuilder<CommandSourceStack> channelBranch(TrafficChannel channel) {
        return Commands.literal(channel.argument())
                .executes(ctx -> showChannel(ctx, channel, DEFAULT_RANKING_LIMIT, null))
                .then(Commands.literal("start").executes(ctx -> startChannel(ctx, channel)))
                .then(Commands.literal("stop").executes(ctx -> stopChannel(ctx, channel)))
                .then(Commands.literal("status").executes(ctx -> showChannelStatus(ctx, channel)))
                .then(Commands.literal("reset").executes(ctx -> resetChannel(ctx, channel)))
                .then(channelShowBranch(channel));
    }

    /** {@code show [outbound|inbound] [limit]}：不带方向时收发两个排行都打印。 */
    private static LiteralArgumentBuilder<CommandSourceStack> channelShowBranch(TrafficChannel channel) {
        LiteralArgumentBuilder<CommandSourceStack> show = Commands.literal("show")
                .executes(ctx -> showChannel(ctx, channel, DEFAULT_RANKING_LIMIT, null));
        show.then(directionBranch(channel, "outbound", Boolean.TRUE));
        show.then(directionBranch(channel, "inbound", Boolean.FALSE));
        show.then(Commands.argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                .executes(ctx -> showChannel(ctx, channel,
                        IntegerArgumentType.getInteger(ctx, "limit"), null)));
        return show;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> directionBranch(TrafficChannel channel,
                                                                             String name,
                                                                             boolean outbound) {
        return Commands.literal(name)
                .executes(ctx -> showChannel(ctx, channel, DEFAULT_RANKING_LIMIT, outbound))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                        .executes(ctx -> showChannel(ctx, channel,
                                IntegerArgumentType.getInteger(ctx, "limit"), outbound)));
    }

    private static int startChannel(CommandContext<CommandSourceStack> context, TrafficChannel channel) {
        CommandSourceStack source = context.getSource();
        channel.start(stats());
        reply(source, Component.literal("已开始记录 " + channel.label() + " 流量（已清空该通道上一次的数据）")
                .withStyle(ChatFormatting.GREEN));
        reply(source, Component.literal("用 /tmm:netstats " + channel.argument() + " show 查看明细")
                .withStyle(ChatFormatting.GRAY));
        reply(source, Component.literal(channel.scopeNote(true)).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int stopChannel(CommandContext<CommandSourceStack> context, TrafficChannel channel) {
        CommandSourceStack source = context.getSource();
        boolean wasRecording = channel.isRecording(stats());
        channel.stop(stats());
        reply(source, Component.literal(wasRecording
                ? "已停止记录 " + channel.label() + " 流量，数据仍保留在内存中"
                : channel.label() + " 当前本来就没有在记录")
                .withStyle(wasRecording ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        return 1;
    }

    private static int resetChannel(CommandContext<CommandSourceStack> context, TrafficChannel channel) {
        channel.reset(stats());
        reply(context.getSource(), Component.literal("已清空 " + channel.label() + " 流量数据")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int showChannelStatus(CommandContext<CommandSourceStack> context, TrafficChannel channel) {
        CommandSourceStack source = context.getSource();
        NetworkStatistics stats = stats();
        ChannelTrafficStats traffic = channel.stats(stats);
        boolean recording = channel.isRecording(stats);

        reply(source, Component.literal("=== " + channel.label() + " 流量状态 (服务端) ===")
                .withStyle(ChatFormatting.BOLD));
        reply(source, Component.literal("记录中: " + (recording ? "是" : "否"))
                .withStyle(recording ? ChatFormatting.GREEN : ChatFormatting.RED));
        long startedAt = channel.recordingStartedAt(stats);
        reply(source, Component.literal("本次开始时间: " + (startedAt > 0L ? formatTime(startedAt) : "尚未开始")));
        reply(source, Component.literal("交互次数: " + traffic.getOutboundCount()));
        reply(source, Component.literal("字节数: 发出 " + traffic.getOutboundBytes()
                + " / 接收 " + traffic.getInboundBytes()));
        reply(source, Component.literal("端点数: " + traffic.getEndpointCount()));
        reply(source, Component.literal(channel.scopeNote(true)).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int showChannel(CommandContext<CommandSourceStack> context, TrafficChannel channel,
                                   int limit, @Nullable Boolean outbound) {
        CommandSourceStack source = context.getSource();
        NetworkStatistics stats = stats();

        if (outbound == null || outbound) {
            NetworkStatsDisplay.channelDetailLines(channel, stats, "服务端", true, limit)
                    .forEach(line -> reply(source, line));
        }
        if (outbound == null || !outbound) {
            NetworkStatsDisplay.channelDetailLines(channel, stats, "服务端", false, limit)
                    .forEach(line -> reply(source, line));
        }
        reply(source, NetworkStatsDisplay.endpointPlaceholderNote());
        return 1;
    }

    private static int showStatsByPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Map<String, PlayerPacketStats> all = stats().getPlayerStats();

        reply(source, Component.literal("=== 按玩家网络统计 (服务端) ===").withStyle(ChatFormatting.BOLD));
        if (all.isEmpty()) {
            reply(source, Component.literal("暂无玩家统计数据").withStyle(ChatFormatting.GRAY));
            return 1;
        }

        for (Map.Entry<String, PlayerPacketStats> entry : all.entrySet()) {
            PlayerPacketStats playerStats = entry.getValue();
            reply(source, Component.literal("玩家: " + entry.getKey()).withStyle(ChatFormatting.YELLOW));
            reply(source, Component.literal("  发出: " + playerStats.getOutboundPackets() + " 包 / "
                    + playerStats.getOutboundBytes() + " 字节"));
            reply(source, Component.literal("  接收: " + playerStats.getInboundPackets() + " 包 / "
                    + playerStats.getInboundBytes() + " 字节"));
            for (Map.Entry<String, PacketStats> typeEntry : topTypes(playerStats.getPacketTypeStats())) {
                reply(source, NetworkStatsDisplay.typeLine(typeEntry.getKey(), typeEntry.getValue(), "    "));
            }
        }
        return 1;
    }

    private static int showPlayerStats(CommandContext<CommandSourceStack> context, @Nullable ServerPlayer target) {
        CommandSourceStack source = context.getSource();
        if (target == null) {
            source.sendFailure(Component.literal("找不到目标玩家"));
            return 0;
        }

        PlayerPacketStats playerStats = stats().getPlayerStats().get(target.getName().getString());
        if (playerStats == null) {
            reply(source, Component.literal("没有玩家 " + target.getName().getString() + " 的统计数据")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }

        reply(source, Component.literal("=== 玩家 " + target.getName().getString() + " 的网络统计 ===")
                .withStyle(ChatFormatting.BOLD));
        reply(source, Component.literal("发出: " + playerStats.getOutboundPackets() + " 包 / "
                + playerStats.getOutboundBytes() + " 字节"));
        reply(source, Component.literal("接收: " + playerStats.getInboundPackets() + " 包 / "
                + playerStats.getInboundBytes() + " 字节"));
        for (Map.Entry<String, PacketStats> typeEntry : topTypes(playerStats.getPacketTypeStats())) {
            reply(source, NetworkStatsDisplay.typeLine(typeEntry.getKey(), typeEntry.getValue(), "  "));
        }
        return 1;
    }

    // ------------------------------------------------------------------ 导出

    private static int exportStats(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();
        try {
            Path file = NetworkStatsExporter.export(stats(), limit);
            reply(source, Component.literal("网络统计已导出到: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(file.toAbsolutePath().toString()).withStyle(ChatFormatting.WHITE)));
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("导出网络统计失败: " + e.getMessage()));
            return 0;
        }
    }

    // ------------------------------------------------------------------ 工具

    /** 取玩家按包类型统计里计数最高的若干项。 */
    private static List<Map.Entry<String, PacketStats>> topTypes(Map<String, PacketStats> typeStats) {
        List<Map.Entry<String, PacketStats>> entries = new ArrayList<>(typeStats.entrySet());
        entries.sort(Comparator.comparingLong((Map.Entry<String, PacketStats> e) -> e.getValue().getCount()).reversed());
        return entries.size() > MAX_TYPES_PER_PLAYER ? entries.subList(0, MAX_TYPES_PER_PLAYER) : entries;
    }

    private static String formatTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(TIME_STAMP);
    }
}
