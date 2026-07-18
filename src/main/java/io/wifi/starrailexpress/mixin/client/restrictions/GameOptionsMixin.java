package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.event.AllowOtherCameraType;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.rules.RoleVisibilityRules;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
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

        // 乘骑载具时自动切换为第三人称视角（座椅方块除外）。
        // 放在角色白名单判定之前，确保所有载具（摩托车/汽车/房车/船/马/滑索等）
        // 统一生效，下车后自动恢复角色原本的视角规则。
        LocalPlayer passenger = Minecraft.getInstance().player;
        if (passenger.isPassenger() && !(passenger.getVehicle() instanceof SeatEntity)) {
            return CameraType.THIRD_PERSON_BACK;
        }

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
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (!GameUtils.isGameRunning(localPlayer))
            return original;
        if (GameUtils.isPlayerAliveAndSurvival(localPlayer)) {
            if (SREClient.gameComponent != null) {
                final var role = SREClient.gameComponent.getRole(Minecraft.getInstance().player);

                if (role != null && RoleVisibilityRules.canUseOtherPerson.stream().anyMatch(predicate -> predicate.test(role))) {
                    return original;
                }
            }
            return CameraType.FIRST_PERSON;
        } else {
            return original;
        }
    }
}
