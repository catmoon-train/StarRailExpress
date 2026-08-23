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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.role_data.innocence.SaltedFishRoleData;

public class SaltedFishClientHandle {
    public static void register() {
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> {
            if (isLocalSaltedFishActive(localPlayer)) {
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
    }

    private static boolean isLocalSaltedFishActive(LocalPlayer localPlayer) {
        if (localPlayer == null) {
            return false;
        }
        SaltedFishRoleData component = RoleData.getNullable(SaltedFishRoleData.class, localPlayer);
        return component != null && component.isActive();
    }
}
