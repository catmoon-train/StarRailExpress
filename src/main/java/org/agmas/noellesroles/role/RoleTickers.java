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

package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import net.minecraft.server.level.ServerPlayer;

public class RoleTickers {
    public static void oldmanTick(ServerPlayer player, SREGameWorldComponent gameWorldComponent){
        var pmc = SREPlayerTaskComponent.KEY.get(player);
        if (!(pmc.tasks.isEmpty())
            && pmc.tasks.getOrDefault(SREPlayerTaskComponent.Task.EXERCISE, null) != null) {
          if (pmc.tasks.get(
              SREPlayerTaskComponent.Task.EXERCISE) instanceof SREPlayerTaskComponent.ExerciseTask et) {
            et.timer = 0;
          }
        }
    }
}
