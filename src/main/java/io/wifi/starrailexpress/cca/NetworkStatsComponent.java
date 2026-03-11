package io.wifi.starrailexpress.cca;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkStatsComponent {
    private final Player player;
    private final Map<String, PacketStats> packetStats = new ConcurrentHashMap<>();
    private final Map<String, PlayerPacketStats> playerPacketStats = new ConcurrentHashMap<>(); // 新增：按玩家统计
    private long totalPacketsSent = 0;
    private long totalPacketsReceived = 0;
    private long totalBytesSent = 0;
    private long totalBytesReceived = 0;
    private long sessionStartTicks = 0;

    public NetworkStatsComponent(Player player) {
        this.player = player;
        this.sessionStartTicks = player.level().getGameTime();
    }

    public static class PacketStats {
        public long count = 0;
        public long totalSize = 0;
        public long maxSize = 0;
        public long minSize = Long.MAX_VALUE;

        public void update(long size) {
            count++;
            totalSize += size;
            if (size > maxSize)
                maxSize = size;
            if (size < minSize)
                minSize = size;
        }

        public double getAverageSize() {
            return count > 0 ? (double) totalSize / count : 0.0;
        }
    }

    // 新增：按玩家统计的数据类
    public static class PlayerPacketStats {
        public long packetsSent = 0;
        public long bytesSent = 0;
        public long packetsReceived = 0;
        public long bytesReceived = 0;
        public final Map<String, PacketStats> packetTypeStats = new HashMap<>();

        public void incrementPacketsSent(String packetType, long size) {
            packetsSent++;
            bytesSent += size;
            packetTypeStats.computeIfAbsent(packetType, k -> new PacketStats()).update(size);
        }

        public void incrementPacketsReceived(String packetType, long size) {
            packetsReceived++;
            bytesReceived += size;
            packetTypeStats.computeIfAbsent(packetType, k -> new PacketStats()).update(size);
        }
    }

    public void incrementPacketsSent(String packetId, long size) {
        packetStats.computeIfAbsent(packetId, k -> new PacketStats()).update(size);
        totalPacketsSent++;
        totalBytesSent += size;
    }

    public void incrementPacketsReceived(String packetId, long size) {
        packetStats.computeIfAbsent(packetId, k -> new PacketStats()).update(size);
        totalPacketsReceived++;
        totalBytesReceived += size;
    }

    // 新增：统计发送给特定玩家的数据包
    public void incrementPacketsSentToPlayer(String targetPlayerName, String packetId, long size) {
        PlayerPacketStats stats = playerPacketStats.computeIfAbsent(targetPlayerName, k -> new PlayerPacketStats());
        stats.incrementPacketsSent(packetId, size);
    }

    // 新增：统计从特定玩家接收的数据包
    public void incrementPacketsReceivedFromPlayer(String sourcePlayerName, String packetId, long size) {
        PlayerPacketStats stats = playerPacketStats.computeIfAbsent(sourcePlayerName, k -> new PlayerPacketStats());
        stats.incrementPacketsReceived(packetId, size);
    }

    public PacketStats getPacketStats(String packetId) {
        return packetStats.getOrDefault(packetId, new PacketStats());
    }

    // 新增：获取特定玩家的统计数据
    public PlayerPacketStats getPlayerPacketStats(String playerName) {
        return playerPacketStats.getOrDefault(playerName, new PlayerPacketStats());
    }

    public Map<String, PacketStats> getAllPacketStats() {
        return new HashMap<>(packetStats);
    }

    // 新增：获取所有玩家统计
    public Map<String, PlayerPacketStats> getAllPlayerPacketStats() {
        return new HashMap<>(playerPacketStats);
    }

    public long getTotalPacketsSent() {
        return totalPacketsSent;
    }

    public long getTotalPacketsReceived() {
        return totalPacketsReceived;
    }

    public long getTotalBytesSent() {
        return totalBytesSent;
    }

    public long getTotalBytesReceived() {
        return totalBytesReceived;
    }

    public double getAveragePacketSize() {
        long totalPackets = totalPacketsSent + totalPacketsReceived;
        long totalBytes = totalBytesSent + totalBytesReceived;
        return totalPackets > 0 ? (double) totalBytes / totalPackets : 0.0;
    }

    public long getSessionDurationTicks() {
        return player.level().getGameTime() - sessionStartTicks;
    }

    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        totalPacketsSent = tag.getLong("TotalPacketsSent");
        totalPacketsReceived = tag.getLong("TotalPacketsReceived");
        totalBytesSent = tag.getLong("TotalBytesSent");
        totalBytesReceived = tag.getLong("TotalBytesReceived");
        sessionStartTicks = tag.getLong("SessionStartTicks");

        ListTag statsList = tag.getList("PacketStats", Tag.TAG_COMPOUND);
        for (int i = 0; i < statsList.size(); i++) {
            CompoundTag statTag = statsList.getCompound(i);
            String packetId = statTag.getString("PacketId");
            PacketStats stats = new PacketStats();
            stats.count = statTag.getLong("Count");
            stats.totalSize = statTag.getLong("TotalSize");
            stats.maxSize = statTag.getLong("MaxSize");
            stats.minSize = statTag.getLong("MinSize");
            packetStats.put(packetId, stats);
        }

        // 读取玩家统计数据
        ListTag playerStatsList = tag.getList("PlayerPacketStats", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerStatsList.size(); i++) {
            CompoundTag playerStatTag = playerStatsList.getCompound(i);
            String playerName = playerStatTag.getString("PlayerName");
            PlayerPacketStats playerStats = new PlayerPacketStats();
            playerStats.packetsSent = playerStatTag.getLong("PacketsSent");
            playerStats.bytesSent = playerStatTag.getLong("BytesSent");
            playerStats.packetsReceived = playerStatTag.getLong("PacketsReceived");
            playerStats.bytesReceived = playerStatTag.getLong("BytesReceived");

            // 读取按包类型统计的数据
            ListTag typeStatsList = playerStatTag.getList("TypeStats", Tag.TAG_COMPOUND);
            for (int j = 0; j < typeStatsList.size(); j++) {
                CompoundTag typeStatTag = typeStatsList.getCompound(j);
                String packetType = typeStatTag.getString("PacketType");
                PacketStats typeStats = new PacketStats();
                typeStats.count = typeStatTag.getLong("Count");
                typeStats.totalSize = typeStatTag.getLong("TotalSize");
                typeStats.maxSize = typeStatTag.getLong("MaxSize");
                typeStats.minSize = typeStatTag.getLong("MinSize");
                playerStats.packetTypeStats.put(packetType, typeStats);
            }

            playerPacketStats.put(playerName, playerStats);
        }
    }

    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putLong("TotalPacketsSent", totalPacketsSent);
        tag.putLong("TotalPacketsReceived", totalPacketsReceived);
        tag.putLong("TotalBytesSent", totalBytesSent);
        tag.putLong("TotalBytesReceived", totalBytesReceived);
        tag.putLong("SessionStartTicks", sessionStartTicks);

        ListTag statsList = new ListTag();
        for (Map.Entry<String, PacketStats> entry : packetStats.entrySet()) {
            CompoundTag statTag = new CompoundTag();
            statTag.putString("PacketId", entry.getKey());
            statTag.putLong("Count", entry.getValue().count);
            statTag.putLong("TotalSize", entry.getValue().totalSize);
            statTag.putLong("MaxSize", entry.getValue().maxSize);
            statTag.putLong("MinSize", entry.getValue().minSize);
            statsList.add(statTag);
        }
        tag.put("PacketStats", statsList);

        // 写入玩家统计数据
        ListTag playerStatsList = new ListTag();
        for (Map.Entry<String, PlayerPacketStats> entry : playerPacketStats.entrySet()) {
            CompoundTag playerStatTag = new CompoundTag();
            playerStatTag.putString("PlayerName", entry.getKey());
            playerStatTag.putLong("PacketsSent", entry.getValue().packetsSent);
            playerStatTag.putLong("BytesSent", entry.getValue().bytesSent);
            playerStatTag.putLong("PacketsReceived", entry.getValue().packetsReceived);
            playerStatTag.putLong("BytesReceived", entry.getValue().bytesReceived);

            // 写入按包类型统计的数据
            ListTag typeStatsList = new ListTag();
            for (Map.Entry<String, PacketStats> typeEntry : entry.getValue().packetTypeStats.entrySet()) {
                CompoundTag typeStatTag = new CompoundTag();
                typeStatTag.putString("PacketType", typeEntry.getKey());
                typeStatTag.putLong("Count", typeEntry.getValue().count);
                typeStatTag.putLong("TotalSize", typeEntry.getValue().totalSize);
                typeStatTag.putLong("MaxSize", typeEntry.getValue().maxSize);
                typeStatTag.putLong("MinSize", typeEntry.getValue().minSize);
                typeStatsList.add(typeStatTag);
            }
            playerStatTag.put("TypeStats", typeStatsList);
            playerStatsList.add(playerStatTag);
        }
        tag.put("PlayerPacketStats", playerStatsList);
    }

}