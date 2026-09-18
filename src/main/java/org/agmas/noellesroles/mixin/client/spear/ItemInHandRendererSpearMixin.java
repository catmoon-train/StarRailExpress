package org.agmas.noellesroles.mixin.client.spear;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

    /** 第一人称手持：{@code renderArmWithItem} 里注入直刺与举矛动画。 */
    @WrapMethod(method = "renderArmWithItem")
    private void spear$renderFirstPerson(AbstractClientPlayer player, float partialTicks, float pitch,
            InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack,
            MultiBufferSource buffer, int light, Operation<Void> original) {
        if (!SpearConfig.isSpear(stack)) {
            original.call(player, partialTicks, pitch, hand, swingProgress, stack, equippedProgress, poseStack, buffer,
                    light);
            return;
        }
        float swing = swingProgress;
        if (swing > 0.0F) {
            // 直刺：自己摆一次前刺，并把 swingProgress 归零以屏蔽原版挥击位移
            SpearAnim.applyFirstPersonStab(poseStack, swing);
            swing = 0.0F;
        }
        if (player.isUsingItem() && player.getUsedItemHand() == hand) {
            spear$applyChargePose(poseStack, player, stack, hand, partialTicks);
        }
        original.call(player, partialTicks, pitch, hand, swing, stack, equippedProgress, poseStack, buffer, light);
    }

    /** 举矛蓄力：抬到肩前、随蓄力进度后拉，并带轻微回摆。 */
    private static void spear$applyChargePose(PoseStack poseStack, AbstractClientPlayer player, ItemStack stack,
            InteractionHand hand, float partialTicks) {
        float used = stack.getItem().getUseDuration(stack, player)
                - (player.getUseItemRemainingTicks() - partialTicks + 1.0F);
        SpearAnim.HoldUpAnimation anim = SpearAnim.HoldUpAnimation.play(SpearConfig.kinetic(), used);
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
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
