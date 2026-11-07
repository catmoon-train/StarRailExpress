package io.wifi.srepackcompat;

import io.wifi.srepackcompat.config.ChlorideOptionPolicy;
import net.fabricmc.loader.api.FabricLoader;
import io.wifi.srepackcompat.startup.StartupClassCache;
import io.wifi.srepackcompat.startup.NeighbourTableFactory;
import io.wifi.srepackcompat.render.RenderTargetCleanup;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** 加载层交接后释放启动专用数据，不让临时索引驻留整个游戏会话。 */
public final class SrePackCompatClient implements ClientModInitializer {
    private boolean finished;

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("gpumemleakfix")) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> RenderTargetCleanup.tick());
        }
        if (FabricLoader.getInstance().isModLoaded("chloride")) {
            ChlorideOptionPolicy.enforce();
            ClientTickEvents.START_CLIENT_TICK.register(client -> ChlorideOptionPolicy.enforce());
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!finished && client.getOverlay() == null && client.screen != null) {
                finished = true;
                NeighbourTableFactory.release();
                io.wifi.srepackcompat.startup.SreModelLookup.finishStartup();
                StartupClassCache.finish("first-ready-tick");
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            NeighbourTableFactory.release();
            io.wifi.srepackcompat.startup.SreModelLookup.finishStartup();
            StartupClassCache.finish("client-stopping");
        });
    }
}
