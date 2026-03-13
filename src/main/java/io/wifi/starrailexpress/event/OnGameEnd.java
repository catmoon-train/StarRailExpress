package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;

public interface OnGameEnd {

    /**
     * Callback for determining whether an {@link ItemStack} should drop when player
     * died
     */
    Event<OnGameEnd> EVENT = createArrayBacked(OnGameEnd.class,
            listeners -> (serverLevel, gameWorldComponent) -> {
                for (OnGameEnd listener : listeners) {
                    listener.onGameEnd(serverLevel, gameWorldComponent);
                }
            });

    void onGameEnd(ServerLevel serverLevel, StarGameWorldComponent gameWorldComponent);
}
