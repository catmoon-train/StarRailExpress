package io.wifi.starrailexpress.cca.network;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.sync.MysqlPlayerDataStore;
import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 玩家数据同步初始化器
 * 在玩家加入服务器时初始化 MySQL 同步
 */
public class SkinsNetworkSyncInitializer {
    private static final Logger logger = LoggerFactory.getLogger(SkinsNetworkSyncInitializer.class);

    public static boolean isEnabled = false;
    // MySQL 配置
    public static String NETWORK_HOST = SREConfig.instance().mysqlSyncHost;
    public static int NETWORK_PORT = SREConfig.instance().mysqlSyncPort;
    public static String NETWORK_KEY = SREConfig.instance().mysqlSyncDatabase;

    /**
     * 注册服务器连接事件
     */
    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (isEnabled) {
                ServerPlayer player = handler.getPlayer();
                onPlayerJoin(player);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            setNetworkServer(
                    SREConfig.instance().mysqlSyncHost,
                    SREConfig.instance().mysqlSyncPort,
                    SREConfig.instance().mysqlSyncDatabase);
            MysqlPlayerDataStore.initializeFromConfig();
            isEnabled = MysqlPlayerDataStore.isAvailable()
                    && (SREConfig.instance().itemSkinSyncServerEnabled
                            || SREConfig.instance().progressionSyncServerEnabled);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            isEnabled = false;
            MysqlPlayerDataStore.shutdown();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (isEnabled) {
                ServerPlayer player = handler.getPlayer();
                onPlayerDisconnect(player);
            }
        });
    }

    /**
     * 玩家加入服务器时的处理
     */
    private static void onPlayerJoin(ServerPlayer player) {
        try {
            SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
            SREPlayerProgressionComponent progressionComponent = SREPlayerProgressionComponent.KEY.get(player);
            NameTagInventoryComponent nameTagInventoryComponent = NameTagInventoryComponent.KEY.get(player);
            if (skinsComponent != null && SREConfig.instance().itemSkinSyncServerEnabled) {
                skinsComponent.initializeNetworkSync(NETWORK_HOST, NETWORK_PORT, NETWORK_KEY);
                skinsComponent.pullSkinsFromNetwork();
                logger.info("玩家 {} 的皮肤 MySQL 同步已初始化", player.getName().getString());
            }
            if (progressionComponent != null && SREConfig.instance().progressionSyncServerEnabled) {
                progressionComponent.initializeNetworkSync(NETWORK_HOST, NETWORK_PORT, NETWORK_KEY);
                progressionComponent.pullProgressionFromNetwork();
                progressionComponent.sync();
            }
            if (nameTagInventoryComponent != null && SREConfig.instance().itemSkinSyncServerEnabled) {
                nameTagInventoryComponent.initializeNetworkSync(NETWORK_HOST, NETWORK_PORT, NETWORK_KEY);
                nameTagInventoryComponent.syncFromLinkedServer();
                nameTagInventoryComponent.sync();
            }
        } catch (Exception e) {
            logger.error("初始化玩家 {} 的皮肤网络同步时出错", player.getName().getString(), e);
        }
    }

    /**
     * 玩家断开连接时的处理
     */
    private static void onPlayerDisconnect(ServerPlayer player) {
        try {
            SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
            SREPlayerProgressionComponent progressionComponent = SREPlayerProgressionComponent.KEY.get(player);
            NameTagInventoryComponent nameTagInventoryComponent = NameTagInventoryComponent.KEY.get(player);
            if (skinsComponent != null && skinsComponent.isNetworkSyncEnabled()) {
                skinsComponent.flushNetworkSyncBlocking();
                skinsComponent.disableNetworkSync();
                logger.info("玩家 {} 的皮肤 MySQL 同步已断开", player.getName().getString());
            }
            if (progressionComponent != null && progressionComponent.isNetworkSyncEnabled()) {
                progressionComponent.flushNetworkSyncBlocking();
                progressionComponent.disableNetworkSync();
            }
            if (nameTagInventoryComponent != null && nameTagInventoryComponent.isNetworkSyncEnabled()) {
                nameTagInventoryComponent.flushNetworkSyncBlocking();
                nameTagInventoryComponent.disableNetworkSync();
            }
        } catch (Exception e) {
            logger.error("处理玩家 {} 的 MySQL 数据同步断开时出错", player.getName().getString(), e);
        }
    }

    /**
     * 设置网络服务器地址
     */
    public static void setNetworkServer(String host, int port, String key) {
        NETWORK_HOST = host;
        NETWORK_PORT = port;
        NETWORK_KEY = key;
        logger.info("MySQL 同步配置已设置: {}:{}/{}", host, port, key);
    }

    /**
     * 获取网络服务器主机
     */
    public static String getNetworkHost() {
        return NETWORK_HOST;
    }

    /**
     * 获取网络服务器端口
     */
    public static String getNetworkKey() {
        return NETWORK_KEY;
    }

    /**
     * 获取网络服务器端口
     */
    public static int getNetworkPort() {
        return NETWORK_PORT;
    }
}
