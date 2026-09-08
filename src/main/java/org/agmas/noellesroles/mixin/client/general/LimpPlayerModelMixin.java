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

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.content.effects.LimpEffect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 腿瘸第三人称迈腿。只改正原版每帧都会重写的腿部旋转，避免改 {@code ModelPart.y}
 * 造成坐标累加。
 */
@Mixin(HumanoidModel.class)
public abstract class LimpPlayerModelMixin<T extends LivingEntity> {

    @Shadow
    @Final
    public ModelPart rightLeg;

    @Shadow
    @Final
    public ModelPart leftLeg;

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void noellesroles$limpAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        int amplifier = LimpEffect.amplifierOf(entity);
        if (amplifier < 0 || limbSwingAmount < 0.01f) {
            return;
        }
        float severity = LimpEffect.severity(amplifier);
        float weak = LimpEffect.limpWeight(limbSwing, amplifier);
        float twist = 0.10f * severity + 0.18f * weak;

        rightLeg.xRot *= 1f - 0.78f * weak;
        rightLeg.zRot = twist;
        leftLeg.xRot *= 1f + 0.26f * weak;

        if ((Object) this instanceof PlayerModel<?> playerModel) {
            playerModel.rightPants.copyFrom(rightLeg);
            playerModel.leftPants.copyFrom(leftLeg);
        }
    }
}
