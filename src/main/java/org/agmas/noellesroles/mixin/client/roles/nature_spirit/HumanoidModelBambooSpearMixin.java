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

package org.agmas.noellesroles.mixin.client.roles.nature_spirit;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.entity.BambooSpearEntity;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.content.item.spear.SpearAnim;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 竹枪复用 spear 的第三人称直刺曲线，以同步实体的杆长驱动手臂。
 * 不依赖本地挥手时长，远端玩家与最后一点耐久碎裂后仍能看到完整动作。
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelBambooSpearMixin<T extends LivingEntity> {

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart head;

    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void bambooSpear$poseRightArm(T entity, CallbackInfo ci) {
        bambooSpear$poseArm(entity, this.rightArm, HumanoidArm.RIGHT);
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void bambooSpear$poseLeftArm(T entity, CallbackInfo ci) {
        bambooSpear$poseArm(entity, this.leftArm, HumanoidArm.LEFT);
    }

    @Unique
    private void bambooSpear$poseArm(T entity, ModelPart arm, HumanoidArm humanoidArm) {
        if (!(entity instanceof Player player)) {
            return;
        }
        BambooSpearEntity bamboo = BambooSpearEntity.getClientActiveSpear(player);
        InteractionHand hand = entity.getMainArm() == humanoidArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = entity.getItemInHand(hand);
        if (!stack.is(ModItems.BAMBOO_SPEAR) && (bamboo == null || bamboo.getAttackHand() != hand)) {
            return;
        }
        arm.xRot = (-(float) Math.PI / 2.0F) + this.head.xRot + 0.8F;
        if (entity.isFallFlying()) {
            arm.xRot -= 0.9599311F;
        }
        arm.yRot = -0.1F * this.head.yRot;
        arm.zRot = 0.0F;
    }

    /** 覆盖原版横挥，只驱动实际攻击的那只手；模型传入的是实体年龄而非 partialTick。 */
    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void bambooSpear$stab(T entity, float ageInTicks, CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        BambooSpearEntity bamboo = BambooSpearEntity.getClientActiveSpear(player);
        InteractionHand hand = bamboo != null ? bamboo.getAttackHand() : entity.swingingArm;
        // attackTime 定义在 EntityModel 父类，不能在 HumanoidModel 上声明 @Shadow。
        float attackTime = ((EntityModel<?>) (Object) this).attackTime;
        if (hand == null || (bamboo == null && (!entity.getItemInHand(hand).is(ModItems.BAMBOO_SPEAR)
                || attackTime <= 0.0F))) {
            return;
        }
        float progress = bamboo != null ? bamboo.getStabProgress(ageInTicks - entity.tickCount) : attackTime;
        HumanoidArm attackArm = hand == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        ModelPart arm = attackArm == HumanoidArm.RIGHT ? this.rightArm : this.leftArm;
        arm.xRot += SpearAnim.thirdPersonArmPitch(progress);
        ci.cancel();
    }
}
