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

package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.MirrorReunionSceneManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MirrorReunionCameraMixin {
    @Shadow
    public abstract void setPosition(Vec3 vec3);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("RETURN"))
    private void noellesroles$mirrorReunionShake(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {
        float fallY = MirrorReunionSceneManager.INSTANCE.getCameraFallY(tickDelta);
        float intensity = MirrorReunionSceneManager.INSTANCE.getShakeIntensity(tickDelta);
        if (intensity <= 0.001f && Math.abs(fallY) < 0.001f) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float t = player.tickCount + tickDelta;
        float yaw = (Mth.sin(t * 1.35f) * 1.6f + Mth.sin(t * 4.8f) * 0.55f) * intensity;
        float pitch = (Mth.cos(t * 1.15f) * 1.05f + Mth.sin(t * 5.4f) * 0.4f) * intensity;
        float ox = Mth.sin(t * 2.2f) * intensity * 0.16f;
        float oy = Mth.sin(t * 3.5f) * intensity * 0.11f;
        float oz = Mth.cos(t * 2.7f) * intensity * 0.16f;
        setRotation(getYRot() + yaw, getXRot() + pitch);
        setPosition(getPosition().add(ox, oy + fallY, oz));
    }
}
