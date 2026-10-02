package org.agmas.noellesroles.mixin.client.katana;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.katana.KatanaAnim;
import org.agmas.noellesroles.katana.KatanaState;
import org.agmas.noellesroles.katana.client.KatanaClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称 / 第三人称手持武士刀的渲染：
 * <ul>
 * <li>第一人称：三连招的自定义挥击曲线替换原版剑挥，以及格挡姿势；</li>
 * <li>第三人称（其他玩家身上的物品）：招式与格挡的物品变换。</li>
 * </ul>
 * 第三人称手臂姿势见 {@code HumanoidModelKatanaMixin}。
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererKatanaMixin {

    /** 第一人称左键：有招式动画时替换原版挥击变换。 */
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmAttackTransform(" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
    private void katana$renderFirstPersonMove(ItemInHandRenderer instance, PoseStack poseStack, HumanoidArm arm,
            float swingProgress, Operation<Void> original, @Local(argsOnly = true) ItemStack stack,
            @Local(argsOnly = true) AbstractClientPlayer player) {
        float progress = KatanaClientState.moveProgress(player);
        if (stack.is(ModItems.KATANA) && progress >= 0.0F) {
            int move = KatanaClientState.animatingMove(player);
            switch (move) {
                case KatanaState.MOVE_THRUST -> KatanaAnim.applyFirstPersonThrust(poseStack, progress);
                case KatanaState.MOVE_SLASH -> KatanaAnim.applyFirstPersonSlash(poseStack, progress);
                default -> KatanaAnim.applyFirstPersonSweep(poseStack, progress);
            }
            return;
        }
        original.call(instance, poseStack, arm, swingProgress);
    }

    /** 第一人称左键：屏蔽原版剑类挥击产生的位移，招式曲线由上面的变换接管。 */
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 12))
    private void katana$suppressFirstPersonSwing(PoseStack poseStack, float x, float y, float z,
            Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        if (!stack.is(ModItems.KATANA)) {
            original.call(poseStack, x, y, z);
        }
    }

    /** 第一人称右键：在原版手臂装备变换之后插入格挡姿势。 */
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;getUseAnimation()" +
                    "Lnet/minecraft/world/item/UseAnim;", shift = At.Shift.AFTER))
    private void katana$renderFirstPersonBlock(AbstractClientPlayer player, float partialTicks, float pitch,
            InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack,
            MultiBufferSource buffer, int light, CallbackInfo ci, @Share("suppress") LocalBooleanRef suppress) {
        if (stack.is(ModItems.KATANA) && player.isUsingItem() && player.getUsedItemHand() == hand) {
            int side = hand == InteractionHand.MAIN_HAND
                    ? (player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1)
                    : (player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1);
            // 原版手部锚点（与矛的自定义姿势一致），否则姿势会从屏幕中心开始
            poseStack.translate(side * 0.56F, -0.52F, -0.72F);
            float raise = player.getTicksUsingItem() + partialTicks;
            KatanaAnim.applyFirstPersonBlock(poseStack, raise, side);
            suppress.set(true);
        }
    }

    /** 格挡姿势自带手部锚点，禁止原版再次叠加装备偏移。 */
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
            ordinal = 2))
    private void katana$suppressEquipOffsetForBlock(ItemInHandRenderer instance, PoseStack poseStack,
            HumanoidArm arm, float equippedProgress, Operation<Void> original,
            @Share("suppress") LocalBooleanRef suppress) {
        if (!suppress.get()) {
            original.call(instance, poseStack, arm, equippedProgress);
        }
    }

    /** 第三人称（其他玩家手持物品）：按招式 / 格挡大幅改变刀的模型朝向（参考矛的做法）。 */
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;" +
            "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void katana$renderThirdPersonItem(net.minecraft.world.entity.LivingEntity entity, ItemStack stack,
            ItemDisplayContext context, boolean leftHanded, PoseStack poseStack, MultiBufferSource buffer, int light,
            CallbackInfo ci) {
        if (!stack.is(ModItems.KATANA)) {
            return;
        }
        if (context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return;
        }
        float partialTicks = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        int side = leftHanded ? -1 : 1;
        // 格挡：刀尖转向左下
        if (entity.isUsingItem() && entity.getUseItem().equals(stack)) {
            float raise = entity.getTicksUsingItem() + partialTicks;
            KatanaAnim.applyThirdPersonBlockItem(poseStack, raise, side);
            return;
        }
        // 招式：按当前招式大幅旋转刀的模型（前倒 / 横转 / 翻腕）
        float attackAnim = entity.getAttackAnim(partialTicks);
        if (attackAnim <= 0.0F) {
            return;
        }
        int move = KatanaClientState.animatingMove(entity);
        switch (move) {
            case KatanaState.MOVE_THRUST -> KatanaAnim.applyThirdPersonThrustItem(poseStack, attackAnim);
            case KatanaState.MOVE_SLASH -> KatanaAnim.applyThirdPersonSlashItem(poseStack, attackAnim);
            default -> KatanaAnim.applyThirdPersonSweepItem(poseStack, attackAnim, side);
        }
    }
}
