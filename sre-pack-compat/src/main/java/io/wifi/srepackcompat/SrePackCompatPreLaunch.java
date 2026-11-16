package io.wifi.srepackcompat;

import io.wifi.srepackcompat.config.BundledConfigInstaller;
import io.wifi.srepackcompat.startup.StartupClassCache;
import io.wifi.srepackcompat.startup.ObsoleteModRemoval;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** 启动入口只依赖 Loader/JDK，不提前解析 Minecraft 客户端类。 */
public final class SrePackCompatPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        // 专用服务器只应用生成预算，不能覆盖客户端配置或运行桌面启动清理。
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        ObsoleteModRemoval.check();
        BundledConfigInstaller.install();
        StartupClassCache.install();
    }
}
