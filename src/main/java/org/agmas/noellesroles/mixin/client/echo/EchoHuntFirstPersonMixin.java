package org.agmas.noellesroles.mixin.client.echo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntAnim;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntClient;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntPayload;
import org.agmas.noellesroles.game.modes.echo.anim.EchoSkillCastAnim;
import org.agmas.noellesroles.game.modes.echo.content.EchoContent;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称的追猎动作与施法动作。
 *
 * <p>注入点在 {@code renderArmWithItem} 的 HEAD：此时 {@link PoseStack} 已经位于「手臂锚点」
 * （原版在调用前完成 hand 侧移、装备进度与手臂旋转），因此在它上面叠加位移/旋转就是相对手部的
 * 局部变换，不需要再复刻锚点常量。本 mixin 只叠加增量，不取消原版分支，
 * 所以蓄力时原版举矛姿势仍会叠加（观感上会抬得更高，与我们的抬刀曲线同向）。
 *
 * <p>动作来源分两类：
 * <ul>
 * <li>本地玩家蓄力：直接读 {@code getTicksUsingItem()}，没有网络延迟；</li>
 * <li>出击/挥空/施法：读 {@link EchoHuntClient} 的本地时间轴（服务器广播驱动）。</li>
 * </ul>
 *
 * <p>所有计算都是表现层，不写回任何状态。
 */
@Mixin(ItemInHandRenderer.class)
public class EchoHuntFirstPersonMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void echo$huntPose(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand,
                               float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack,
                               MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (player == null || stack.isEmpty()) return;
        int side = hand == InteractionHand.MAIN_HAND
                ? (player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1)
                : (player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1);

        // 施法：任何专长信物的右键都会播一段固定时长的施法动作。
        if (stack.is(EchoContent.SKILL_TOKEN) && hand == InteractionHand.MAIN_HAND) {
            EchoHuntClient.Action action = EchoHuntClient.action(player.getUUID());
            if (action != null && action.kind == EchoHuntPayload.Kind.CAST && action.style != null) {
                float progress = action.progress(System.nanoTime() / 1_000_000L);
                if (progress >= 0F) {
                    EchoSkillCastAnim.Pose pose = EchoSkillCastAnim.firstPerson(progress, action.style, side);
                    apply(poseStack, pose.tx(), pose.ty(), pose.tz(), pose.rx(), pose.ry(), pose.rz());
                }
            }
            return;
        }

        if (!stack.is(EchoContent.CUTTER)) return;

        if (player.isUsingItem() && player.getUsedItemHand() == hand) {
            var data = EchoRoleDataHolder.of(player);
            float charge = Math.min(1F, (player.getTicksUsingItem() + partialTicks)
                    / org.agmas.noellesroles.game.modes.echo.combat.EchoCutterStrike.CHARGE_TICKS);
            apply(poseStack, EchoHuntAnim.firstPersonCharge(charge, side, false));
            return;
        }
        EchoHuntClient.Action action = EchoHuntClient.action(player.getUUID());
        if (action == null || action.kind == EchoHuntPayload.Kind.CHARGE) return;
        float progress = action.progress(System.nanoTime() / 1_000_000L);
        if (progress < 0F) return;
        switch (action.kind) {
            case STRIKE -> apply(poseStack, EchoHuntAnim.firstPersonStrike(progress, action.empowered, side));
            case MISS -> apply(poseStack, EchoHuntAnim.firstPersonRecover(progress, true, side));
            // 擦刀：empowered 位复用为「是否命中」，挥空时只做小幅收刀。
            case WIPE -> apply(poseStack, EchoHuntAnim.firstPersonWipe(progress, !action.empowered, side));
            case INSTINCT -> {
                float[] pose = EchoHuntAnim.firstPersonCharge(0.55F, side, true);
                float fade = 1F - progress;
                for (int i = 0; i < pose.length; i++) pose[i] *= fade;
                apply(poseStack, pose);
            }
            default -> { }
        }
    }

    /** 把 6 元增量姿态应用到 PoseStack（先平移，再按 Z→Y→X 顺序旋转）。 */
    private static void apply(PoseStack poseStack, float[] pose) {
        if (pose == null || pose.length < 6) return;
        apply(poseStack, pose[0], pose[1], pose[2], pose[3], pose[4], pose[5]);
    }

    private static void apply(PoseStack poseStack, float tx, float ty, float tz, float rx, float ry, float rz) {
        if (tx != 0F || ty != 0F || tz != 0F) poseStack.translate(tx, ty, tz);
        if (rx == 0F && ry == 0F && rz == 0F) return;
        // 绕「手部锚点」旋转，而不是绕相机原点：否则大角度会把整只手臂甩出屏幕。
        // 锚点常量与项目既有第一人称格挡（KatanaAnim 调用处）一致。
        poseStack.translate(ANCHOR_X, ANCHOR_Y, ANCHOR_Z);
        if (rz != 0F) poseStack.mulPose(Axis.ZP.rotationDegrees(rz));
        if (ry != 0F) poseStack.mulPose(Axis.YP.rotationDegrees(ry));
        if (rx != 0F) poseStack.mulPose(Axis.XP.rotationDegrees(rx));
        poseStack.translate(-ANCHOR_X, -ANCHOR_Y, -ANCHOR_Z);
    }

    /** 原版手部锚点（与既有第一人称格挡姿势相同）。 */
    private static final float ANCHOR_X = 0.56F, ANCHOR_Y = -0.52F, ANCHOR_Z = -0.72F;

    /** 隔离对 RoleData 的直接依赖，避免在类加载期强绑定服务端数据类。 */
    private static final class EchoRoleDataHolder {
        private EchoRoleDataHolder() { }
        static EchoRoleData of(AbstractClientPlayer player) {
            return io.wifi.starrailexpress.api.data.RoleData.getNullable(EchoRoleData.class, player);
        }
    }
}