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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.effects.LimpEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class LimpCameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("RETURN"))
    private void noellesroles$limpCamera(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (thirdPerson || !(focusedEntity instanceof LocalPlayer player)) {
            return;
        }
        int amplifier = LimpEffect.amplifierOf(player);
        if (amplifier < 0 || !player.onGround() || player.getVehicle() != null) {
            return;
        }
        if (player.walkAnimation.speed(tickDelta) < 0.01f) {
            return;
        }
        float weight = LimpEffect.limpWeight(player.walkAnimation.position(tickDelta), amplifier);
        if (weight < 0.01f) {
            return;
        }
        setRotation(getYRot(), getXRot() + LimpEffect.cameraPitch(amplifier) * weight);
        setPosition(getPosition().add(0, -LimpEffect.cameraDip(amplifier) * weight, 0));
    }
}
