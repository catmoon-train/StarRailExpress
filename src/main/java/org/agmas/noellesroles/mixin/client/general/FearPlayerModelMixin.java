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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.content.effects.FearEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class FearPlayerModelMixin<T extends LivingEntity> {

    @Shadow
    @Final
    public ModelPart head;

    @Shadow
    @Final
    public ModelPart body;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart rightLeg;

    @Shadow
    @Final
    public ModelPart leftLeg;

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void noellesroles$fearAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (FearEffects.isSitting(entity)) {
            rightLeg.xRot = -1.4137167F;
            rightLeg.yRot = (float) (Math.PI / 10.0);
            rightLeg.zRot = 0.07853982F;
            leftLeg.xRot = -1.4137167F;
            leftLeg.yRot = (float) (-Math.PI / 10.0);
            leftLeg.zRot = -0.07853982F;
            copyPants();
        }
        if (!FearEffects.isTrembling(entity)) {
            return;
        }
        float shake = 0.16f;
        body.zRot += Mth.sin(ageInTicks * 1.7f) * shake;
        body.xRot += Mth.cos(ageInTicks * 2.1f) * shake * 0.45f;
        head.zRot += Mth.sin(ageInTicks * 2.3f) * shake;
        head.xRot += Mth.cos(ageInTicks * 1.9f) * shake * 0.35f;
        rightArm.xRot += Mth.sin(ageInTicks * 2.8f) * shake;
        leftArm.xRot += Mth.cos(ageInTicks * 2.6f) * shake;
        rightArm.zRot += Mth.sin(ageInTicks * 2.2f) * shake * 0.4f;
        leftArm.zRot += Mth.cos(ageInTicks * 2.4f) * shake * 0.4f;
        if ((Object) this instanceof PlayerModel<?> playerModel) {
            playerModel.jacket.copyFrom(body);
            playerModel.rightSleeve.copyFrom(rightArm);
            playerModel.leftSleeve.copyFrom(leftArm);
            playerModel.hat.copyFrom(head);
        }
        copyPants();
    }

    private void copyPants() {
        if ((Object) this instanceof PlayerModel<?> playerModel) {
            playerModel.rightPants.copyFrom(rightLeg);
            playerModel.leftPants.copyFrom(leftLeg);
        }
    }
}
