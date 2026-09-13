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

package io.wifi.starrailexpress.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.network.NetworkStatsDisplay;
import io.wifi.starrailexpress.network.NetworkStatsDisplay.RankingMode;
import io.wifi.starrailexpress.network.NetworkStatsExporter;
import io.wifi.starrailexpress.network.NetworkStatistics;
import io.wifi.starrailexpress.network.PacketStats;
import io.wifi.starrailexpress.network.PlayerPacketStats;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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
 * 客户端网络统计命令 {@code /tmm:netstatsc}。
 *
 * <p>统计的是客户端这一侧看到的流量（本客户端发出与本客户端收到），数据存在客户端本地，
 * 导出到客户端运行目录（{@code .minecraft}）下的 {@code netstats/}。服务端的对应命令是
 * {@code /tmm:netstats}，两侧的 {@code start}/{@code stop} 互不影响。
 *
 * <p>客户端命令没有权限体系，因此不加 {@code requires} 判断。
 */
public class NetworkStatsClientCommand {

    private static final int DEFAULT_RANKING_LIMIT = 10;
    private static final int DEFAULT_EXPORT_LIMIT = 200;
    private static final int MAX_LIMIT = 5000;
    private static final int MAX_TYPES_PER_PLAYER = 8;

    private static final DateTimeFormatter TIME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                register(dispatcher));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("tmm:netstatsc")
                .executes(NetworkStatsClientCommand::showGlobalStats)
                .then(ClientCommandManager.literal("start").executes(NetworkStatsClientCommand::startRecording))
                .then(ClientCommandManager.literal("stop").executes(NetworkStatsClientCommand::stopRecording))
                .then(ClientCommandManager.literal("status").executes(NetworkStatsClientCommand::showStatus))
                .then(ClientCommandManager.literal("reset").executes(NetworkStatsClientCommand::resetStats))
                .then(ClientCommandManager.literal("global").executes(NetworkStatsClientCommand::showGlobalStats))
                .then(rankingBranch("rankings", null))
                .then(rankingBranch("outbound_rankings", Boolean.TRUE))
                .then(rankingBranch("inbound_rankings", Boolean.FALSE))
                .then(ClientCommandManager.literal("byplayer").executes(NetworkStatsClientCommand::showStatsByPlayer))
                .then(ClientCommandManager.literal("player").executes(NetworkStatsClientCommand::showPlayerStats))
                .then(ClientCommandManager.literal("export")
                        .executes(ctx -> exportStats(ctx, DEFAULT_EXPORT_LIMIT))
                        .then(ClientCommandManager.argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                .executes(ctx -> exportStats(ctx,
                                        IntegerArgumentType.getInteger(ctx, "limit")))))
        );
    }

    private static NetworkStatistics stats() {
        return NetworkStatistics.getClientInstance();
    }

    private static void reply(FabricClientCommandSource source, Component message) {
        source.sendFeedback(message);
    }

    private static void hint(FabricClientCommandSource source, Component message) {
        source.sendFeedback(message.copy().withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ 记录开关

    private static int startRecording(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        stats().startRecording();
        reply(source, Component.literal("已开始记录客户端网络统计（已清空上一次的数据）")
                .withStyle(ChatFormatting.GREEN));
        hint(source, Component.literal("只统计自定义载荷包，原版 Minecraft 包不计入"));
        hint(source, Component.literal("用 /tmm:netstatsc export 导出到客户端 netstats/ 目录"));
        return 1;
    }

    private static int stopRecording(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        boolean wasRecording = stats().isRecording();
        stats().stopRecording();
        reply(source, Component.literal(wasRecording
                ? "已停止记录，数据仍保留在内存中"
                : "当前本来就没有在记录").withStyle(wasRecording ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        return 1;
    }

    private static int resetStats(CommandContext<FabricClientCommandSource> context) {
        stats().resetStats();
        reply(context.getSource(), Component.literal("已清空客户端网络统计数据").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int showStatus(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        NetworkStatistics stats = stats();

        reply(source, Component.literal("=== 网络统计状态 (客户端) ===").withStyle(ChatFormatting.BOLD));
        reply(source, Component.literal("记录中: " + (stats.isRecording() ? "是" : "否"))
                .withStyle(stats.isRecording() ? ChatFormatting.GREEN : ChatFormatting.RED));
        long startedAt = stats.getRecordingStartedAt();
        reply(source, Component.literal("本次开始时间: "
                + (startedAt > 0L ? formatTime(startedAt) : "尚未开始")));
        reply(source, Component.literal("已跟踪包类型: " + stats.getTrackedTypeCount()));
        reply(source, Component.literal("字节数来源: 实测 " + stats.getMeasuredSizeCount()
                + " 包，回退估算 " + stats.getFallbackSizeCount() + " 包"));
        reply(source, Component.literal("导出目录: " + NetworkStatsExporter.exportDirectory()));
        return 1;
    }

    // ------------------------------------------------------------------ 展示

    private static int showGlobalStats(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        NetworkStatistics stats = stats();

        reply(source, Component.literal("=== 全局网络统计 (客户端) ===").withStyle(ChatFormatting.BOLD));
        reply(source, Component.literal("记录中: " + (stats.isRecording() ? "是" : "否"))
                .withStyle(stats.isRecording() ? ChatFormatting.GREEN : ChatFormatting.RED));
        reply(source, Component.literal("本端发出: " + stats.getOutboundPackets() + " 包 / "
                + stats.getOutboundBytes() + " 字节"));
        reply(source, Component.literal("本端接收: " + stats.getInboundPackets() + " 包 / "
                + stats.getInboundBytes() + " 字节"));
        reply(source, Component.literal("平均包大小: " + NetworkStatsDisplay.format(stats.getAveragePacketSize()) + " 字节"));
        return 1;
    }

    /**
     * 构造一条排行分支：{@code <名字> [count|bytes|avg_size] [limit]}。
     *
     * <p>不带维度时默认按包数量；{@code <名字> <limit>} 这种旧写法也仍然有效。
     *
     * @param outbound {@code null} 表示分支同时显示收发两个方向
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> rankingBranch(String name,
                                                                                  @Nullable Boolean outbound) {
        LiteralArgumentBuilder<FabricClientCommandSource> branch = ClientCommandManager.literal(name)
                .executes(ctx -> showRankings(ctx, DEFAULT_RANKING_LIMIT, outbound, RankingMode.COUNT));
        for (RankingMode mode : RankingMode.values()) {
            branch.then(ClientCommandManager.literal(mode.argument())
                    .executes(ctx -> showRankings(ctx, DEFAULT_RANKING_LIMIT, outbound, mode))
                    .then(ClientCommandManager.argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                            .executes(ctx -> showRankings(ctx,
                                    IntegerArgumentType.getInteger(ctx, "limit"), outbound, mode))));
        }
        branch.then(ClientCommandManager.argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                .executes(ctx -> showRankings(ctx,
                        IntegerArgumentType.getInteger(ctx, "limit"), outbound, RankingMode.COUNT)));
        return branch;
    }

    private static int showRankings(CommandContext<FabricClientCommandSource> context, int limit,
                                    @Nullable Boolean outbound, RankingMode mode) {
        FabricClientCommandSource source = context.getSource();
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

    private static int showStatsByPlayer(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        Map<String, PlayerPacketStats> all = stats().getPlayerStats();

        reply(source, Component.literal("=== 按玩家网络统计 (客户端) ===").withStyle(ChatFormatting.BOLD));
        if (all.isEmpty()) {
            hint(source, Component.literal("暂无玩家统计数据"));
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

    /** 客户端只认识本地玩家，因此这个子命令固定展示本地玩家的统计。 */
    private static int showPlayerStats(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        NetworkStatistics stats = stats();

        Player localPlayer = stats.getLocalPlayer();
        if (localPlayer == null) {
            hint(source, Component.literal("当前拿不到本地玩家身份（是否还没进入世界）"));
            return 1;
        }
        String name = localPlayer.getName().getString();

        PlayerPacketStats playerStats = stats.getPlayerStats().get(name);
        if (playerStats == null) {
            hint(source, Component.literal("没有本地玩家 " + name + " 的统计数据"));
            return 1;
        }

        reply(source, Component.literal("=== 玩家 " + name + " 的网络统计 (客户端) ===").withStyle(ChatFormatting.BOLD));
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

    private static int exportStats(CommandContext<FabricClientCommandSource> context, int limit) {
        FabricClientCommandSource source = context.getSource();
        try {
            Path file = NetworkStatsExporter.export(stats(), limit);
            reply(source, Component.literal("网络统计已导出到: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(file.toAbsolutePath().toString()).withStyle(ChatFormatting.WHITE)));
            return 1;
        } catch (Exception e) {
            source.sendError(Component.literal("导出网络统计失败: " + e.getMessage()));
            return 0;
        }
    }

    // ------------------------------------------------------------------ 工具

    private static List<Map.Entry<String, PacketStats>> topTypes(Map<String, PacketStats> typeStats) {
        List<Map.Entry<String, PacketStats>> entries = new ArrayList<>(typeStats.entrySet());
        entries.sort(Comparator.comparingLong((Map.Entry<String, PacketStats> e) -> e.getValue().getCount()).reversed());
        return entries.size() > MAX_TYPES_PER_PLAYER ? entries.subList(0, MAX_TYPES_PER_PLAYER) : entries;
    }

    private static String formatTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(TIME_STAMP);
    }
}
