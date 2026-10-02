package org.agmas.noellesroles.mixin.client.katana;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.katana.KatanaAnim;
import org.agmas.noellesroles.katana.KatanaState;
import org.agmas.noellesroles.katana.client.KatanaClientState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称武士刀姿势：
 * <ul>
 * <li>右键格挡：持刀手臂抬起横在胸前，刀尖斜向左下；</li>
 * <li>三连招：横扫（手臂水平扫动）/ 突刺（手臂前伸）/
 * 劈砍（举刀下劈，双臂劈落时向内聚拢，参考手铐前铐姿势的收敛方向）。</li>
 * </ul>
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelKatanaMixin<T extends LivingEntity> {

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
    private void katana$poseRightArm(T entity, CallbackInfo ci) {
        katana$poseArm(entity, this.rightArm, HumanoidArm.RIGHT);
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void katana$poseLeftArm(T entity, CallbackInfo ci) {
        katana$poseArm(entity, this.leftArm, HumanoidArm.LEFT);
    }

    @SuppressWarnings("unchecked")
    private void katana$poseArm(T entity, ModelPart arm, HumanoidArm humanoidArm) {
        ItemStack heldStack = entity.getMainArm() == humanoidArm
                ? entity.getMainHandItem() : entity.getOffhandItem();
        if (!entity.getMainHandItem().is(ModItems.KATANA) && !entity.getOffhandItem().is(ModItems.KATANA)) {
            return;
        }
        if (!heldStack.is(ModItems.KATANA)) {
            // 空手一侧：劈砍时辅助持刀
            if (entity.getAttackAnim(0.0F) > 0.0F
                    && KatanaClientState.animatingMove(entity) == KatanaState.MOVE_SLASH
                    && !entity.isUsingItem()) {
                float[] r = KatanaAnim.thirdPersonSlash(entity.getAttackAnim(0.0F));
                int side = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
                // 劈落时与持刀侧一起向内聚拢，双手在劈落瞬间收向中线（同手铐前铐姿势的收敛方向）
                float converge = KatanaAnim.slashConverge(entity.getAttackAnim(0.0F));
                arm.xRot = r[0] + 0.25F;
                arm.yRot = -side * 0.7F * converge;
                arm.zRot = -side * (0.15F + 0.4F * converge);
            }
            return;
        }

        // 格挡：抬臂横在胸前（前摇 0.4 秒内抬到位；raise 归一化到 0~1）
        if (entity.isUsingItem() && entity.getUseItem().is(ModItems.KATANA)) {
            float raise = (entity.getTicksUsingItem() + 0.0F) / KatanaState.BLOCK_WINDUP_TICKS;
            float[] r = KatanaAnim.thirdPersonBlock(raise);
            arm.xRot = r[0];
            arm.yRot = r[1];
            arm.zRot = r[2];
            return;
        }

        // 三连招：以原版挥击进度驱动曲线
        float attackAnim = entity.getAttackAnim(0.0F);
        if (attackAnim <= 0.0F) {
            return;
        }
        int side = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
        int move = KatanaClientState.animatingMove(entity);
        switch (move) {
            case KatanaState.MOVE_THRUST -> {
                float[] r = KatanaAnim.thirdPersonThrust(attackAnim, this.head.xRot);
                arm.xRot = r[0];
                arm.yRot = -0.1F * this.head.yRot;
                arm.zRot = 0.0F;
            }
            case KatanaState.MOVE_SLASH -> {
                float[] r = KatanaAnim.thirdPersonSlash(attackAnim);
                // 劈落时双手向内聚拢（方向与手铐前铐姿势一致：yRot/zRot 朝中线收）；
                // 举刀时 converge 为负 = 略向外张开，收招回到自然。
                float converge = KatanaAnim.slashConverge(attackAnim);
                arm.xRot = r[0];
                arm.yRot = -side * 0.7F * converge;
                arm.zRot = -side * (0.25F + 0.4F * converge);
            }
            default -> {
                float[] r = KatanaAnim.thirdPersonSweep(attackAnim, side);
                arm.xRot = r[0];
                arm.yRot = r[1];
                arm.zRot = r[2];
            }
        }
    }

    /** 取消原版剑挥击动画，由上面的姿势 Mixin 按招式接管。 */
    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void katana$setupAttackAnimation(T entity, float ageInTicks, CallbackInfo ci) {
        float attackTime = entity.getAttackAnim(ageInTicks);
        if (attackTime <= 0.0F) {
            return;
        }
        net.minecraft.world.InteractionHand swingingHand = entity.swingingArm;
        ItemStack stack = swingingHand == net.minecraft.world.InteractionHand.MAIN_HAND
                ? entity.getMainHandItem() : entity.getOffhandItem();
        if (stack != null && stack.is(ModItems.KATANA)) {
            ci.cancel();
        }
    }
}
