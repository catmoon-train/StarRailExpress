/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pro.fazeclan.river.stupid_express.modifier.magnate;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
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
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.serverLevel());
            WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(player.serverLevel());

            // Check if player has magnate modifier
            if (!modifierComponent.isModifier(player, SEModifiers.MAGNATE)) {
                continue;
            }

            // Get player's role
            SRERole role = gameWorld.getRole(player);
            if (role == null) {
                continue;
            }

            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            shop.addToBalance(PASSIVE_INCOME_AMOUNT);
            shop.sync();
        }
    }
}
