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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public interface MatrixParticleManager {
    /**
     * 取枪口位置：优先主手。副手也拿着枪时，副手渲染不会再覆盖主手记录，
     * 避免开火粒子跑到副手/屏幕另一侧。
     */
    static Vec3 muzzlePosForPlayer$get(Player playerEntity) {
        Vec3 pos = SREClient.particleMap.getOrDefault(playerEntity, null);
        SREClient.particleMap.remove(playerEntity);
        if (pos != null)
            return pos;
        pos = SREClient.offhandParticleMap.getOrDefault(playerEntity, null);
        SREClient.offhandParticleMap.remove(playerEntity);
        return pos;
    }

    static void muzzlePosForPlayer$set(Player playerEntity, Vec3 vec3d) {
        muzzlePosForPlayer$set(playerEntity, InteractionHand.MAIN_HAND, vec3d);
    }

    /** 按正在渲染的手分别记录，主手与副手互不覆盖。 */
    static void muzzlePosForPlayer$set(Player playerEntity, InteractionHand hand, Vec3 vec3d) {
        if (hand == InteractionHand.OFF_HAND) {
            SREClient.offhandParticleMap.put(playerEntity, vec3d);
        } else {
            SREClient.particleMap.put(playerEntity, vec3d);
        }
    }
}
