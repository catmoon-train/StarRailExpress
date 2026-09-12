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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.effects.FearEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 害怕只作用在当前被渲染的玩家。共享 PlayerModel 会把倾斜外套留给下一个人，
 * 所以没有害怕的玩家必须在 setupAnim 开头清掉残留。
 */
@Mixin(value = HumanoidModel.class, priority = 1200)
public abstract class FearPlayerModelMixin<T extends LivingEntity> {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void noellesroles$fearPrepare(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (FearEffects.isSitting(entity)) {
            model.riding = true;
            return;
        }
        if (entity instanceof Player) {
            model.body.zRot = 0.0f;
            model.head.zRot = 0.0f;
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void noellesroles$fearShake(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (!FearEffects.isTrembling(entity)) {
            if (entity instanceof Player) {
                model.body.zRot = 0.0f;
                model.head.zRot = 0.0f;
                noellesroles$copyOverlays(model);
            }
            return;
        }
        float lean = Mth.sin(ageInTicks * 1.35f) * 0.035f;
        float nod = Mth.cos(ageInTicks * 1.7f) * 0.018f;
        model.head.zRot = lean;
        model.body.zRot = lean;
        model.rightArm.zRot += lean;
        model.leftArm.zRot += lean;
        model.rightLeg.zRot += lean;
        model.leftLeg.zRot += lean;
        model.head.xRot += nod;
        noellesroles$copyOverlays(model);
    }

    @Unique
    private static void noellesroles$copyOverlays(HumanoidModel<?> model) {
        if (!(model instanceof PlayerModel<?> playerModel)) {
            return;
        }
        playerModel.hat.copyFrom(model.head);
        playerModel.jacket.copyFrom(model.body);
        playerModel.rightSleeve.copyFrom(model.rightArm);
        playerModel.leftSleeve.copyFrom(model.leftArm);
        playerModel.rightPants.copyFrom(model.rightLeg);
        playerModel.leftPants.copyFrom(model.leftLeg);
    }
}
