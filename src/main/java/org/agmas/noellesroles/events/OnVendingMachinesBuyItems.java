package org.agmas.noellesroles.events;

import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;

public interface OnVendingMachinesBuyItems {
    Event<OnVendingMachinesBuyItems> EVENT = EventFactory.createArrayBacked(OnVendingMachinesBuyItems.class, listeners -> (player, deathReason) -> {
        for (OnVendingMachinesBuyItems listener : listeners) {
            if (!listener.allowBuy(player, deathReason)) {
                return false;
            }
        }
        return true;
    });

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean allowBuy(Player player, ShopEntry entry);
}
