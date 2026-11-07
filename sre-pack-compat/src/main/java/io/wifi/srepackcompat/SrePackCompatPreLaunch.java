package io.wifi.srepackcompat;

import io.wifi.srepackcompat.config.BundledConfigInstaller;
import io.wifi.srepackcompat.startup.StartupClassCache;
import io.wifi.srepackcompat.startup.ObsoleteModRemoval;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** 启动入口只依赖 Loader/JDK，不提前解析 Minecraft 客户端类。 */
public final class SrePackCompatPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        ObsoleteModRemoval.check();
        BundledConfigInstaller.install();
        StartupClassCache.install();
    }
}
