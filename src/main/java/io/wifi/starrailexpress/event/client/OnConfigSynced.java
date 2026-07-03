package io.wifi.starrailexpress.event.client;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnConfigSynced {
    Event<OnConfigSynced> EVENT = createArrayBacked(OnConfigSynced.class, listeners -> (configId, client) -> {
        for (OnConfigSynced listener : listeners) {
            listener.onConfigSynced(configId, client);
        }
        return;
    });

    /**
     * 玩家射击时的回调方法。
     *
     * <p>
     * Callback invoked when a player shoots with a revolver.
     *
     * @param player 射击的玩家 / the player who fired
     * @param target 被击中的目标玩家，可能为 null / the target player who was hit, may be null
     */
    void onConfigSynced(String configId, Minecraft client);
}