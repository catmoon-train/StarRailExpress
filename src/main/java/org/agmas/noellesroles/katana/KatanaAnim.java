package org.agmas.noellesroles.katana;

import net.minecraft.util.Mth;

/**
 * 武士刀的动画曲线：横扫 / 突刺 / 劈砍 / 格挡。
 * 全部是纯数学，双端都会被引用，但不依赖任何客户端逻辑类
 * （与 {@code SpearAnim} 相同的组织方式）。
 *
 * <p>所有函数的 {@code progress} 参数范围均为 {@code 0.0F ~ 1.0F}，
 * 表示招式动画的推进进度。
 */
public final class KatanaAnim {

    private KatanaAnim() {
    }

    // ───────────────────────── 缓动工具 ─────────────────────────

    /** 平滑缓出（先快后慢）。 */
    public static float easeOut(float p) {
        p = Mth.clamp(p, 0.0F, 1.0F);
        return 1.0F - (1.0F - p) * (1.0F - p);
    }

    /** 平滑缓入缓出。 */
    public static float easeInOut(float p) {
        p = Mth.clamp(p, 0.0F, 1.0F);
        return p * p * (3.0F - 2.0F * p);
    }

    /**
     * 三段式权重：把总进度拆成「前段 windup → 中段 strike → 后段 recover」三个
     * 归一化子进度，用于构造「就位 → 出招 → 收招」的招式曲线。
     *
     * @return {@code [windup, strike, recover]} 三个子进度（各自 0~1）
     */
    public static float[] phases(float progress, float windupEnd, float strikeEnd) {
        float windup = Mth.clamp(Mth.inverseLerp(progress, 0.0F, windupEnd), 0.0F, 1.0F);
        float strike = Mth.clamp(Mth.inverseLerp(progress, windupEnd, strikeEnd), 0.0F, 1.0F);
        float recover = Mth.clamp(Mth.inverseLerp(progress, strikeEnd, 1.0F), 0.0F, 1.0F);
        return new float[]{easeInOut(windup), easeInOut(strike), easeInOut(recover)};
    }

    // ───────────────────────── 第一人称 ─────────────────────────

    /**
     * 第一人称横扫：抬臂从左侧就位 → 水平弧线从左扫到右 → 收招。
     * 表现为物品横向平移 + 大幅绕 Y 轴旋转 + 翻腕把刀身放平。
     */
    public static void applyFirstPersonSweep(com.mojang.blaze3d.vertex.PoseStack poseStack, float progress) {
        float[] ph = phases(progress, 0.20F, 0.55F);
        float wind = ph[0], strike = ph[1], recover = ph[2];
        // 横向：先到左侧 (-)，扫到右侧 (+)，再收回
        float x = -0.35F * wind + 0.70F * strike - 0.35F * recover;
        // 弧线：绕 Y 轴从左前方扫到右前方（大幅）
        float yaw = -80.0F * wind + 160.0F * strike - 80.0F * recover;
        // 纵向：略微下压再回正
        float y = -0.06F * wind + 0.10F * strike - 0.04F * recover;
        // 翻腕：横扫时把刀身放平（大幅滚转），扫完回正
        float roll = -70.0F * wind + 95.0F * strike - 25.0F * recover;
        poseStack.translate(x, y, 0.0F);
        mulPoseAround(poseStack, com.mojang.math.Axis.YP.rotationDegrees(yaw), 0.0F, 0.1F, 0.0F);
        mulPoseAround(poseStack, com.mojang.math.Axis.ZP.rotationDegrees(roll), 0.0F, 0.0F, 0.0F);
    }

    /** 第一人称突刺：直接复用矛的直刺曲线（向前推刺 + 收回）。 */
    public static void applyFirstPersonThrust(com.mojang.blaze3d.vertex.PoseStack poseStack, float progress) {
        org.agmas.noellesroles.spear.SpearAnim.applyFirstPersonStab(poseStack, progress);
    }

    /**
     * 第一人称劈砍：双手举刀过顶 → 从上至下劈落 → 收招。
     * 表现为物品先大幅上举（刀身向后仰），再快速下劈（刀身向前倒）。
     */
    public static void applyFirstPersonSlash(com.mojang.blaze3d.vertex.PoseStack poseStack, float progress) {
        float[] ph = phases(progress, 0.25F, 0.60F);
        float wind = ph[0], strike = ph[1], recover = ph[2];
        // 举刀：上抬；劈落：快速下压
        float y = 0.30F * wind - 0.55F * strike + 0.25F * recover;
        // 绕 X 轴：先大幅向上翻举（负角度为向上），再向下劈
        float pitch = -110.0F * wind + 170.0F * strike - 60.0F * recover;
        // 纵深：举刀时略拉回，劈落时送出
        float z = -0.12F * wind + 0.18F * strike - 0.06F * recover;
        poseStack.translate(0.0F, y, z);
        mulPoseAround(poseStack, com.mojang.math.Axis.XP.rotationDegrees(pitch), 0.0F, 0.05F, 0.0F);
    }

    /**
     * 第一人称格挡：抬臂把刀竖在身前，刀尖指向左下。
     *
     * @param raise 抬臂进度 0~1（前摇期间从 0 涨到 1）
     * @param side  1 = 右手 / -1 = 左手
     */
    public static void applyFirstPersonBlock(com.mojang.blaze3d.vertex.PoseStack poseStack, float raise, int side) {
        raise = easeOut(Mth.clamp(raise, 0.0F, 1.0F));
        // 抬臂：物品向屏幕中上移动
        poseStack.translate(side * -0.10F * raise, 0.18F * raise, -0.08F * raise);
        // 翻腕：刀尖转向左下（绕 Z 轴倾斜 + 绕 Y 轴内收）
        mulPoseAround(poseStack, com.mojang.math.Axis.ZP.rotationDegrees(-40.0F * raise), 0.0F, 0.0F, 0.0F);
        mulPoseAround(poseStack, com.mojang.math.Axis.YP.rotationDegrees(side * -55.0F * raise), 0.0F, 0.0F, 0.0F);
        mulPoseAround(poseStack, com.mojang.math.Axis.XP.rotationDegrees(-20.0F * raise), 0.0F, 0.0F, 0.0F);
    }

    // ───────────────────────── 第三人称 ─────────────────────────

    /**
     * 第三人称横扫：手臂前平举，绕身体水平扫动（右 → 左 → 右由调用方决定符号）。
     *
     * @return {@code [xRot, yRot, zRot]} 手臂欧拉角增量（弧度）
     */
    public static float[] thirdPersonSweep(float progress, int side) {
        float[] ph = phases(progress, 0.20F, 0.55F);
        float wind = ph[0], strike = ph[1], recover = ph[2];
        float yRot = (-0.9F * wind + 1.8F * strike - 0.9F * recover) * side;
        return new float[]{-1.45F, yRot, 0.0F};
    }

    /** 第三人称突刺：手臂伸直向前（与矛的姿态一致）。 */
    public static float[] thirdPersonThrust(float progress, float headPitch) {
        float[] ph = phases(progress, 0.05F, 0.35F);
        float strike = ph[1], recover = ph[2];
        float xRot = (-(float) Math.PI / 2.0F) + headPitch
                + 0.4F * strike - 0.4F * recover;
        return new float[]{xRot, -0.1F, 0.0F};
    }

    /**
     * 第三人称劈砍：双臂举刀过顶 → 向前下方劈落。
     *
     * @return 主手 {@code [xRot, yRot, zRot]}（弧度），副手由调用方按镜像处理
     */
    public static float[] thirdPersonSlash(float progress) {
        float[] ph = phases(progress, 0.25F, 0.60F);
        float wind = ph[0], strike = ph[1], recover = ph[2];
        float xRot = -2.9F * wind + 2.2F * strike + 0.7F * recover;
        return new float[]{xRot, 0.0F, 0.0F};
    }

    /**
     * 第三人称劈砍：双手「向内聚拢」的权重曲线（配合角度常数使用）。
     *
     * <p>
     * 举刀（windup）时略向外张开（负值），劈落（strike）时快速向内聚拢到峰值，
     * 收招（recover）平滑回到自然。聚拢的方向约定与手铐前铐姿势一致
     * （{@code HandCuffsPoseMixin}：右臂 yRot/zRot 取负、左臂取正，双手向中线收）。
     *
     * @return 聚拢权重，约 -0.35 ~ +0.65
     */
    public static float slashConverge(float progress) {
        float[] ph = phases(progress, 0.25F, 0.60F);
        float wind = ph[0], strike = ph[1], recover = ph[2];
        return -0.35F * wind + 1.0F * strike - 0.65F * recover;
    }

    /**
     * 第三人称格挡：右臂抬起横在胸前，刀尖斜向左下。
     *
     * @return {@code [xRot, yRot, zRot]}（弧度）
     */
    public static float[] thirdPersonBlock(float raise) {
        raise = easeOut(Mth.clamp(raise, 0.0F, 1.0F));
        float xRot = -0.4F * raise - 1.5F * raise;
        float yRot = -0.55F * raise;
        float zRot = -0.30F * raise;
        return new float[]{xRot, yRot, zRot};
    }

    // ─────────────────── 第三人称物品模型朝向 ───────────────────
    // 参考矛（ItemInHandRendererSpearMixin#spear$renderThirdPerson）的符号约定：
    // 第三人称物品坐标系里，XN 正角度 = 刀身向前倒，YP = 横向转刀，ZP = 翻腕滚转。

    /** 第三人称突刺：刀身随突刺前指（与矛的直刺同款前倒角度）。 */
    public static void applyThirdPersonThrustItem(com.mojang.blaze3d.vertex.PoseStack poseStack, float progress) {
        float[] ph = phases(progress, 0.05F, 0.35F);
        float strike = ph[1], recover = ph[2];
        mulPoseAround(poseStack, com.mojang.math.Axis.XN.rotationDegrees(80.0F * (strike - recover)),
                0.0F, -0.125F, 0.125F);
    }

    /** 第三人称横扫：刀身放平（前倒）并随横扫轨迹横向转刀、翻腕。 */
    public static void applyThirdPersonSweepItem(com.mojang.blaze3d.vertex.PoseStack poseStack, float progress,
            int side) {
        float[] ph = phases(progress, 0.20F, 0.55F);
        float wind = ph[0], strike = ph[1], recover = ph[2];
        mulPoseAround(poseStack, com.mojang.math.Axis.XN.rotationDegrees(65.0F * strike - 15.0F * recover),
                0.0F, -0.125F, 0.125F);
        mulPoseAround(poseStack, com.mojang.math.Axis.YP.rotationDegrees(
                        side * (-50.0F * wind + 100.0F * strike - 50.0F * recover)),
                0.0F, 0.0F, 0.125F);
        mulPoseAround(poseStack, com.mojang.math.Axis.ZP.rotationDegrees(side * -35.0F * strike),
                0.0F, 0.0F, 0.0F);
    }

    /** 第三人称劈砍：举刀过顶（刀身后仰）→ 随劈落刀身大幅向前下倒。 */
    public static void applyThirdPersonSlashItem(com.mojang.blaze3d.vertex.PoseStack poseStack, float progress) {
        float[] ph = phases(progress, 0.25F, 0.60F);
        float wind = ph[0], strike = ph[1], recover = ph[2];
        mulPoseAround(poseStack, com.mojang.math.Axis.XN.rotationDegrees(
                        -110.0F * wind + 160.0F * strike - 50.0F * recover),
                0.0F, -0.125F, 0.125F);
        mulPoseAround(poseStack, com.mojang.math.Axis.ZP.rotationDegrees(-25.0F * strike),
                0.0F, 0.0F, 0.0F);
    }

    /** 第三人称格挡：抬臂翻腕，刀尖转向左下。 */
    public static void applyThirdPersonBlockItem(com.mojang.blaze3d.vertex.PoseStack poseStack, float raise,
            int side) {
        raise = easeOut(Mth.clamp(raise, 0.0F, 1.0F));
        mulPoseAround(poseStack, com.mojang.math.Axis.ZP.rotationDegrees(-45.0F * raise),
                0.0F, 0.0F, 0.0F);
        mulPoseAround(poseStack, com.mojang.math.Axis.YP.rotationDegrees(side * -60.0F * raise),
                0.0F, 0.0F, 0.0F);
        mulPoseAround(poseStack, com.mojang.math.Axis.XN.rotationDegrees(25.0F * raise),
                0.0F, -0.125F, 0.125F);
    }

    /** 绕指定枢轴旋转（本版本的 PoseStack 没有 mulPose(Quaternionf, x, y, z)）。 */
    public static void mulPoseAround(com.mojang.blaze3d.vertex.PoseStack poseStack,
            org.joml.Quaternionf rotation, float x, float y, float z) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(rotation);
        poseStack.translate(-x, -y, -z);
    }
}
