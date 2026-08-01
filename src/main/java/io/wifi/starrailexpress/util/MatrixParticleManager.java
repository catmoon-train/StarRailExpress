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

package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public interface MatrixParticleManager {
    static Vec3 muzzlePosForPlayer$get(Player playerEntity) {
        Vec3 pos = SREClient.particleMap.getOrDefault(playerEntity, null);
        SREClient.particleMap.remove(playerEntity);
        return pos;
    }

    static void muzzlePosForPlayer$set(Player playerEntity, Vec3 vec3d) {
        SREClient.particleMap.put(playerEntity, vec3d);
    }
}
