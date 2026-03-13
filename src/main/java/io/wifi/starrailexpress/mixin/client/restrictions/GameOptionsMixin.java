package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Options.class)
public class GameOptionsMixin {
    @ModifyReturnValue(method = "getCameraType", at = @At("RETURN"))
    public CameraType getPerspective(CameraType original) {
        if (SREClient.isInLobby) {
            return original;
        }
        if (Minecraft.getInstance() == null)
            return original;
        if (Minecraft.getInstance().player == null)
            return original;

        var camera = AllowOtherCameraType.EVENT.invoker().onGetCameraType(original, Minecraft.getInstance().player);
        if (camera != AllowOtherCameraType.ReturnCameraType.NO_CHANGE) {
            switch (camera) {
                case AllowOtherCameraType.ReturnCameraType.FIRST_PERSON:
                    return CameraType.FIRST_PERSON;
                case AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK:
                    return CameraType.THIRD_PERSON_BACK;
                case AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_FRONT:
                    return CameraType.THIRD_PERSON_FRONT;
                default:
            }
        }
        if (GameFunctions.isPlayerAliveAndSurvival(Minecraft.getInstance().player)) {
            if (SREClient.gameComponent != null) {
                final var role = SREClient.gameComponent.getRole(Minecraft.getInstance().player);

                if (role != null && SRE.canUseOtherPerson.stream().anyMatch(predicate -> predicate.test(role))) {
                    return original;
                }
            }
            return CameraType.FIRST_PERSON;
        } else {
            return original;
        }
    }
}
