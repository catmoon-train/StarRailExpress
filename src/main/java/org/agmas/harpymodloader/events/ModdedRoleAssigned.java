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
import org.agmas.noellesroles.init.RoleInitialItems;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface ModdedRoleAssigned {

    Event<ModdedRoleAssigned> EVENT = createArrayBacked(ModdedRoleAssigned.class, listeners -> (player, role) -> {
        // 使用映射表添加初始物品（包括映射表和职业里的getDefaultItems）
        RoleInitialItems.addInitialItemsForRole(player, role);

        // 先调用 onInit

        if (player instanceof ServerPlayer serverPlayer) {
            RoleMethodDispatcher.onInit(role, serverPlayer.getServer(), serverPlayer);
        }

        // 再调用 EVENT
        
        for (ModdedRoleAssigned listener : listeners) {
            listener.assignModdedRole(player, role);
        }
    });

    void assignModdedRole(ServerPlayer player, SRERole role);
}