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

/**
 * 害怕只作用在当前被渲染的玩家。共享 PlayerModel 会把倾斜外套留给下一个人，
 * 所以没有害怕的玩家必须在 setupAnim 开头清掉残留。
 */
@Mixin(value = PlayerModel.class, priority = 1200)
public abstract class FearPlayerModelMixin<T extends LivingEntity> {

    @Shadow
    public boolean riding;

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

    @Inject(method = "setupAnim", at = @At("HEAD"))
    private void noellesroles$fearPrepare(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (FearEffects.isSitting(entity)) {
            this.riding = true;
            return;
        }
        // 上一帧害怕玩家留下的 body/head.zRot 和外套，必须在原版动画前清掉。
        body.zRot = 0.0f;
        head.zRot = 0.0f;
    }

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void noellesroles$fearShake(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!FearEffects.isTrembling(entity)) {
            body.zRot = 0.0f;
            head.zRot = 0.0f;
            copyOverlays();
            return;
        }
        float lean = Mth.sin(ageInTicks * 1.35f) * 0.035f;
        float nod = Mth.cos(ageInTicks * 1.7f) * 0.018f;
        head.zRot = lean;
        body.zRot = lean;
        rightArm.zRot += lean;
        leftArm.zRot += lean;
        rightLeg.zRot += lean;
        leftLeg.zRot += lean;
        head.xRot += nod;
        copyOverlays();
    }

    private void copyOverlays() {
        PlayerModel<?> playerModel = (PlayerModel<?>) (Object) this;
        playerModel.hat.copyFrom(head);
        playerModel.jacket.copyFrom(body);
        playerModel.rightSleeve.copyFrom(rightArm);
        playerModel.leftSleeve.copyFrom(leftArm);
        playerModel.rightPants.copyFrom(rightLeg);
        playerModel.leftPants.copyFrom(leftLeg);
    }
}
