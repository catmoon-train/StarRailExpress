package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnGameTrueStarted {

    /**
     * Callback for determining whether an {@link ItemStack} should drop when player
     * died
     */
    Event<OnGameTrueStarted> EVENT = createArrayBacked(OnGameTrueStarted.class,
            listeners -> (sl) -> {
                for (OnGameTrueStarted listener : listeners) {
                    listener.onGameTrueStarted(sl);
                }
            });

    void onGameTrueStarted(ServerLevel serverLevel);
}
