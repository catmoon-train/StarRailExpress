package io.wifi.starrailexpress.cca.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.network.cy.client.DataAccessClient;
import io.wifi.starrailexpress.network.cy.model.PlayerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 皮肤网络同步管理器
 * 负责将解锁的皮肤数据同步到TCP服务器
 * 所有网络操作异步执行，不阻塞游戏线程
 */
public class SkinsNetworkSyncManager {
    private static final Logger logger = LoggerFactory.getLogger(SkinsNetworkSyncManager.class);
    private static final Gson GSON = new GsonBuilder().create();
    
    // 异步线程池，线程数为2
    private static final ExecutorService executorService = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "SkinSync-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });
    
    private DataAccessClient networkClient;
    private String playerUuid;
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private AtomicBoolean isSyncing = new AtomicBoolean(false);
    
    // 本地缓存，用于追踪已同步的版本
    // private Map<String, Long> syncedVersions = new ConcurrentHashMap<>();
    private long localVersion = System.currentTimeMillis();
    
    // 回调函数
    private Consumer<Boolean> syncCallback;
    private Consumer<Map<String, Object>> fetchCallback;
    
    public SkinsNetworkSyncManager(String playerUuid) {
        this.playerUuid = playerUuid;
    }
    
    /**
     * 设置同步完成回调
     */
    public void setSyncCallback(Consumer<Boolean> callback) {
        this.syncCallback = callback;
    }
    
    /**
     * 设置拉取完成回调
     */
    public void setFetchCallback(Consumer<Map<String, Object>> callback) {
        this.fetchCallback = callback;
    }
    
    /**
     * 初始化网络连接
     * @param host 服务器主机地址
     * @param port 服务器端口
     * @return 连接是否成功
     */
    public boolean initialize(String host, int port) {
        try {
            this.networkClient = new DataAccessClient(host, port);
            if (!networkClient.connect()) {
                logger.warn("无法连接到网络服务器 {}:{}", host, port);
                return false;
            }
            
            if (!networkClient.authenticate()) {
                logger.warn("无法认证网络服务器 {}:{}", host, port);
                networkClient.disconnect();
                return false;
            }
            
            this.isConnected.set(true);
            logger.info("皮肤网络同步管理器已初始化: {}:{}", host, port);
            return true;
        } catch (Exception e) {
            logger.error("初始化网络连接失败", e);
            return false;
        }
    }
    
    /**
     * 同步已解锁的皮肤到服务器（异步）
     * @param equippedSkins 装备的皮肤映射 {itemName -> skinName}
     * @param unlockedSkins 解锁的皮肤映射 {itemName -> {skinName -> isUnlocked}}
     * @return Future 对象，表示异步操作
     */
    public Future<?> syncSkinsToServerAsync(
            Map<String, String> equippedSkins,
            Map<String, Map<String, Boolean>> unlockedSkins) {
        
        return executorService.submit(() -> {
            if (!isConnected.get() || isSyncing.getAndSet(true)) {
                if (syncCallback != null) {
                    syncCallback.accept(false);
                }
                return;
            }
            
            try {
                // 获取或创建玩家数据
                PlayerData playerData = networkClient.getPlayerData(playerUuid);
                if (playerData == null) {
                    playerData = new PlayerData(playerUuid, "unknown");
                    logger.debug("为玩家创建新的网络数据: {}", playerUuid);
                }
                
                // 创建皮肤数据对象
                Map<String, Object> skinData = new HashMap<>();
                skinData.put("equipped", equippedSkins);
                skinData.put("unlocked", unlockedSkins);
                skinData.put("version", this.localVersion);
                skinData.put("timestamp", System.currentTimeMillis());
                
                String skinDataJson = GSON.toJson(skinData);
                
                // 将皮肤数据存储在玩家的自定义数据中
                playerData.setCustomData(skinDataJson);
                playerData.setDataVersion(this.localVersion);
                playerData.setLastModifiedBy("SkinsSync");
                
                // 同步到服务器
                boolean success = networkClient.savePlayerData(playerData);
                
                if (success) {
                    this.localVersion = System.currentTimeMillis();
                    logger.debug("成功同步皮肤数据到服务器，玩家: {}", playerUuid);
                    if (syncCallback != null) {
                        syncCallback.accept(true);
                    }
                } else {
                    logger.warn("同步皮肤数据到服务器失败，玩家: {}", playerUuid);
                    if (syncCallback != null) {
                        syncCallback.accept(false);
                    }
                }
            } catch (Exception e) {
                logger.error("同步皮肤数据时发生错误", e);
                if (syncCallback != null) {
                    syncCallback.accept(false);
                }
            } finally {
                isSyncing.set(false);
            }
        });
    }
    
    /**
     * 从服务器获取玩家的皮肤数据（异步）
     * @return Future 对象，异步操作完成后返回皮肤数据
     */
    public Future<?> fetchSkinsFromServerAsync() {
        return executorService.submit(() -> {
            if (!isConnected.get() || isSyncing.getAndSet(true)) {
                if (fetchCallback != null) {
                    fetchCallback.accept(null);
                }
                return;
            }
            
            try {
                PlayerData playerData = networkClient.getPlayerData(playerUuid);
                if (playerData == null) {
                    logger.debug("服务器上未找到玩家数据: {}", playerUuid);
                    if (fetchCallback != null) {
                        fetchCallback.accept(null);
                    }
                    return;
                }
                
                String customData = playerData.getCustomData();
                if (customData == null || customData.isEmpty()) {
                    logger.debug("玩家没有皮肤数据: {}", playerUuid);
                    if (fetchCallback != null) {
                        fetchCallback.accept(null);
                    }
                    return;
                }
                
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> skinData = GSON.fromJson(customData, Map.class);
                    logger.debug("成功从服务器获取皮肤数据，玩家: {}", playerUuid);
                    if (fetchCallback != null) {
                        fetchCallback.accept(skinData);
                    }
                } catch (Exception e) {
                    logger.warn("解析皮肤数据失败: {}", playerUuid, e);
                    if (fetchCallback != null) {
                        fetchCallback.accept(null);
                    }
                }
            } catch (Exception e) {
                logger.error("从服务器获取皮肤数据时发生错误", e);
                if (fetchCallback != null) {
                    fetchCallback.accept(null);
                }
            } finally {
                isSyncing.set(false);
            }
        });
    }
    
    /**
     * 检查是否有更新的皮肤数据在服务器上
     * @return 如果服务器上的数据更新，返回true
     */
    public boolean hasUpdatesOnServer() {
        if (!isConnected.get()) {
            return false;
        }
        
        try {
            PlayerData playerData = networkClient.getPlayerData(playerUuid);
            if (playerData == null) {
                return false;
            }
            
            long serverVersion = playerData.getDataVersion();
            return serverVersion > this.localVersion;
        } catch (Exception e) {
            logger.debug("检查服务器更新时发生错误", e);
            return false;
        }
    }
    
    /**
     * 断开网络连接
     */
    public void disconnect() {
        if (networkClient != null && isConnected.get()) {
            try {
                networkClient.disconnect();
                isConnected.set(false);
                logger.info("已断开网络连接");
            } catch (Exception e) {
                logger.error("断开连接时发生错误", e);
            }
        }
    }
    
    /**
     * 检查是否已连接到服务器
     */
    public boolean isConnected() {
        return isConnected.get() && networkClient != null;
    }
    
    /**
     * 获取当前的本地版本号
     */
    public long getLocalVersion() {
        return this.localVersion;
    }
    
    /**
     * 设置本地版本号
     */
    public void setLocalVersion(long version) {
        this.localVersion = version;
    }
}
