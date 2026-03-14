package org.agmas.harpymodloader.events;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import java.util.Map;

public interface OnGamePlayerRolesConfirm {

    Event<OnGamePlayerRolesConfirm> EVENT = createArrayBacked(OnGamePlayerRolesConfirm.class,
            listeners -> (serverWorld, roleAssignments) -> {
                for (OnGamePlayerRolesConfirm listener : listeners) {
                    listener.beforeAssignRole(serverWorld, roleAssignments);
                }
            });

    void beforeAssignRole(ServerLevel serverWorld, Map<Player, SRERole> roleAssignments);
}