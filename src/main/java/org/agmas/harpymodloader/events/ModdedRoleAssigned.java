package org.agmas.harpymodloader.events;

import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.init.RoleInitialItems;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ModdedRoleAssigned {

    Event<ModdedRoleAssigned> EVENT = createArrayBacked(ModdedRoleAssigned.class, listeners -> (player, role) -> {
        // 使用映射表添加初始物品（包括映射表和职业里的getDefaultItems）
        RoleInitialItems.addInitialItemsForRole(player, role);

        for (ModdedRoleAssigned listener : listeners) {
            listener.assignModdedRole(player, role);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RoleMethodDispatcher.onInit(role, serverPlayer.getServer(), serverPlayer);
        }
    });

    void assignModdedRole(ServerPlayer player, SRERole role);
}