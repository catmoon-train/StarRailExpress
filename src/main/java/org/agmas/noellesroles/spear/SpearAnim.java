package org.agmas.noellesroles.spear;

import net.minecraft.util.Mth;

/**
 * 矛的动画曲线：举矛蓄力（抬 / 稳 / 收 / 回摆）与直刺（前刺 / 收回）。
 * 全部是纯数学，双端都会被引用，但不依赖任何客户端类。
 */
public final class SpearAnim {

    private SpearAnim() {
    }

    /**
     * 举矛蓄力动画的各阶段进度。
     *
     * @param raiseProgress      抬起总进度
     * @param raiseProgressStart 抬起前段
     * @param raiseProgressMiddle 抬起中段
     * @param raiseProgressEnd   抬起末段
     * @param swayProgress       回摆进度
     * @param lowerProgress      下压进度
     * @param raiseBackProgress  收招回位进度
     * @param swayIntensity      回摆强度
     * @param swayScaleSlow      慢速摆动
     * @param swayScaleFast      快速摆动
     */
    public record HoldUpAnimation(
            float raiseProgress,
            float raiseProgressStart,
            float raiseProgressMiddle,
            float raiseProgressEnd,
            float swayProgress,
            float lowerProgress,
            float raiseBackProgress,
            float swayIntensity,
            float swayScaleSlow,
            float swayScaleFast) {

        public static HoldUpAnimation play(SpearComponents.KineticWeapon kinetic, float useTicks) {
            int delay = kinetic.delayTicks();
            int dismountEnd = kinetic.dismountConditions()
                    .map(SpearComponents.KineticWeapon.Condition::maxDurationTicks).orElse(0) + delay;
            int knockbackEnd = kinetic.knockbackConditions()
                    .map(SpearComponents.KineticWeapon.Condition::maxDurationTicks).orElse(0) + delay;
            int damageEnd = kinetic.damageConditions()
                    .map(SpearComponents.KineticWeapon.Condition::maxDurationTicks).orElse(0) + delay;

            float f = useTicks;
            float g = Mth.clamp(Mth.inverseLerp(f, 0.0F, delay), 0.0F, 1.0F);
            float h = Mth.clamp(Mth.inverseLerp(g, 0.0F, 0.5F), 0.0F, 1.0F);
            float m = Mth.clamp(Mth.inverseLerp(g, 0.5F, 0.8F), 0.0F, 1.0F);
            float n = Mth.clamp(Mth.inverseLerp(g, 0.8F, 1.0F), 0.0F, 1.0F);
            float o = Mth.clamp(Mth.inverseLerp(f, dismountEnd, knockbackEnd), 0.0F, 1.0F);
            float p = Mth.clamp(Mth.inverseLerp(f, knockbackEnd, damageEnd - 5), 0.0F, 1.0F);
            if (p < 0.0F) {
                p = 0.0F;
            } else if (p > 1.0F) {
                p = 1.0F;
            } else {
                double d = Math.sin((20.0 * p - 11.125) * (float) Math.PI * 4.0F / 9.0F);
                p = p < 0.5F ? (float) (-(Math.pow(2.0, 20.0 * p - 10.0) * d) / 2.0)
                        : (float) (Math.pow(2.0, -20.0 * p + 10.0) * d / 2.0 + 1.0);
            }
            p = 1.0F - p * p * p;
            float q = Mth.clamp(Mth.inverseLerp(f, damageEnd - 5, damageEnd), 0.0F, 1.0F);
            float r = 2.0F * (float) Math.sqrt(1.0F + Mth.square(1.0F - o))
                    - 2.0F * ((-Mth.sqrt(1.0F - o * o)) + 1.0F);
            float s = Mth.sin(f * 19.0F * (float) (Math.PI / 180.0));
            float t = Mth.sin(f * 30.0F * (float) (Math.PI / 180.0));
            float u = Mth.clamp((1.0F - f / Math.max(Math.max(dismountEnd, knockbackEnd), damageEnd)) * 20.0F, 0.0F,
                    1.0F) * (2.9F - r);
            return new HoldUpAnimation(g, h, m, n, o, p, q, r, s * u, t * u);
        }
    }

    /** 直刺动画的前段（0 ~ 0.05 进度）。 */
    private static float stabPullBack(float progress) {
        return -(Mth.cos((float) Math.PI * Mth.clamp(Mth.inverseLerp(progress, 0.0F, 0.05F), 0.0F, 1.0F)) - 1.0F) / 2.0F;
    }

    /** 直刺动画的中段（0.05 ~ 0.2 进度，平方曲线）。 */
    private static float stabForward(float progress) {
        float h = Mth.clamp(Mth.inverseLerp(progress, 0.05F, 0.2F), 0.0F, 1.0F);
        return h * h;
    }

    /** 直刺动画的收段（0.4 ~ 1.0 进度，缓入缓出）。 */
    private static float stabRecover(float progress) {
        float j = Mth.clamp(Mth.inverseLerp(progress, 0.4F, 1.0F), 0.0F, 1.0F);
        if (j < 0.5F) {
            return j == 0.0F ? 0.0F : (float) (Math.pow(2.0F, 20.0F * j - 10.0F) / 2.0F);
        }
        return j == 1.0F ? 1.0F : (float) ((2.0F - Math.pow(2.0F, -20.0F * j + 10.0F)) / 2.0F);
    }

    /** 第一人称直刺：把物品向前推并略微上扬。 */
    public static void applyFirstPersonStab(com.mojang.blaze3d.vertex.PoseStack poseStack, float swingProgress) {
        float g = stabPullBack(swingProgress);
        float h = stabForward(swingProgress);
        float j = stabRecover(swingProgress);
        poseStack.translate(j * 0.1F * (g - h), -0.075F * (g - j), 0.65F * (g - h));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-70.0F * (g - j)));
        poseStack.translate(0.0F, 0.0F, -0.25F * (j - h));
    }

    /** 绕指定枢轴旋转（本版本的 PoseStack 没有 mulPose(Quaternionf, x, y, z)）。 */
    public static void mulPoseAround(com.mojang.blaze3d.vertex.PoseStack poseStack, org.joml.Quaternionf rotation,
            float x, float y, float z) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(rotation);
        poseStack.translate(-x, -y, -z);
    }

    /** 第三人称直刺：手臂前刺（由实体模型调用）。 */
    public static void applyThirdPersonStab(com.mojang.blaze3d.vertex.PoseStack poseStack, float progress,
            float forwardMovement) {
        float i = stabForward(progress);
        float j = stabRecover(progress);
        mulPoseAround(poseStack, com.mojang.math.Axis.XN.rotationDegrees(70.0F * (i - j)), 0.0F, -0.125F, 0.125F);
        poseStack.translate(0.0F, forwardMovement * (i - j), 0.0F);
    }

    /** 第三人称直刺：手臂俯仰增量（单位：弧度）。 */
    public static float thirdPersonArmPitch(float progress) {
        float g = stabPullBack(progress);
        float h = stabForward(progress);
        float j = stabRecover(progress);
        return (90.0F * g - 120.0F * h + 30.0F * j) * ((float) Math.PI / 180.0F);
    }
}
