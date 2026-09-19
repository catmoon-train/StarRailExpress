package org.agmas.noellesroles.mixin.client.spear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.spear.SpearAnim;
import org.agmas.noellesroles.spear.SpearConfig;
import org.agmas.noellesroles.spear.SpearUser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手持矛的动画：
 * <ul>
 * <li>第一人称：直刺（前刺 + 收回）与举矛蓄力抬枪；</li>
 * <li>第三人称：手持物品的前刺与举矛姿态。</li>
 * </ul>
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererSpearMixin {

    /** 第一人称左键：替换原版挥击变换，避免矛仍按剑的轨迹摆动。 */
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmAttackTransform(" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
    private void spear$renderFirstPersonStab(ItemInHandRenderer instance, PoseStack poseStack, HumanoidArm arm,
            float swingProgress, Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        if (SpearConfig.isSpear(stack)) {
            SpearAnim.applyFirstPersonStab(poseStack, swingProgress);
            return;
        }
        original.call(instance, poseStack, arm, swingProgress);
    }

    /** 第一人称左键：屏蔽原版剑类挥击产生的位移，直刺曲线由上面的变换接管。 */
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 12))
    private void spear$suppressFirstPersonSwing(PoseStack poseStack, float x, float y, float z,
            Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        if (!SpearConfig.isSpear(stack)) {
            original.call(poseStack, x, y, z);
        }
    }

    /** 第一人称右键：在原版手臂装备变换之后插入完整的举矛动画。 */
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;getUseAnimation()" +
                    "Lnet/minecraft/world/item/UseAnim;", shift = At.Shift.AFTER))
    private void spear$renderFirstPersonCharge(AbstractClientPlayer player, float partialTicks, float pitch,
            InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack,
            MultiBufferSource buffer, int light, CallbackInfo ci, @Share("suppress") LocalBooleanRef suppress) {
        if (SpearConfig.isSpear(stack) && player.isUsingItem() && player.getUsedItemHand() == hand) {
            spear$applyChargePose(poseStack, player, stack, hand, partialTicks);
            suppress.set(true);
        }
    }

    /** 参考 Backported-Spears：自定义举矛动作已经包含手部锚点，禁止原版再次叠加装备偏移。 */
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
            ordinal = 2))
    private void spear$suppressEquipOffsetForCharge(ItemInHandRenderer instance, PoseStack poseStack,
            HumanoidArm arm, float equippedProgress, Operation<Void> original,
            @Share("suppress") LocalBooleanRef suppress) {
        if (!suppress.get()) {
            original.call(instance, poseStack, arm, equippedProgress);
        }
    }

    /** 举矛蓄力：抬到肩前、随蓄力进度后拉，并带轻微回摆。 */
    private static void spear$applyChargePose(PoseStack poseStack, AbstractClientPlayer player, ItemStack stack,
            InteractionHand hand, float partialTicks) {
        float used = stack.getItem().getUseDuration(stack, player)
                - (player.getUseItemRemainingTicks() - partialTicks + 1.0F);
        SpearAnim.HoldUpAnimation anim = SpearAnim.HoldUpAnimation.play(SpearConfig.kinetic(), used);
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        // Backported-Spears' vanilla hand anchor. Without it the custom pose
        // starts from the center and appears on the left side of the screen.
        poseStack.translate(side * 0.56F, -0.52F, -0.72F);
        poseStack.translate(
                side * (anim.raiseProgress() * 0.15F + anim.raiseProgressEnd() * -0.05F
                        + anim.swayProgress() * -0.1F + anim.swayScaleSlow() * 0.005F),
                anim.raiseProgress() * -0.075F + anim.raiseProgressMiddle() * 0.075F
                        + anim.swayScaleFast() * 0.01F,
                anim.raiseProgressStart() * 0.05F + anim.raiseProgressEnd() * -0.05F
                        + anim.swayScaleSlow() * 0.005F);
        float raise = anim.raiseProgress();
        if (raise < 0.5F) {
            raise = 4.0F * raise * raise * (7.189819F * raise - 2.5949094F) / 2.0F;
        } else {
            float g = 2.0F * raise - 2.0F;
            raise = (g * g * (3.5949094F * g + 2.5949094F) + 2.0F) / 2.0F;
        }
        SpearAnim.mulPoseAround(poseStack, Axis.XP.rotationDegrees(
                -65.0F * raise - 35.0F * (1.0F - anim.lowerProgress()) + 100.0F * anim.raiseBackProgress()
                        - 0.5F * anim.swayScaleFast()), 0.0F, 0.1F, 0.0F);
        SpearAnim.mulPoseAround(poseStack, Axis.YN.rotationDegrees(side * (-90.0F
                * Mth.clamp(Mth.inverseLerp(anim.raiseProgress(), 0.5F, 0.55F), 0.0F, 1.0F)
                + 90.0F * anim.swayProgress() + 2.0F * anim.swayScaleSlow())), side * 0.15F, 0.0F, 0.0F);
        // 冲锋命中后的收招下压
        float recover = player instanceof SpearUser spearUser ? spearUser.getTimeSinceLastKineticAttack(partialTicks)
                : 0.0F;
        recover = (1.0F - Mth.square(Mth.square(
                1.0F - Mth.clamp(Mth.inverseLerp(recover, 1.0F, 3.0F), 0.0F, 1.0F)))
                + (Mth.cos((float) Math.PI * Mth.clamp(Mth.inverseLerp(recover, 3.0F, 10.0F), 0.0F, 1.0F)) - 1.0F) / 2.0F)
                * 0.4F;
        if (recover >= 10.0F) {
            recover = 0.0F;
        }
        poseStack.translate(0.0F, -recover, 0.0F);
    }

    /** 第三人称（其他玩家 / 自己身上的物品）：直刺与举矛姿态。 */
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void spear$renderThirdPerson(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            boolean leftHanded, PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (!SpearConfig.isSpear(stack)) {
            return;
        }
        if (context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return;
        }
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float attackAnim = entity.getAttackAnim(partialTicks);
        if (attackAnim > 0.0F) {
            SpearAnim.applyThirdPersonStab(poseStack, attackAnim, SpearConfig.kinetic().forwardMovement());
        }
        if (entity.isUsingItem() && entity.getUseItem().equals(stack)) {
            float used = stack.getItem().getUseDuration(stack, entity)
                    - (entity.getUseItemRemainingTicks() - partialTicks + 1.0F);
            SpearAnim.HoldUpAnimation anim = SpearAnim.HoldUpAnimation.play(SpearConfig.kinetic(), used);
            int side = leftHanded ? -1 : 1;
            float g = 1.0F - anim.raiseProgress() - 1.0F;
            g = 1.0F - (1.0F + 2.70158F * g * g * g + 1.70158F * Mth.square(g));
            float recover = entity instanceof SpearUser spearUser
                    ? spearUser.getTimeSinceLastKineticAttack(partialTicks)
                    : 0.0F;
            recover = (1.0F - Mth.square(Mth.square(
                    1.0F - Mth.clamp(Mth.inverseLerp(recover, 1.0F, 3.0F), 0.0F, 1.0F)))
                    + (Mth.cos((float) Math.PI * Mth.clamp(Mth.inverseLerp(recover, 3.0F, 10.0F), 0.0F, 1.0F)) - 1.0F)
                            / 2.0F) * 0.4F;
            if (recover >= 10.0F) {
                recover = 0.0F;
            }
            poseStack.translate(0.0F, -recover * 0.4F,
                    -SpearConfig.kinetic().forwardMovement() * (g - anim.raiseBackProgress()) + recover);
            SpearAnim.mulPoseAround(poseStack, Axis.XN.rotationDegrees(
                    anim.raiseProgress() * 70.0F - anim.raiseBackProgress() * 70.0F), 0.0F, -0.03125F, 0.125F);
            SpearAnim.mulPoseAround(poseStack, Axis.YP.rotationDegrees(
                    anim.raiseProgress() * side * 90.0F - anim.swayProgress() * side * 90.0F), 0.0F, 0.0F, 0.125F);
        }
    }
}
