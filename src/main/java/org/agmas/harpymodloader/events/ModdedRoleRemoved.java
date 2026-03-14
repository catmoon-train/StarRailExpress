package org.agmas.harpymodloader.events;


import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ModdedRoleRemoved {

    Event<ModdedRoleRemoved> EVENT = createArrayBacked(ModdedRoleRemoved.class, listeners -> (player, role) -> {
        for (ModdedRoleRemoved listener : listeners) {
            listener.removeModdedRole(player, role);
        }
    });

    void removeModdedRole(Player player, SRERole role);
}