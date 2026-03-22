package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.cca.NetworkStatsComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class NetworkStatistics {
    private static final Logger LOGGER = LoggerFactory.getLogger("TMM-NetworkStats");
    private static final NetworkStatistics INSTANCE = new NetworkStatistics();
    
    // 统计数据
    private final AtomicLong totalPacketsSent = new AtomicLong(0);
    private final AtomicLong totalPacketsReceived = new AtomicLong(0);
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    
    // 按包类型统计
    private final ConcurrentHashMap<String, NetworkStatsComponent.PacketStats> serverPacketStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NetworkStatsComponent.PacketStats> clientPacketStats = new ConcurrentHashMap<>();
    
    // 按玩家统计
    private final ConcurrentHashMap<String, NetworkStatsComponent.PlayerPacketStats> playerPacketStats = new ConcurrentHashMap<>();
    
    public static NetworkStatistics getInstance() {
        return INSTANCE;
    }
    
    public void initialize() {
        // 注册服务器端发送包监听器
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // 当玩家加入时，可以添加额外的初始化逻辑
        });
        
        // 通过事件总线注册包监听器
        registerPacketListeners();
        
        LOGGER.info("Network Statistics initialized");
    }
    
    private void registerPacketListeners() {
        // 由于Fabric API没有直接的包拦截器，我们需要在每个包的发送/接收处手动添加统计
        // 这里提供一个通用的工具方法，供其他地方调用进行统计
    }
    
    public void recordPacketSend(ResourceLocation packetId, long size, PacketFlow flow) {
        recordPacketSend(packetId.toString(), size, flow, null);
    }
    
    public void recordPacketSend(String packetId, long size, PacketFlow flow) {
        recordPacketSend(packetId, size, flow, null);
    }
    
    // 新增：支持记录发包对象
    public void recordPacketSend(ResourceLocation packetId, long size, PacketFlow flow, String targetPlayer) {
        recordPacketSend(packetId.toString(), size, flow, targetPlayer);
    }
    
    // 新增：支持记录发包对象
    public void recordPacketSend(String packetId, long size, PacketFlow flow, String targetPlayer) {
        totalPacketsSent.incrementAndGet();
        totalBytesSent.addAndGet(size);
        
        if (flow == PacketFlow.SERVERBOUND) {
            serverPacketStats.computeIfAbsent(packetId, k -> new NetworkStatsComponent.PacketStats()).update(size);
        } else {
            clientPacketStats.computeIfAbsent(packetId, k -> new NetworkStatsComponent.PacketStats()).update(size);
        }
        
        // 如果指定了目标玩家，则更新玩家统计
        if (targetPlayer != null) {
            NetworkStatsComponent.PlayerPacketStats playerStats = 
                playerPacketStats.computeIfAbsent(targetPlayer, k -> new NetworkStatsComponent.PlayerPacketStats());
            playerStats.incrementPacketsSent(packetId, size);
        }
    }
    
    public void recordPacketReceive(ResourceLocation packetId, long size, PacketFlow flow) {
        recordPacketReceive(packetId.toString(), size, flow, null);
    }
    
    public void recordPacketReceive(String packetId, long size, PacketFlow flow) {
        recordPacketReceive(packetId, size, flow, null);
    }
    
    // 新增：支持记录发包对象
    public void recordPacketReceive(ResourceLocation packetId, long size, PacketFlow flow, String sourcePlayer) {
        recordPacketReceive(packetId.toString(), size, flow, sourcePlayer);
    }
    
    // 新增：支持记录发包对象
    public void recordPacketReceive(String packetId, long size, PacketFlow flow, String sourcePlayer) {
        totalPacketsReceived.incrementAndGet();
        totalBytesReceived.addAndGet(size);
        
        if (flow == PacketFlow.SERVERBOUND) {
            serverPacketStats.computeIfAbsent(packetId, k -> new NetworkStatsComponent.PacketStats()).update(size);
        } else {
            clientPacketStats.computeIfAbsent(packetId, k -> new NetworkStatsComponent.PacketStats()).update(size);
        }
        
        // 如果指定了源玩家，则更新玩家统计
        if (sourcePlayer != null) {
            NetworkStatsComponent.PlayerPacketStats playerStats = 
                playerPacketStats.computeIfAbsent(sourcePlayer, k -> new NetworkStatsComponent.PlayerPacketStats());
            playerStats.incrementPacketsReceived(packetId, size);
        }
    }
    
    // 获取全局统计信息
    public long getTotalPacketsSent() {
        return totalPacketsSent.get();
    }
    
    public long getTotalPacketsReceived() {
        return totalPacketsReceived.get();
    }
    
    public long getTotalBytesSent() {
        return totalBytesSent.get();
    }
    
    public long getTotalBytesReceived() {
        return totalBytesReceived.get();
    }
    
    public double getAveragePacketSize() {
        long totalPackets = getTotalPacketsSent() + getTotalPacketsReceived();
        long totalBytes = getTotalBytesSent() + getTotalBytesReceived();
        return totalPackets > 0 ? (double) totalBytes / totalPackets : 0.0;
    }
    
    // 获取特定包类型的统计信息
    public NetworkStatsComponent.PacketStats getServerPacketStats(String packetId) {
        return serverPacketStats.getOrDefault(packetId, new NetworkStatsComponent.PacketStats());
    }
    
    public NetworkStatsComponent.PacketStats getClientPacketStats(String packetId) {
        return clientPacketStats.getOrDefault(packetId, new NetworkStatsComponent.PacketStats());
    }
    
    // 获取玩家统计信息
    public NetworkStatsComponent.PlayerPacketStats getPlayerPacketStats(String playerName) {
        return playerPacketStats.getOrDefault(playerName, new NetworkStatsComponent.PlayerPacketStats());
    }
    
    // 获取所有玩家统计信息
    public ConcurrentHashMap<String, NetworkStatsComponent.PlayerPacketStats> getAllPlayerPacketStats() {
        return playerPacketStats;
    }
    
    // 获取所有服务器包统计信息
    public ConcurrentHashMap<String, NetworkStatsComponent.PacketStats> getAllServerPacketStats() {
        return serverPacketStats;
    }
    
    // 获取所有客户端包统计信息
    public ConcurrentHashMap<String, NetworkStatsComponent.PacketStats> getAllClientPacketStats() {
        return clientPacketStats;
    }
    
    // 新增：获取按发送包数量排行的包类型
    public List<String> getTopPacketsByCount(int limit, boolean isServerBound) {
        ConcurrentHashMap<String, NetworkStatsComponent.PacketStats> statsMap = 
            isServerBound ? serverPacketStats : clientPacketStats;
            
        return statsMap.entrySet().stream()
            .sorted(Map.Entry.<String, NetworkStatsComponent.PacketStats>comparingByValue(
                Comparator.comparingLong(packetStats -> packetStats.count)).reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    // 新增：获取按发送字节数排行的包类型
    public List<String> getTopPacketsByBytes(int limit, boolean isServerBound) {
        ConcurrentHashMap<String, NetworkStatsComponent.PacketStats> statsMap = 
            isServerBound ? serverPacketStats : clientPacketStats;
            
        return statsMap.entrySet().stream()
            .sorted(Map.Entry.<String, NetworkStatsComponent.PacketStats>comparingByValue(
                Comparator.comparingLong(packetStats -> packetStats.totalSize)).reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    // 新增：获取按平均包大小排行的包类型
    public List<String> getTopPacketsByAvgSize(int limit, boolean isServerBound) {
        ConcurrentHashMap<String, NetworkStatsComponent.PacketStats> statsMap = 
            isServerBound ? serverPacketStats : clientPacketStats;
            
        return statsMap.entrySet().stream()
            .filter(entry -> entry.getValue().count > 0) // 确保至少有一个包
            .sorted(Map.Entry.<String, NetworkStatsComponent.PacketStats>comparingByValue(
                Comparator.comparingDouble(packetStats -> packetStats.getAverageSize())).reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    // 重置统计信息
    public void resetStats() {
        totalPacketsSent.set(0);
        totalPacketsReceived.set(0);
        totalBytesSent.set(0);
        totalBytesReceived.set(0);
        serverPacketStats.clear();
        clientPacketStats.clear();
        playerPacketStats.clear();
    }
    
    public void logStats() {
        LOGGER.info("=== Network Statistics ===");
        LOGGER.info("Total packets sent: {}", getTotalPacketsSent());
        LOGGER.info("Total packets received: {}", getTotalPacketsReceived());
        LOGGER.info("Total bytes sent: {}", getTotalBytesSent());
        LOGGER.info("Total bytes received: {}", getTotalBytesReceived());
        LOGGER.info("Average packet size: {:.2f}", getAveragePacketSize());
        LOGGER.info("========================");
    }
}