package io.wifi.starrailexpress.network.cy.client;

import io.wifi.starrailexpress.network.cy.model.PlayerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据合并管理器 - 处理多服务器间的数据同步和冲突解决
 * 可选功能：使用 enableMergeSynchronization() 启用多服务器同步
 */
public class MergeDataManager {
    private static final Logger logger = LoggerFactory.getLogger(MergeDataManager.class);
    
    private Map<String, Long> versionCache; // 数据版本缓存
    private AtomicLong localVersionCounter; // 本地版本计数器
    private List<DataAccessClient> connectedServers; // 连接的其他服务器
    private boolean enableMergeSync; // 是否启用merge同步
    
    public MergeDataManager() {
        this.versionCache = new ConcurrentHashMap<>();
        this.localVersionCounter = new AtomicLong(System.currentTimeMillis());
        this.connectedServers = new ArrayList<>();
        this.enableMergeSync = false;
    }
    
    /**
     * 启用merge同步功能
     */
    public void enableMergeSynchronization() {
        this.enableMergeSync = true;
        logger.info("已启用Merge同步功能");
    }
    
    /**
     * 禁用merge同步功能
     */
    public void disableMergeSynchronization() {
        this.enableMergeSync = false;
        logger.info("已禁用Merge同步功能");
    }
    
    /**
     * 添加连接的服务器
     */
    public void addConnectedServer(DataAccessClient serverClient) {
        if (!connectedServers.contains(serverClient)) {
            connectedServers.add(serverClient);
            logger.info("已添加连接服务器: {}:{}", serverClient.getHost(), serverClient.getPort());
        }
    }
    
    /**
     * 移除连接的服务器
     */
    public void removeConnectedServer(DataAccessClient serverClient) {
        connectedServers.remove(serverClient);
        logger.info("已移除连接服务器: {}:{}", serverClient.getHost(), serverClient.getPort());
    }
    
    /**
     * 获取数据版本号
     */
    public long getDataVersion(String dataId) {
        return versionCache.getOrDefault(dataId, 0L);
    }
    
    /**
     * 更新数据版本号
     */
    public void updateDataVersion(String dataId, long version) {
        versionCache.put(dataId, version);
        logger.debug("更新数据版本: {} -> {}", dataId, version);
    }
    
    /**
     * 生成新的本地版本号
     */
    public long generateLocalVersion() {
        return localVersionCounter.incrementAndGet();
    }
    
    /**
     * 合并保存玩家数据（带版本控制）
     */
    public boolean savePlayerDataWithMerge(DataAccessClient client, PlayerData player) {
        try {
            // 获取当前服务器上的最新数据
            PlayerData serverPlayer = client.getPlayerData(player.getUuid());
            long serverVersion = getDataVersion(player.getUuid());
            
            // 如果服务器上没有数据，直接保存
            if (serverPlayer == null) {
                long newVersion = generateLocalVersion();
                player.setCustomData(addVersionInfo(player.getCustomData(), newVersion));
                boolean result = client.savePlayerData(player);
                if (result) {
                    updateDataVersion(player.getUuid(), newVersion);
                    syncToOtherServers(client, player, newVersion);
                }
                return result;
            }
            
            // 解析版本信息
            long localVersion = extractVersionFromData(player.getCustomData());
            long currentServerVersion = extractVersionFromData(serverPlayer.getCustomData());
            
            // 版本冲突检测
            if (localVersion < currentServerVersion) {
                logger.warn("检测到版本冲突，本地版本{} < 服务器版本{}，执行合并操作", 
                           localVersion, currentServerVersion);
                
                // 执行数据合并
                PlayerData mergedPlayer = mergePlayerData(serverPlayer, player);
                long mergedVersion = generateLocalVersion();
                mergedPlayer.setCustomData(addVersionInfo(mergedPlayer.getCustomData(), mergedVersion));
                
                boolean result = client.savePlayerData(mergedPlayer);
                if (result) {
                    updateDataVersion(player.getUuid(), mergedVersion);
                    syncToOtherServers(client, mergedPlayer, mergedVersion);
                }
                return result;
            } else {
                // 版本一致或本地版本更新，直接保存
                long newVersion = generateLocalVersion();
                player.setCustomData(addVersionInfo(player.getCustomData(), newVersion));
                boolean result = client.savePlayerData(player);
                if (result) {
                    updateDataVersion(player.getUuid(), newVersion);
                    syncToOtherServers(client, player, newVersion);
                }
                return result;
            }
            
        } catch (Exception e) {
            logger.error("合并保存玩家数据失败", e);
            return false;
        }
    }
    
    /**
     * 合并两个玩家数据
     */
    private PlayerData mergePlayerData(PlayerData serverData, PlayerData localData) {
        PlayerData merged = new PlayerData();
        merged.setUuid(localData.getUuid());
        merged.setUsername(localData.getUsername());
        
        // 合并基本信息 - 选择最新的
        if (serverData.getLastLogin() != null && localData.getLastLogin() != null) {
            long serverTime = Long.parseLong(serverData.getLastLogin());
            long localTime = Long.parseLong(localData.getLastLogin());
            merged.setLastLogin(serverTime > localTime ? serverData.getLastLogin() : localData.getLastLogin());
        } else {
            merged.setLastLogin(serverData.getLastLogin() != null ? serverData.getLastLogin() : localData.getLastLogin());
        }
        
        // 合并游戏时间 - 取最大值
        merged.setPlaytimeMinutes(Math.max(serverData.getPlaytimeMinutes(), localData.getPlaytimeMinutes()));
        
        // 合并IP地址
        merged.setLastIp(serverData.getLastIp() != null ? serverData.getLastIp() : localData.getLastIp());
        
        // 合并自定义数据
        merged.setCustomData(serverData.getCustomData() != null ? serverData.getCustomData() : localData.getCustomData());
        
        return merged;
    }
    
    /**
     * 在自定义数据中添加版本信息
     */
    private String addVersionInfo(String customData, long version) {
        try {
            if (customData == null || customData.isEmpty()) {
                return "{\"__version__\":" + version + "}";
            }
            return "{\"data\":\"" + customData + "\",\"__version__\":" + version + "}";
        } catch (Exception e) {
            return customData;
        }
    }
    
    /**
     * 从自定义数据中提取版本信息
     */
    private long extractVersionFromData(String customData) {
        try {
            if (customData == null || customData.isEmpty() || !customData.contains("__version__")) 
                return 0L;
            
            int startIndex = customData.indexOf("__version__\":") + 12;
            int endIndex = customData.indexOf(",", startIndex);
            if (endIndex == -1) endIndex = customData.indexOf("}", startIndex);
            if (endIndex != -1) {
                return Long.parseLong(customData.substring(startIndex, endIndex).trim());
            }
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
    
    /**
     * 同步数据到其他连接的服务器
     */
    private void syncToOtherServers(DataAccessClient sourceClient, PlayerData player, long version) {
        if (!enableMergeSync || connectedServers.isEmpty()) return;
        
        logger.debug("开始同步数据到{}个连接的服务器", connectedServers.size());
        
        for (DataAccessClient server : connectedServers) {
            try {
                if (server != sourceClient && server.isConnected()) {
                    // 创建带有版本信息的数据副本
                    PlayerData syncPlayer = new PlayerData(player.getUuid(), player.getUsername());
                    syncPlayer.setPlaytimeMinutes(player.getPlaytimeMinutes());
                    syncPlayer.setLastLogin(player.getLastLogin());
                    syncPlayer.setLastIp(player.getLastIp());
                    syncPlayer.setCustomData(addVersionInfo(player.getCustomData(), version));
                    
                    boolean result = server.savePlayerData(syncPlayer);
                    if (result) {
                        logger.debug("成功同步到服务器: {}:{}", server.getHost(), server.getPort());
                    } else {
                        logger.warn("同步到服务器失败: {}:{}", server.getHost(), server.getPort());
                    }
                }
            } catch (Exception e) {
                logger.error("同步到服务器时发生错误: {}:{}", server.getHost(), server.getPort(), e);
            }
        }
    }
    
    /**
     * 获取连接的服务器数量
     */
    public int getConnectedServerCount() {
        return connectedServers.size();
    }
}
