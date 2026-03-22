package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnShieldBroken {

    /**
     * On shiled broke.
     * (Player victim, Player killer)
     */
    Event<OnShieldBroken> EVENT = createArrayBacked(OnShieldBroken.class,
            listeners -> (a, b) -> {
                for (OnShieldBroken listener : listeners) {
                    listener.onShieldBroken(a, b);
                }
            });

    void onShieldBroken(Player victim, Player killer);
}
