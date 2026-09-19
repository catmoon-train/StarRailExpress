package org.agmas.noellesroles.mixin.client.spear;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.spear.SpearAnim;
import org.agmas.noellesroles.spear.SpearConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称：持矛的手臂摆出「直刺」姿态，举矛蓄力时抬臂并带回摆。
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelSpearMixin<T extends LivingEntity> {

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart body;

    @Shadow
    @Final
    public ModelPart head;

    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void spear$poseRightArm(T entity, CallbackInfo ci) {
        spear$poseArm(entity, this.rightArm, HumanoidArm.RIGHT);
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void spear$poseLeftArm(T entity, CallbackInfo ci) {
        spear$poseArm(entity, this.leftArm, HumanoidArm.LEFT);
    }

    @Unique
    private void spear$poseArm(T entity, ModelPart arm, HumanoidArm humanoidArm) {
        ItemStack stack = entity.getMainArm() == humanoidArm ? entity.getMainHandItem() : entity.getOffhandItem();
        if (!SpearConfig.isSpear(stack)) {
            return;
        }
        // 持矛待机：手臂向前伸直并略微抬高
        arm.yRot = -0.1F * this.head.yRot;
        arm.xRot = (-(float) Math.PI / 2.0F) + this.head.xRot + 0.8F;
        if (entity.isFallFlying()) {
            arm.xRot -= 0.9599311F;
        }
        if (entity.isUsingItem()) {
            SpearAnim.HoldUpAnimation anim = SpearAnim.HoldUpAnimation.play(SpearConfig.kinetic(),
                    entity.getTicksUsingItem());
            int side = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
            float rad = (float) (Math.PI / 180.0);
            arm.yRot += -side * anim.swayScaleFast() * rad * anim.swayIntensity();
            arm.zRot += -side * anim.swayScaleSlow() * rad * anim.swayIntensity() * 0.5F;
            arm.xRot += rad * (-40.0F * anim.raiseProgressStart() + 30.0F * anim.raiseProgressMiddle()
                    - 20.0F * anim.raiseProgressEnd() + 20.0F * anim.lowerProgress()
                    + 10.0F * anim.raiseBackProgress() + 0.6F * anim.swayScaleSlow() * anim.swayIntensity());
        }
        // 直刺时手臂的俯仰变化
        float attackAnim = entity.getAttackAnim(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if (attackAnim > 0.0F) {
            // 原版手臂动画以身体为参考系；不扣除身体旋转时，第三人称直刺会偏向一侧。
            arm.yRot -= this.body.yRot;
            arm.xRot += SpearAnim.thirdPersonArmPitch(attackAnim);
        }
    }
}
