package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.game.roles.innocence.salted_fish.SaltedFishPlayerComponent;

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
        SaltedFishPlayerComponent component = SaltedFishPlayerComponent.KEY.maybeGet(localPlayer).orElse(null);
        return component != null && component.isActive();
    }
}
