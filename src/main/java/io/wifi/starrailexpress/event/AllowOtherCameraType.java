package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface AllowOtherCameraType {

    /**
     * Callback for determining whether an {@link ItemStack} should drop when player
     * died
     */
    public enum ReturnCameraType {
        NO_CHANGE,
        FIRST_PERSON,
        THIRD_PERSON_BACK,
        THIRD_PERSON_FRONT
    }

    Event<AllowOtherCameraType> EVENT = createArrayBacked(AllowOtherCameraType.class,
            listeners -> (original, localPlayer) -> {
                ReturnCameraType returnvalue = ReturnCameraType.NO_CHANGE;
                for (AllowOtherCameraType listener : listeners) {
                    ReturnCameraType temp = listener.onGetCameraType(original, localPlayer);
                    if (temp != null) {
                        if (temp != ReturnCameraType.NO_CHANGE) {
                            return temp;
                        }
                    }
                }
                return returnvalue;
            });

    ReturnCameraType onGetCameraType(CameraType original, LocalPlayer localplayer);
}
