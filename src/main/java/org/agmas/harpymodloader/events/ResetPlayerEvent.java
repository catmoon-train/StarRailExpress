package org.agmas.harpymodloader.events;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.modifiers.Modifier;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ResetPlayerEvent {

    Event<ResetPlayerEvent> EVENT = createArrayBacked(ResetPlayerEvent.class, listeners -> (player) -> {
        for (ResetPlayerEvent listener : listeners) {
            listener.resetPlayer(player);
        }
    });

    void resetPlayer(Player player);
}