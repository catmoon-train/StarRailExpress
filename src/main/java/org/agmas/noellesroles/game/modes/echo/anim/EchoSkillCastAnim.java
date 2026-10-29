package org.agmas.noellesroles.game.modes.echo.anim;

import org.agmas.noellesroles.game.modes.echo.state.EchoSkill;

/**
 * 六个专长主动技能的施法动作曲线。
 *
 * <p>每个专长有一套独立的动作母题（不是同一个抬手换色），并且第一人称与第三人称分开给出：
 * <ul>
 * <li>{@link Style#PRESS} 稳载：双手下压，像把货物稳稳放下；</li>
 * <li>{@link Style#SNATCH} 顺手：从低处快速上挑，手腕一勾；</li>
 * <li>{@link Style#SCAN} 锁迹：把信物举到眼前，然后向前指认；</li>
 * <li>{@link Style#SLAM} 过载：高举过头再猛砸而下，动作带反弹；</li>
 * <li>{@link Style#CONCEAL} 暗格：向胸口内收并压腕，把东西藏进衣襟；</li>
 * <li>{@link Style#OFFER} 拍板：向外递出、掌心翻上，最后收势。</li>
 * </ul>
 *
 * <p>纯数学、无状态、不读世界；参数 {@code t} 是 0~1 的施法进度。
 */
public final class EchoSkillCastAnim {
    private EchoSkillCastAnim() { }

    /** 施法动作时长（tick），与网络包共用。 */
    public static final int CAST_TICKS = 16;

    public enum Style { PRESS, SNATCH, SCAN, SLAM, CONCEAL, OFFER, CLAW, WEAVE }

    /** 一组姿态增量：位移（格）+ 旋转（角度）。 */
    public record Pose(float tx, float ty, float tz, float rx, float ry, float rz) {
        public static final Pose NONE = new Pose(0F, 0F, 0F, 0F, 0F, 0F);
        public Pose mirror(int side) { return new Pose(tx * side, ty, tz, rx, ry * side, rz * side); }
        public Pose scale(float k) { return new Pose(tx * k, ty * k, tz * k, rx * k, ry * k, rz * k); }
    }

    public static Style style(EchoSkill skill) {
        if (skill == null) return Style.PRESS;
        return switch (skill) {
            case STEADY_HAUL -> Style.PRESS;
            case SWIFT_PICK -> Style.SNATCH;
            case LOCK_ON -> Style.SCAN;
            case STALL_BURST -> Style.SLAM;
            case DARK_CACHE -> Style.CONCEAL;
            case BROKER_DEAL -> Style.OFFER;
            case REND_DASH -> Style.CLAW;
            case WEB_NEST -> Style.WEAVE;
        };
    }

    /** 施法时信物附近的提示色（ARGB），供 HUD 与粒子共用。 */
    public static int glowColor(Style style) {
        return switch (style) {
            case PRESS -> 0xFFD4AF37;
            case SNATCH -> 0xFF7FD8C4;
            case SCAN -> 0xFFE06B65;
            case SLAM -> 0xFFFF8A3D;
            case CONCEAL -> 0xFF9B7BD4;
            case OFFER -> 0xFFFFE8C0;
            case CLAW -> 0xFFFFB4A8;
            case WEAVE -> 0xFF9FD8F0;
        };
    }

    // ───────────────────────── 采样工具 ─────────────────────────

    /**
     * 在关键帧上线性采样：{@code times} 单调递增且首尾为 0 与 1。
     * 值在关键帧之间线性插值，端点之外取端点值，因此不会越界。
     */
    public static Pose sample(float t, float[] times, Pose[] poses) {
        t = EchoHuntAnim.clamp01(t);
        int last = Math.min(times.length, poses.length) - 1;
        if (last < 0) return Pose.NONE;
        if (last == 0) return poses[0];
        for (int i = 0; i < last; i++) {
            float a = times[i], b = times[i + 1];
            if (t > b) continue;
            float span = b - a;
            float k = span <= 1.0E-5F ? 1F : EchoHuntAnim.clamp01((t - a) / span);
            return lerp(poses[i], poses[i + 1], k);
        }
        return poses[last];
    }

    private static Pose lerp(Pose a, Pose b, float k) {
        return new Pose(a.tx() + (b.tx() - a.tx()) * k, a.ty() + (b.ty() - a.ty()) * k,
                a.tz() + (b.tz() - a.tz()) * k, a.rx() + (b.rx() - a.rx()) * k,
                a.ry() + (b.ry() - a.ry()) * k, a.rz() + (b.rz() - a.rz()) * k);
    }

    // ───────────────────────── 第一人称 ─────────────────────────

    /**
     * 第一人称施法姿态。
     *
     * @param t     0~1
     * @param style 专长动作母题
     * @param side  +1 右手 / -1 左手
     */
    public static Pose firstPerson(float t, Style style, int side) {
        Pose pose = switch (style) {
            case PRESS -> sample(t, new float[]{0F, 0.30F, 0.62F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0.16F, -0.10F, -18F, 0F, 0F),
                    new Pose(0F, -0.10F, -0.20F, 34F, 0F, 0F),
                    Pose.NONE});
            case SNATCH -> sample(t, new float[]{0F, 0.18F, 0.42F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(-0.10F, -0.12F, 0F, 26F, -24F, -18F),
                    new Pose(0.14F, 0.20F, -0.16F, -34F, 30F, 26F),
                    Pose.NONE});
            case SCAN -> sample(t, new float[]{0F, 0.24F, 0.55F, 0.80F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0.22F, -0.06F, -46F, 0F, 0F),
                    new Pose(0F, 0.20F, -0.10F, -58F, 0F, 0F),
                    new Pose(0F, 0.02F, -0.34F, -96F, 0F, 0F),
                    Pose.NONE});
            case SLAM -> sample(t, new float[]{0F, 0.30F, 0.48F, 0.70F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0.34F, 0F, -104F, 0F, 0F),
                    new Pose(0F, -0.22F, -0.26F, 62F, 0F, 0F),
                    new Pose(0F, -0.06F, -0.20F, 30F, 0F, 0F),
                    Pose.NONE});
            case CONCEAL -> sample(t, new float[]{0F, 0.34F, 0.66F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(-0.06F, 0.06F, -0.04F, -46F, -30F, -22F),
                    new Pose(-0.10F, 0.00F, 0.02F, -30F, -46F, -34F),
                    Pose.NONE});
            case OFFER -> sample(t, new float[]{0F, 0.28F, 0.58F, 0.82F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0.10F, -0.36F, -8F, -18F, -24F),
                    new Pose(0.05F, 0.06F, -0.42F, -2F, 12F, 18F),
                    new Pose(0F, 0.04F, -0.30F, -4F, 20F, 8F),
                    Pose.NONE});
            case CLAW -> sample(t, new float[]{0F, 0.22F, 0.46F, 0.74F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0.10F, 0.06F, 24F, 30F, 26F),
                    new Pose(0.06F, 0.02F, -0.30F, -34F, -40F, -34F),
                    new Pose(0.02F, -0.04F, -0.18F, -12F, -16F, -12F),
                    Pose.NONE});
            case WEAVE -> sample(t, new float[]{0F, 0.30F, 0.58F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0.28F, -0.05F, -30F, 0F, 0F),
                    new Pose(0F, -0.22F, -0.24F, 52F, 0F, 0F),
                    Pose.NONE});
        };
        return pose.mirror(side);
    }

    // ───────────────────────── 第三人称 ─────────────────────────

    /** 第三人称手臂姿态（弧度），叠加在 vanilla 手臂姿势之后。 */
    public static float[] thirdPersonArm(float t, Style style, int side) {
        Pose pose = switch (style) {
            case PRESS -> sample(t, new float[]{0F, 0.30F, 0.62F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -1.05F, 0F, 0.10F),
                    new Pose(0F, 0F, 0F, 0.62F, 0F, -0.18F),
                    Pose.NONE});
            case SNATCH -> sample(t, new float[]{0F, 0.18F, 0.42F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, 0.55F, 0.45F, -0.35F),
                    new Pose(0F, 0F, 0F, -1.35F, -0.55F, 0.55F),
                    Pose.NONE});
            case SCAN -> sample(t, new float[]{0F, 0.24F, 0.55F, 0.80F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -1.15F, -0.20F, -0.30F),
                    new Pose(0F, 0F, 0F, -1.40F, -0.10F, -0.42F),
                    new Pose(0F, 0F, 0F, -1.55F, 0.12F, -0.10F),
                    Pose.NONE});
            case SLAM -> sample(t, new float[]{0F, 0.30F, 0.48F, 0.70F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -2.55F, 0F, 0.15F),
                    new Pose(0F, 0F, 0F, 0.95F, 0F, -0.10F),
                    new Pose(0F, 0F, 0F, 0.45F, 0F, -0.05F),
                    Pose.NONE});
            case CONCEAL -> sample(t, new float[]{0F, 0.34F, 0.66F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -0.75F, 0.70F, -0.55F),
                    new Pose(0F, 0F, 0F, -0.45F, 0.95F, -0.75F),
                    Pose.NONE});
            case OFFER -> sample(t, new float[]{0F, 0.28F, 0.58F, 0.82F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -1.30F, -0.35F, -0.20F),
                    new Pose(0F, 0F, 0F, -1.36F, 0.30F, 0.15F),
                    new Pose(0F, 0F, 0F, -1.10F, 0.42F, 0.05F),
                    Pose.NONE});
            case CLAW -> sample(t, new float[]{0F, 0.22F, 0.46F, 0.74F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -0.90F, 0.55F, 0.35F),
                    new Pose(0F, 0F, 0F, 0.85F, -0.70F, -0.50F),
                    new Pose(0F, 0F, 0F, 0.35F, -0.30F, -0.20F),
                    Pose.NONE});
            case WEAVE -> sample(t, new float[]{0F, 0.30F, 0.58F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -1.20F, 0F, 0.15F),
                    new Pose(0F, 0F, 0F, 0.95F, 0F, -0.10F),
                    Pose.NONE});
        };
        return new float[]{pose.rx(), pose.ry() * side, pose.rz() * side};
    }

    /** 第三人称手持信物模型自身的朝向（角度）。 */
    public static Pose thirdPersonItem(float t, Style style, int side) {
        Pose pose = switch (style) {
            case PRESS -> sample(t, new float[]{0F, 0.30F, 0.62F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, 42F, 0F, 0F),
                    new Pose(0F, 0F, 0F, -34F, 0F, 0F),
                    Pose.NONE});
            case SNATCH -> sample(t, new float[]{0F, 0.18F, 0.42F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -30F, 40F, 30F),
                    new Pose(0F, 0F, 0F, 55F, -46F, -38F),
                    Pose.NONE});
            case SCAN -> sample(t, new float[]{0F, 0.24F, 0.55F, 0.80F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, 30F, 10F, 18F),
                    new Pose(0F, 0F, 0F, 38F, 6F, 26F),
                    new Pose(0F, 0F, 0F, 74F, -8F, 8F),
                    Pose.NONE});
            case SLAM -> sample(t, new float[]{0F, 0.30F, 0.48F, 0.70F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -70F, 0F, -14F),
                    new Pose(0F, 0F, 0F, 88F, 0F, 10F),
                    new Pose(0F, 0F, 0F, 46F, 0F, 4F),
                    Pose.NONE});
            case CONCEAL -> sample(t, new float[]{0F, 0.34F, 0.66F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, 24F, -52F, -40F),
                    new Pose(0F, 0F, 0F, 12F, -74F, -58F),
                    Pose.NONE});
            case OFFER -> sample(t, new float[]{0F, 0.28F, 0.58F, 0.82F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, 62F, 24F, 30F),
                    new Pose(0F, 0F, 0F, 68F, -20F, -18F),
                    new Pose(0F, 0F, 0F, 54F, -28F, -6F),
                    Pose.NONE});
            case CLAW -> sample(t, new float[]{0F, 0.22F, 0.46F, 0.74F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -40F, 34F, 30F),
                    new Pose(0F, 0F, 0F, 62F, -40F, -34F),
                    new Pose(0F, 0F, 0F, 24F, -16F, -12F),
                    Pose.NONE});
            case WEAVE -> sample(t, new float[]{0F, 0.30F, 0.58F, 1F}, new Pose[]{
                    Pose.NONE,
                    new Pose(0F, 0F, 0F, -46F, 0F, 18F),
                    new Pose(0F, 0F, 0F, 74F, 0F, -10F),
                    Pose.NONE});
        };
        return new Pose(pose.tx(), pose.ty(), pose.tz(), pose.rx(), pose.ry() * side, pose.rz() * side);
    }
}