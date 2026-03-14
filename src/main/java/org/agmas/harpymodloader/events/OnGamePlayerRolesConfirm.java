package org.agmas.harpymodloader.events;


import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import java.util.Map;
public interface OnGamePlayerRolesConfirm {

    Event<OnGamePlayerRolesConfirm> EVENT = createArrayBacked(OnGamePlayerRolesConfirm.class, listeners -> (roleAssignments) -> {
        for (OnGamePlayerRolesConfirm listener : listeners) {
            listener.beforeAssignRole(roleAssignments);
        }
    });

    void beforeAssignRole(Map<Player, SRERole> roleAssignments);
}