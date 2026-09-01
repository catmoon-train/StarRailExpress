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

package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.client.SREClient;
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
    private static long lastCacheTime = 0;
    private static CameraType cacheResult = null;
    private static CameraType cacheOriginal = null;
    private static final int CACHE_TIME_GAP = 100;

    @ModifyReturnValue(method = "getCameraType", at = @At("RETURN"))
    public CameraType getPerspective(CameraType original) {
        if (SREClient.isInLobby) {
            return original;
        }
        final var client = Minecraft.getInstance();
        if (client == null)
            return original;
        if (client.player == null)
            return original;
        long now = System.currentTimeMillis();
        if (now - lastCacheTime > CACHE_TIME_GAP || cacheResult == null || cacheOriginal != original) {
            lastCacheTime = now;
            cacheOriginal = original;
            cacheResult = getResult(original, client);
        }
        if (cacheResult != null) {
            return cacheResult;
        }
        return original;
    }

    private CameraType getResult(CameraType original, Minecraft client) {
        var camera = AllowOtherCameraType.EVENT.invoker().onGetCameraType(original, client.player);
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
        LocalPlayer localPlayer = client.player;
        if (!GameUtils.isGameRunning(localPlayer))
            return original;
        if (GameUtils.isPlayerAliveAndSurvival(localPlayer)) {
            if (SREClient.gameComponent != null) {
                final var role = SREClient.gameComponent.getRole(client.player);

                if (role != null && RoleVisibilityRules.canUseOtherPerson.stream()
                        .anyMatch(predicate -> predicate.test(role))) {
                    return original;
                }
            }
            return CameraType.FIRST_PERSON;
        } else {
            return original;
        }
    }
}
