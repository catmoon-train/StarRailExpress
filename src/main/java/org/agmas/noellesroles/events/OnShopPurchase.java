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
