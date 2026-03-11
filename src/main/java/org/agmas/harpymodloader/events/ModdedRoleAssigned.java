package org.agmas.harpymodloader.events;


import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;
public interface ModdedRoleAssigned {

    Event<ModdedRoleAssigned> EVENT = createArrayBacked(ModdedRoleAssigned.class, listeners -> (player, role) -> {
        for (ModdedRoleAssigned listener : listeners) {
            listener.assignModdedRole(player, role);
            if (player instanceof ServerPlayer serverPlayer) {
                RoleMethodDispatcher.onInit(role,serverPlayer.getServer(), serverPlayer);
            }
        }
    });

    void assignModdedRole(Player player, Role role);
}