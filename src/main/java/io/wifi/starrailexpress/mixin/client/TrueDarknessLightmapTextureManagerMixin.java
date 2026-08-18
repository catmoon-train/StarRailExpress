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

package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.RenderClientLightLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LightTexture.class)
public abstract class TrueDarknessLightmapTextureManagerMixin {
    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;lerp(Lorg/joml/Vector3fc;F)Lorg/joml/Vector3f;", ordinal = 0))
    private Vector3f tmm$fuckYourBlueAssHueMojang(Vector3f instance, Vector3fc other, float t,
            Operation<Vector3f> original) {
        return original.call(instance, other, t);
    }

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3f;lerp(Lorg/joml/Vector3fc;F)Lorg/joml/Vector3f;", ordinal = 6))
    private Vector3f tmm$trueDarknessAndSunLight(Vector3f instance, Vector3fc other, float t,
            Operation<Vector3f> original) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        float light = RenderClientLightLevel.EVENT.invoker().renderClientLightLevel(instance, other, t);
        if (light >= 0) {
            return original.call(instance, new Vector3f(.8f, .8f, .8f), light);
        }
        if (client.player != null && world != null) {
            if (SREClient.isPlayerSpectatingOrCreative() && !client.player.isCreative()) {
                return original.call(instance, new Vector3f(.8f, .8f, .8f), 0.75f);
            } else
                return original.call(instance, new Vector3f(.8f, .8f, .8f),
                        Mth.lerp(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),
                                SREClient.prevInstinctLightLevel, SREClient.instinctLightLevel));
        }

        return original.call(instance, other, t);
    }

    @ModifyVariable(method = "updateLightTexture", at = @At(value = "STORE"), ordinal = 2)
    private float tmm$keepSkylight(float value) {
        return value;
    }
}
