package io.wifi.starrailexpress.event;


import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnPlayerKilledPlayer {
    public static enum DeathReason {
        GUN_SHOOT,
        KNIFE,
        UNKNOWN,
        OTHER,
        GRENADE,
        BAT,
        POISON,
        ARROW
    }
    Event<OnPlayerKilledPlayer> EVENT = createArrayBacked(OnPlayerKilledPlayer.class, listeners -> (victim, killer, reason) -> {
        for (OnPlayerKilledPlayer listener : listeners) {
            listener.playerKilled(victim, killer,reason);
        }
    });
    
    void playerKilled(ServerPlayer victim, ServerPlayer killer, DeathReason reason);
}