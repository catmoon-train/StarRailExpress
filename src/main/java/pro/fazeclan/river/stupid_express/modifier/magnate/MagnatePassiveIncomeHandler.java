package pro.fazeclan.river.stupid_express.modifier.magnate;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerShopComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;

import pro.fazeclan.river.stupid_express.constants.SEModifiers;

public class MagnatePassiveIncomeHandler {

    private static final int PASSIVE_INCOME_INTERVAL_TICKS = 1200; // 60 seconds = 1200 ticks
    private static final int PASSIVE_INCOME_AMOUNT = 25; // 25 coins

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(MagnatePassiveIncomeHandler::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();

        // Only process every 60 seconds
        if (gameTime % PASSIVE_INCOME_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StarGameWorldComponent gameWorld = StarGameWorldComponent.KEY.get(player.serverLevel());
            WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(player.serverLevel());

            // Check if player has magnate modifier
            if (!modifierComponent.isModifier(player, SEModifiers.MAGNATE)) {
                continue;
            }

            // Get player's role
            Role role = gameWorld.getRole(player);
            if (role == null) {
                continue;
            }

            StarPlayerShopComponent shop = StarPlayerShopComponent.KEY.get(player);
            shop.addToBalance(PASSIVE_INCOME_AMOUNT);
            shop.sync();
        }
    }
}
