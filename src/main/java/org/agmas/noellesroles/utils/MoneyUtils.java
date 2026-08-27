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

package org.agmas.noellesroles.utils;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class MoneyUtils {
    public static void setBalance(Player player, int balance) {
        SREPlayerShopComponent.KEY.get(player).setBalance(balance);
    }

    public static void addToBalance(Player player, int balance) {
        SREPlayerShopComponent.KEY.get(player).addToBalance(balance);
    }

    public static int getBalance(Player player) {
        return SREPlayerShopComponent.KEY.get(player).balance;
    }

    public static void setMinigamesTokens(Player player, int balance) {
        SREPlayerMinigameTaskComponent.KEY.get(player).setTokens(balance);
    }

    public static void addToMinigamesTokens(Player player, int balance) {
        SREPlayerMinigameTaskComponent.KEY.get(player).addTokens(balance);
    }

    public static int getMinigamesTokens(Player player) {
        return SREPlayerMinigameTaskComponent.KEY.get(player).getTokens();
    }

    public static boolean hasBalance(ServerPlayer player, int money) {
        return getBalance(player) >= money;
    }
}
