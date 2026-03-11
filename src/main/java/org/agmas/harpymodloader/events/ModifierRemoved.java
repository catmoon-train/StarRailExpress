package org.agmas.harpymodloader.events;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.modifiers.Modifier;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ModifierRemoved {

    Event<ModifierRemoved> EVENT = createArrayBacked(ModifierRemoved.class, listeners -> (player, modifer) -> {
        for (ModifierRemoved listener : listeners) {
            listener.removeModifier(player, modifer);
        }
    });

    void removeModifier(Player player, Modifier modifier);
}