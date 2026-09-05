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

import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.role_data.innocence.TomatoHeadRoleData;

public class TomatoHeadClientHandle {

    private static boolean forcingTomatoCamera;

    public static void register() {
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> {
            if (isLocalTomatoForm(localPlayer)) {
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickCamera(client));
    }

    private static void tickCamera(Minecraft client) {
        LocalPlayer player = client.player;
        if (isLocalTomatoForm(player)) {
            forcingTomatoCamera = true;
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (forcingTomatoCamera) {
            forcingTomatoCamera = false;
            client.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static boolean isLocalTomatoForm(LocalPlayer localPlayer) {
        return localPlayer != null && TomatoHeadRoleData.isTomatoForm(localPlayer);
    }
}
