package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.role.ModRoles;

/** 自然精灵伪装成方块时强制第三人称，方便看清自己对齐的格子。 */
public class NatureSpiritClientHandle {

    public static void register() {
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> {
            if (isCamouflagedNatureSpirit(localPlayer)) {
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
    }

    private static boolean isCamouflagedNatureSpirit(LocalPlayer localPlayer) {
        if (localPlayer == null || !EntityDisguise.isDisguised(localPlayer)) {
            return false;
        }
        return SREClient.gameComponent != null
                && SREClient.gameComponent.isRole(localPlayer, ModRoles.NATURE_SPIRIT);
    }
}
