package io.wifi.starrailexpress.cca.network;

import io.wifi.starrailexpress.cca.PlayerSkinsComponent;
import io.wifi.starrailexpress.SREConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 皮肤网络同步初始化器
 * 在玩家加入服务器时初始化皮肤同步
 */
public class SkinsNetworkSyncInitializer {
    private static final Logger logger = LoggerFactory.getLogger(SkinsNetworkSyncInitializer.class);

    public static boolean isEnabled = false;
    // 网络服务器配置
    public static String NETWORK_HOST = "exmaple.com";
    public static int NETWORK_PORT = 80;
    public static String NETWORK_KEY = "";

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
            if (SREConfig.itemSkinSyncServerEnabled) {
                setNetworkServer(SREConfig.itemSkinSyncServerHost, SREConfig.itemSkinSyncServerPort,
                        SREConfig.itemSkinSyncServerKey);
                isEnabled = true;
            } else {
                isEnabled = false;
            }
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
            PlayerSkinsComponent skinsComponent = PlayerSkinsComponent.KEY.get(player);
            if (skinsComponent != null) {
                // 初始化网络同步，连接到TCP服务器
                skinsComponent.initializeNetworkSync(NETWORK_HOST, NETWORK_PORT, NETWORK_KEY);

                // 尝试从网络拉取之前保存的皮肤数据
                skinsComponent.pullSkinsFromNetwork();

                logger.info("玩家 {} 的皮肤网络同步已初始化", player.getName().getString());
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
            PlayerSkinsComponent skinsComponent = PlayerSkinsComponent.KEY.get(player);
            if (skinsComponent != null && skinsComponent.isNetworkSyncEnabled()) {
                // 异步执行最后一次同步和断开连接
                skinsComponent.pullSkinsFromNetwork();
                skinsComponent.disableNetworkSync();

                logger.info("玩家 {} 的皮肤网络同步已断开", player.getName().getString());
            }
        } catch (Exception e) {
            logger.error("处理玩家 {} 的皮肤网络同步断开时出错", player.getName().getString(), e);
        }
    }

    /**
     * 设置网络服务器地址
     */
    public static void setNetworkServer(String host, int port, String key) {
        NETWORK_HOST = host;
        NETWORK_PORT = port;
        NETWORK_KEY = key;
        logger.info("皮肤网络服务器已设置: {}:{}", host, port);
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
