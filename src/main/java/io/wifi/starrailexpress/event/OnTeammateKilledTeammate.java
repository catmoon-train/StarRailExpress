package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnTeammateKilledTeammate {
    Event<OnTeammateKilledTeammate> EVENT = createArrayBacked(OnTeammateKilledTeammate.class,
            listeners -> (victim, killer, isInnocent, deathReason) -> {
                for (OnTeammateKilledTeammate listener : listeners) {
                    listener.playerKilled(victim, killer, isInnocent, deathReason);
                }
            });

    void playerKilled(ServerPlayer victim, ServerPlayer killer, boolean isInnocent, ResourceLocation deathReason);
}