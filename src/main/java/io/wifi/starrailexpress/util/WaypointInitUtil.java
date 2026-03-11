package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.data.WaypointManager;
import io.wifi.starrailexpress.data.WaypointVisibilityManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class WaypointInitUtil {
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(WaypointInitUtil::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        // 初始化路径点管理器并加载数据
        WaypointManager manager = WaypointManager.get(server);
        manager.loadFromFile();
        
        // 初始化路径点可见性管理器
        WaypointVisibilityManager visibilityManager = WaypointVisibilityManager.get(server);
        // 可选：从保存的数据中恢复可见性状态
    }
}