package org.agmas.noellesroles.mixin.client.echo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntAnim;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntClient;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntPayload;
import org.agmas.noellesroles.game.modes.echo.anim.EchoSkillCastAnim;
import org.agmas.noellesroles.game.modes.echo.content.EchoContent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称的追猎/施法手臂姿势：其他玩家看到的追捕者也有完整的「抬刀—劈砍—后摇」。
 *
 * <p>沿用武士刀 mixin 的做法，在 {@code poseRightArm/poseLeftArm} 的 TAIL 直接覆写
 * {@link ModelPart} 的欧拉角；因为只处理手持回声工具的实体，原版姿势在其它情况下不受影响。
 *
 * <p>优先级：施法 &gt; 蓄力（使用中）&gt; 出击/后摇（网络动作）。
 */
@Mixin(HumanoidModel.class)
public abstract class EchoHuntThirdPersonMixin<T extends LivingEntity> {

    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void echo$poseRightArm(T entity, CallbackInfo ci) {
        echo$poseArm(entity, this.rightArm, HumanoidArm.RIGHT);
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void echo$poseLeftArm(T entity, CallbackInfo ci) {
        echo$poseArm(entity, this.leftArm, HumanoidArm.LEFT);
    }

    private void echo$poseArm(T entity, ModelPart arm, HumanoidArm humanoidArm) {
        ItemStack held = entity.getMainArm() == humanoidArm ? entity.getMainHandItem() : entity.getOffhandItem();
        if (held.isEmpty()) return;
        boolean token = held.is(EchoContent.SKILL_TOKEN);
        boolean cutter = held.is(EchoContent.CUTTER);
        if (!token && !cutter) return;
        int side = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
        InteractionHand hand = entity.getMainArm() == humanoidArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        // 1) 施法：任何专长信物。
        if (token) {
            EchoHuntClient.Action action = EchoHuntClient.action(entity.getUUID());
            if (action != null && action.kind == EchoHuntPayload.Kind.CAST && action.style != null) {
                float progress = action.progress(System.nanoTime() / 1_000_000L);
                if (progress >= 0F) {
                    float[] pose = EchoSkillCastAnim.thirdPersonArm(progress, action.style, side);
                    arm.xRot += pose[0];
                    arm.yRot += pose[1];
                    arm.zRot += pose[2];
                }
            }
            return;
        }

        // 2) 使用中持续抬刀，蓄满后保持，直到松手。
        if (cutter && entity.isUsingItem() && entity.getUsedItemHand() == hand) {
            float charge = Math.min(1F, (entity.getTicksUsingItem() + partialTick()) / chargeTicks(entity));
            float[] pose = EchoHuntAnim.thirdPersonCharge(charge, side, false);
            arm.xRot = pose[0];
            arm.yRot = pose[1];
            arm.zRot = pose[2];
            return;
        }
        // 3) 出击与后摇。
        EchoHuntClient.Action action = EchoHuntClient.action(entity.getUUID());
        if (action == null) return;
        float progress = action.progress(System.nanoTime() / 1_000_000L);
        if (progress < 0F) return;
        float[] pose = switch (action.kind) {
            case STRIKE -> EchoHuntAnim.thirdPersonStrike(progress, action.empowered, side);
            case MISS -> EchoHuntAnim.thirdPersonRecover(progress, true, side);
            case WIPE -> EchoHuntAnim.thirdPersonWipe(progress, !action.empowered, side);
            case INSTINCT -> {
                float[] base = EchoHuntAnim.thirdPersonCharge(0.55F, side, true);
                float fade = 1F - progress;
                yield new float[]{base[0] * fade, base[1] * fade, base[2] * fade};
            }
            default -> null;
        };
        if (pose == null) return;
        arm.xRot = pose[0];
        arm.yRot = pose[1];
        arm.zRot = pose[2];
    }

    /** 所有玩家的蓄力动作统一为 0.8 秒。 */
    private static int chargeTicks(LivingEntity entity) {
        return org.agmas.noellesroles.game.modes.echo.combat.EchoCutterStrike.CHARGE_TICKS;
    }

    /** 单帧部分 tick，用于让动画在 20 Hz 的实体 tick 之间也平滑。 */
    private static float partialTick() {
        return net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }
}