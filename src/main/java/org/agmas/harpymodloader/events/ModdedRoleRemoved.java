/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.harpymodloader.events;


import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ModdedRoleRemoved {

    Event<ModdedRoleRemoved> EVENT = createArrayBacked(ModdedRoleRemoved.class, listeners -> (player, role) -> {
        
        // 先调用 onInit

        if (player instanceof ServerPlayer serverPlayer) {
            RoleMethodDispatcher.onRemoveRole(role, serverPlayer.getServer(), serverPlayer);
        }

        for (ModdedRoleRemoved listener : listeners) {
            listener.removeModdedRole(player, role);
        }
    });

    void removeModdedRole(Player player, SRERole role);
}