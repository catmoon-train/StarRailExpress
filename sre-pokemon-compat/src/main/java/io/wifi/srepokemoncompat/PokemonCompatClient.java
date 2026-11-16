package io.wifi.srepokemoncompat;

import io.wifi.srepackcompat.cobblemon.CobblemonClientGate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** 客户端只接受本次连接同步的宝可梦开关。 */
public final class PokemonCompatClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        if (loader.isModLoaded("cobblemon") || loader.isModLoaded("mega_showdown")
                || loader.isModLoaded("journeymap")) CobblemonClientGate.initialize();
    }
}
