package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface EarlyKillPlayer {
    Event<EarlyKillPlayer> FIND_KILLER_EVENT = createArrayBacked(EarlyKillPlayer.class,
            listeners -> (victim, killer, reason) -> {
                Player result = null;
                for (EarlyKillPlayer listener : listeners) {
                    result = listener.findTrueKiller(victim, killer, reason);
                    if (result != null)
                        return result;
                }
                return null;
            });

    Player findTrueKiller(Player victim, Player killer, ResourceLocation reason);
}