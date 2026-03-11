package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnTrainAreaHaveReseted {

    /**
     * Callback for determining whether an {@link ItemStack} should drop when player
     * died
     */
    Event<OnTrainAreaHaveReseted> EVENT = createArrayBacked(OnTrainAreaHaveReseted.class,
            listeners -> (sl) -> {
                for (OnTrainAreaHaveReseted listener : listeners) {
                    listener.onWorldHaveReseted(sl);
                }
            });

    void onWorldHaveReseted(ServerLevel serverWorld);
}
