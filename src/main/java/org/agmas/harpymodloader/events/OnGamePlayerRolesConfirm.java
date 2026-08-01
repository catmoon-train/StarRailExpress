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

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnGamePlayerRolesConfirm {

    Event<OnGamePlayerRolesConfirm> EVENT = createArrayBacked(OnGamePlayerRolesConfirm.class,
            listeners -> (serverWorld, roleAssignments) -> {
                for (OnGamePlayerRolesConfirm listener : listeners) {
                    listener.beforeAssignRole(serverWorld, roleAssignments);
                }
            });

    void beforeAssignRole(ServerLevel serverWorld, Map<Player, SRERole> roleAssignments);
}