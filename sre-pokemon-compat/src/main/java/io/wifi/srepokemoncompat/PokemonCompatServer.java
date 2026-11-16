package io.wifi.srepokemoncompat;

import io.wifi.srepackcompat.cobblemon.CobblemonSpawnBudget;
import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/** 只注册一次服务端负载采样；客户端连接远程服务器时不运行此处的 tick 回调。 */
public final class PokemonCompatServer implements ModInitializer {
    @Override public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) return;
        CobblemonMechanics.initialize();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CobblemonSpawnBudget.resetLoadBudget());
        ServerTickEvents.START_SERVER_TICK.register(server ->
                CobblemonSpawnBudget.observeAverageTickNanos(server.getAverageTickTimeNanos()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CobblemonSpawnBudget.resetLoadBudget());
    }
}
