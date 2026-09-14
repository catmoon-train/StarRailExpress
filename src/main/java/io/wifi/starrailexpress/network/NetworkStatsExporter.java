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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 把 {@link NetworkStatistics} 的数据导出为 JSON 文件。
 *
 * <p>服务端与客户端共用这套逻辑，各自写到自己运行目录下的 {@code netstats/}：
 * 服务端是服务端根目录，客户端是 {@code .minecraft}，因此「客户端数据存客户端本地、
 * 服务端数据存服务端本地」是天然成立的。
 */
public final class NetworkStatsExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter HUMAN_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 导出目录名（相对于本侧运行目录）。 */
    private static final String OUTPUT_DIR = "netstats";

    private NetworkStatsExporter() {
    }

    /** 导出目录（绝对路径），用于命令反馈。 */
    public static Path exportDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(OUTPUT_DIR).toAbsolutePath();
    }

    /**
     * 把统计写入文件。
     *
     * @param stats 要导出的实例（服务端或客户端）
     * @param limit 每个排行最多导出多少条
     * @return 实际写入的文件路径
     */
    public static Path export(NetworkStatistics stats, int limit) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        Path directory = FabricLoader.getInstance().getGameDir().resolve(OUTPUT_DIR);
        Files.createDirectories(directory);

        Path file = directory.resolve(stats.getSide() + "_" + now.format(FILE_STAMP) + ".json");
        JsonObject root = build(stats, limit, now);

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        return file;
    }

    /** 组装导出用的 JSON，便于命令直接复用或测试。 */
    public static JsonObject build(NetworkStatistics stats, int limit, LocalDateTime now) {
        JsonObject root = new JsonObject();

        JsonObject meta = new JsonObject();
        meta.addProperty("side", stats.getSide());
        meta.addProperty("timestamp", now.format(HUMAN_STAMP));
        meta.addProperty("recording", stats.isRecording());
        meta.addProperty("recorded_since", formatStart(stats.getRecordingStartedAt()));
        meta.addProperty("limit", limit);
        meta.addProperty("tracked_types", stats.getTrackedTypeCount());
        meta.addProperty("direction_note",
                "outbound = 本端发出，inbound = 本端收到；每个排行按各自维度排序，行内同时给出两个方向的计数。"
                        + "以 CCA_ 开头的条目是 CCA 组件同步，按组件 key 分列。");
        meta.addProperty("scope_note",
                "只统计自定义载荷包，原版 Minecraft 包不计入；HTTP 与 SQL 流量另见 traffic_channels。");
        FabricLoader.getInstance().getModContainer("starrailexpress")
                .ifPresent(container -> meta.addProperty("mod_version", container.getMetadata().getVersion().getFriendlyString()));
        root.add("meta", meta);

        JsonObject global = new JsonObject();
        global.addProperty("outbound_packets", stats.getOutboundPackets());
        global.addProperty("outbound_bytes", stats.getOutboundBytes());
        global.addProperty("inbound_packets", stats.getInboundPackets());
        global.addProperty("inbound_bytes", stats.getInboundBytes());
        global.addProperty("average_packet_size", stats.getAveragePacketSize());
        global.addProperty("total_bytes_including_channels", stats.getTotalBytes());
        root.add("global_stats", global);

        root.add("traffic_channels", trafficChannels(stats, limit));

        // 字节数可信度：实测与回退估算各占多少，避免再次出现“看不出字节数是假的”情况。
        JsonObject sizes = new JsonObject();
        sizes.addProperty("measured", stats.getMeasuredSizeCount());
        sizes.addProperty("fallback_estimated", stats.getFallbackSizeCount());
        sizes.addProperty("note", "自定义载荷为真实序列化字节数（不含外层包 id 与类型标识）；"
                + "fallback_estimated 为无法序列化时按 " + NetworkUtils.FALLBACK_SIZE + " 字节估算的包数");
        root.add("size_measurement", sizes);

        root.add("outbound_rankings_by_count", ranking(stats.topBy(true, false, limit)));
        root.add("outbound_rankings_by_bytes", ranking(stats.topBy(true, true, limit)));
        root.add("outbound_rankings_by_avg_size", ranking(stats.topByAverageSize(true, limit)));
        root.add("inbound_rankings_by_count", ranking(stats.topBy(false, false, limit)));
        root.add("inbound_rankings_by_bytes", ranking(stats.topBy(false, true, limit)));
        root.add("inbound_rankings_by_avg_size", ranking(stats.topByAverageSize(false, limit)));

        JsonArray players = new JsonArray();
        for (Map.Entry<String, PlayerPacketStats> entry : stats.getPlayerStats().entrySet()) {
            PlayerPacketStats playerStats = entry.getValue();
            JsonObject player = new JsonObject();
            player.addProperty("player_name", entry.getKey());
            player.addProperty("outbound_packets", playerStats.getOutboundPackets());
            player.addProperty("outbound_bytes", playerStats.getOutboundBytes());
            player.addProperty("inbound_packets", playerStats.getInboundPackets());
            player.addProperty("inbound_bytes", playerStats.getInboundBytes());

            JsonArray types = new JsonArray();
            for (Map.Entry<String, PacketStats> typeEntry : playerStats.getPacketTypeStats().entrySet()) {
                JsonObject type = new JsonObject();
                type.addProperty("packet_type", typeEntry.getKey());
                writeCounters(type, typeEntry.getValue());
                types.add(type);
            }
            player.add("packet_type_stats", types);
            players.add(player);
        }
        root.add("player_stats", players);

        return root;
    }

    /**
     * HTTP 与 SQL 两条通道：开关状态、收发次数与字节，以及按字节倒序的端点明细。
     * 字节口径见文件末尾的 note 字段。
     */
    private static JsonObject trafficChannels(NetworkStatistics stats, int limit) {
        JsonObject channels = new JsonObject();
        for (TrafficChannel channel : TrafficChannel.values()) {
            ChannelTrafficStats traffic = channel.stats(stats);
            JsonObject entry = new JsonObject();
            entry.addProperty("recording", channel.isRecording(stats));
            entry.addProperty("interactions", traffic.getOutboundCount());
            entry.addProperty("outbound_bytes", traffic.getOutboundBytes());
            entry.addProperty("inbound_bytes", traffic.getInboundBytes());
            entry.addProperty("average_size", traffic.getOutbound().getAverageSize());
            entry.addProperty("endpoints", traffic.getEndpointCount());
            entry.add("top_endpoints_by_outbound_bytes", endpointRanking(traffic, true, limit));
            entry.add("top_endpoints_by_inbound_bytes", endpointRanking(traffic, false, limit));
            channels.add(channel.argument(), entry);
        }
        channels.addProperty("note",
                "HTTP: 发出 = URL 字节 + 请求体字节，收到 = 响应体字节；"
                        + "SQL: 发出 = 语句文本 + 绑定参数，收到 = 结果集列值（写入类语句只计次数、收到的字节为 0）。"
                        + "两者都不含请求头/响应头、TLS 与协议开销，属于下界；"
                        + "端点标签里的 {uuid}/{token} 是路径中玩家 UUID 与随机令牌的占位符。");
        return channels;
    }

    private static JsonArray endpointRanking(ChannelTrafficStats traffic, boolean outbound, int limit) {
        JsonArray array = new JsonArray();
        List<Map.Entry<String, PacketStats>> entries = traffic.topEndpoints(outbound, true, limit);
        for (int i = 0; i < entries.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("rank", i + 1);
            row.addProperty("endpoint", entries.get(i).getKey());
            writeCounters(row, entries.get(i).getValue());
            array.add(row);
        }
        return array;
    }

    private static JsonArray ranking(List<NetworkStatistics.PacketTypeStats> entries) {
        JsonArray array = new JsonArray();
        for (int i = 0; i < entries.size(); i++) {
            NetworkStatistics.PacketTypeStats typeStats = entries.get(i);
            JsonObject row = new JsonObject();
            row.addProperty("rank", i + 1);
            row.addProperty("packet_id", typeStats.getId());
            // 排行同时给出两个方向，便于对照。
            JsonObject outbound = new JsonObject();
            writeCounters(outbound, typeStats.getOutbound());
            JsonObject inbound = new JsonObject();
            writeCounters(inbound, typeStats.getInbound());
            row.add("outbound", outbound);
            row.add("inbound", inbound);
            array.add(row);
        }
        return array;
    }

    private static void writeCounters(JsonObject target, PacketStats stats) {
        target.addProperty("count", stats.getCount());
        target.addProperty("total_size", stats.getTotalSize());
        target.addProperty("avg_size", stats.getAverageSize());
        target.addProperty("max_size", stats.getMaxSize());
        target.addProperty("min_size", stats.getMinSize());
    }

    private static String formatStart(long epochMillis) {
        if (epochMillis <= 0L) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(HUMAN_STAMP);
    }
}
