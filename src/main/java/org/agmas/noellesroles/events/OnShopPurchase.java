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

package org.agmas.noellesroles.events;

import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;

public interface OnShopPurchase {
    Event<OnShopPurchase> EVENT = EventFactory.createArrayBacked(OnShopPurchase.class, listeners -> (player, entry, price) -> {
        for (OnShopPurchase listener : listeners) {
            listener.onPurchase(player, entry, price);
        }
    });

    void onPurchase(Player player, ShopEntry entry, int price);
}
