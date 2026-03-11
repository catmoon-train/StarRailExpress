package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnPlayerKilledPlayerIdentifier {
    /**
     * Same as OnPlayerKilledPlayer but it provides ResourceLocation
     * instead of OnPlayerKilledPlayer.DeathReason
     */
    Event<OnPlayerKilledPlayerIdentifier> EVENT = createArrayBacked(OnPlayerKilledPlayerIdentifier.class,
            listeners -> (victim, killer, reason) -> {
                for (OnPlayerKilledPlayerIdentifier listener : listeners) {
                    listener.playerKilled(victim, killer, reason);
                }
            });

    void playerKilled(ServerPlayer victim, ServerPlayer killer, ResourceLocation reason);
}