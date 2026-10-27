package org.agmas.noellesroles.mixin.client.echo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntAnim;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntClient;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntPayload;
import org.agmas.noellesroles.game.modes.echo.anim.EchoSkillCastAnim;
import org.agmas.noellesroles.game.modes.echo.content.EchoContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称手持模型朝向：手臂姿势由 {@link EchoHuntThirdPersonMixin} 负责，
 * 这里单独处理「刀/信物本身」怎么转，两者叠加才是完整的挥砍观感。
 *
 * <p>只在 THIRD_PERSON_* 上下文生效，且只对回声工具生效；其它物品完全走原版。
 */
@Mixin(ItemInHandRenderer.class)
public class EchoHuntItemPoseMixin {

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;" +
            "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void echo$thirdPersonItemPose(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                          boolean leftHanded, PoseStack poseStack, MultiBufferSource buffer, int light,
                                          CallbackInfo ci) {
        if (context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) return;
        if (entity == null || stack.isEmpty()) return;
        int side = leftHanded ? -1 : 1;

        if (stack.is(EchoContent.SKILL_TOKEN)) {
            EchoHuntClient.Action action = EchoHuntClient.action(entity.getUUID());
            if (action == null || action.kind != EchoHuntPayload.Kind.CAST || action.style == null) return;
            float progress = action.progress(System.nanoTime() / 1_000_000L);
            if (progress < 0F) return;
            EchoSkillCastAnim.Pose pose = EchoSkillCastAnim.thirdPersonItem(progress, action.style, side);
            rotate(poseStack, pose.rx(), pose.ry(), pose.rz());
            return;
        }

        if (!stack.is(EchoContent.CUTTER)) return;


        EchoHuntClient.Action action = EchoHuntClient.action(entity.getUUID());
        if (action == null) return;
        float progress = action.progress(System.nanoTime() / 1_000_000L);
        if (progress < 0F) return;
        if (action.kind == EchoHuntPayload.Kind.STRIKE) {
            float[] pose = EchoHuntAnim.thirdPersonStrikeItem(progress, action.empowered, side);
            rotate(poseStack, pose[3], pose[4], pose[5]);
        } else if (action.kind == EchoHuntPayload.Kind.WIPE) {
            float[] pose = EchoHuntAnim.thirdPersonWipeItem(progress, !action.empowered, side);
            rotate(poseStack, pose[3], pose[4], pose[5]);
        } else if (action.kind == EchoHuntPayload.Kind.INSTINCT) {
            float[] pose = EchoHuntAnim.thirdPersonChargeItem(0.55F, side, true);
            float fade = 1F - progress;
            rotate(poseStack, pose[3] * fade, pose[4] * fade, pose[5] * fade);
        }
    }

    private static void rotate(PoseStack poseStack, float rx, float ry, float rz) {
        if (rx != 0F) poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        if (ry != 0F) poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        if (rz != 0F) poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
    }

    private static int chargeTicks(LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            var data = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                    org.agmas.noellesroles.game.modes.echo.state.EchoRoleData.class, player);
            if (data != null) return Math.max(1, data.cutterChargeTicks());
        }
        return 20;
    }

    private static float partialTick() {
        return net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }
}