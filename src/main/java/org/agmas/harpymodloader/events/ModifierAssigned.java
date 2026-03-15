package org.agmas.harpymodloader.events;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.modifiers.SREModifier;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ModifierAssigned {

    Event<ModifierAssigned> EVENT = createArrayBacked(ModifierAssigned.class, listeners -> (player, modifer) -> {
        for (ModifierAssigned listener : listeners) {
            listener.assignModifier(player, modifer);
        }
    });

    void assignModifier(Player player, SREModifier modifier);
}