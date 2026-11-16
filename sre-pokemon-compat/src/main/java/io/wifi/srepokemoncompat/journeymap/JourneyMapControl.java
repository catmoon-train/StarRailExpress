package io.wifi.srepokemoncompat.journeymap;

import net.fabricmc.loader.api.FabricLoader;

/** 可选 JourneyMap 的状态切换桥接，不引入目标模组编译依赖。 */
public interface JourneyMapControl {
    void sre$stopMapping();

    static void stopIfInstalled() {
        if (!FabricLoader.getInstance().isModLoaded("journeymap")) return;
        try {
            Object client = Class.forName("journeymap.client.JourneymapClient")
                    .getMethod("getInstance").invoke(null);
            if (client != null) ((JourneyMapControl) client).sre$stopMapping();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot stop JourneyMap after pokmen was disabled", error);
        }
    }
}
