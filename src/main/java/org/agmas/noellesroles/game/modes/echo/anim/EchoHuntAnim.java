package org.agmas.noellesroles.game.modes.echo.anim;

/**
 * 追捕者「狩猎循环」的表现曲线。
 *
 * <p>与权威战斗结算严格分离：这里只有纯数学，不读世界、不读网络、
 * 不持有任何状态，因此可以被单元测试完整覆盖，也不会在客户端与服务端之间产生判定分歧。
 *
 * <p>返回值约定统一为「增量」而不是绝对值：
 * <ul>
 * <li>第一人称 {@code [tx,ty,tz,rx,ry,rz]}：位移（格）与欧拉角（度），叠加在原版手臂锚点上；</li>
 * <li>第三人称手臂 {@code [xRot,yRot,zRot]}：弧度，叠加在 {@code HumanoidModel} 的 vanilla 姿势之后；</li>
 * <li>第三人称物品 {@code [tx,ty,tz,degX,degY,degZ]}：物品模型自身的位移与旋转（度）。</li>
 * </ul>
 *
 * <p>时间参数 {@code t} 一律是 0~1 的相位进度：蓄力是 0→1（越靠后越满），
 * 出击是 0→1（0 为起手瞬间），后摇是 0→1（0 为刚进后摇，1 为完全恢复）。
 */
public final class EchoHuntAnim {
    private EchoHuntAnim() { }

    /** 蓄力三段分界：抬刀结束 / 蓄势结束（之后只剩颤抖）。 */
    public static final float CHARGE_LIFT = 0.55F, CHARGE_COIL = 0.85F;
    /** 出击弧线的起手占比与总时长参考（仅用于曲线形状，不是权威 tick）。 */
    public static final float STRIKE_WINDUP = 0.18F;
    /** 满锁定时的额外幅度倍率。 */
    public static final float EMPOWERED_SCALE = 1.20F;

    public static float clamp01(float value) { return value < 0F ? 0F : value > 1F ? 1F : value; }

    /** 先快后慢。 */
    public static float easeOut(float p) { p = clamp01(p); float inv = 1F - p; return 1F - inv * inv; }

    /** 先慢后快。 */
    public static float easeIn(float p) { p = clamp01(p); return p * p; }

    /** 平滑两端。 */
    public static float easeInOut(float p) { p = clamp01(p); return p * p * (3F - 2F * p); }

    /** 0→1→0 的单峰弧线，用于「挥出去再收回来」这类一次性动作。 */
    public static float arc(float p) { return (float) Math.sin(Math.PI * clamp01(p)); }

    /**
     * 蓄力的三段权重。
     *
     * @return {@code [lift, coil, tremble]}，各自 0~1；只在前一段完成后才推进下一段
     */
    public static float[] chargePhases(float t) {
        t = clamp01(t);
        float lift = easeInOut(clamp01(t / CHARGE_LIFT));
        float coil = easeInOut(clamp01((t - CHARGE_LIFT) / (CHARGE_COIL - CHARGE_LIFT)));
        float tremble = easeInOut(clamp01((t - CHARGE_COIL) / (1F - CHARGE_COIL)));
        return new float[]{lift, coil, tremble};
    }

    /**
     * 第一人称蓄力：把刀从腰侧抬到肩后，锁定越满抖得越明显。
     *
     * @param side      +1 右手 / -1 左手
     * @param empowered 本次出击是否为满锁定扑击（幅度更大、抬得更高）
     */
    public static float[] firstPersonCharge(float t, int side, boolean empowered) {
        float[] ph = chargePhases(t);
        float lift = ph[0], coil = ph[1], tremble = ph[2];
        float amp = empowered ? EMPOWERED_SCALE : 1F;
        // 颤抖用固定频率的 sin(t)，保证同一进度得到同一结果（可测、不依赖系统时间）。
        float shake = tremble * (float) Math.sin(clamp01(t) * 46F) * 2.2F;
        return new float[]{
                side * (-0.07F * lift + 0.03F * coil) * amp,
                0.13F * lift + 0.04F * coil,
                -0.11F * lift + 0.15F * coil,
                (-42F * lift - 24F * coil) * amp,
                side * (11F * lift - 27F * coil),
                side * (-9F * lift - 21F * coil + shake)
        };
    }

    /**
     * 第一人称出击：极短的压腕前摇，随后一刀向前下方挥出，末尾自然回正。
     *
     * @param t 0 = 松手瞬间，1 = 出击窗口结束
     */
    public static float[] firstPersonStrike(float t, boolean empowered, int side) {
        t = clamp01(t);
        // 前摇分量在出击过程中同步回落，保证 t=1 时完全回到持刀待机（后摇从零开始）。
        float wind = easeIn(clamp01(t / STRIKE_WINDUP)) * (1F - t);
        float sweep = arc((t - STRIKE_WINDUP * 0.5F) / (1F - STRIKE_WINDUP * 0.5F));
        float amp = empowered ? EMPOWERED_SCALE : 1F;
        return new float[]{
                side * (0.06F * wind + 0.16F * sweep * amp),
                -0.03F * wind - 0.18F * sweep * amp,
                -0.10F * wind - 0.40F * sweep * amp,
                -26F * wind + 96F * sweep * amp,
                side * (16F * wind - 52F * sweep),
                side * (14F * wind - 40F * sweep)
        };
    }

    /**
     * 第一人称后摇：从出击末尾的姿态缓慢回到持刀待机；挥空时下垂更明显。
     *
     * @param t 0 = 刚进后摇，1 = 完全恢复
     */
    public static float[] firstPersonRecover(float t, boolean miss, int side) {
        float decay = 1F - easeOut(t);
        float amp = miss ? 1.5F : 1F;
        return new float[]{
                0F,
                (-0.05F - (miss ? 0.10F : 0F)) * decay * amp,
                -0.09F * decay * amp,
                22F * decay * amp,
                side * -14F * decay,
                side * 11F * decay
        };
    }

    /** 第三人称蓄力：持刀臂抬过肩、肘部外张，扑击时抬得更高。 */
    public static float[] thirdPersonCharge(float t, int side, boolean empowered) {
        float[] ph = chargePhases(t);
        float lift = ph[0], coil = ph[1], tremble = ph[2];
        float amp = empowered ? 1.15F : 1F;
        float shake = tremble * (float) Math.sin(clamp01(t) * 46F) * 0.05F;
        return new float[]{
                (-1.15F * lift - 0.35F * coil) * amp,
                side * (0.38F * lift - 0.62F * coil),
                side * (-0.22F * lift - 0.30F * coil + shake)
        };
    }

    /** 第三人称出击：从举刀过顶一路劈到身前下方，末段回到自然下垂。 */
    public static float[] thirdPersonStrike(float t, boolean empowered, int side) {
        t = clamp01(t);
        float wind = easeIn(clamp01(t / STRIKE_WINDUP));
        float sweep = arc((t - STRIKE_WINDUP * 0.5F) / (1F - STRIKE_WINDUP * 0.5F));
        float amp = empowered ? EMPOWERED_SCALE : 1F;
        return new float[]{
                -1.05F * wind + 3.10F * sweep * amp,
                side * (0.45F * wind - 1.05F * sweep),
                side * (-0.30F * wind + 0.70F * sweep)
        };
    }

    /** 第三人称后摇：手臂缓慢收回；挥空时垂得更低、更久。 */
    public static float[] thirdPersonRecover(float t, boolean miss, int side) {
        float decay = 1F - easeOut(t);
        float amp = miss ? 1.6F : 1F;
        return new float[]{0.85F * decay * amp, side * -0.35F * decay, side * 0.30F * decay};
    }

    /** 第三人称蓄力时的刀身朝向：刀尖随抬刀后仰，锁定满时额外翻腕。 */
    public static float[] thirdPersonChargeItem(float t, int side, boolean empowered) {
        float[] ph = chargePhases(t);
        float lift = ph[0], coil = ph[1];
        float amp = empowered ? 1.15F : 1F;
        return new float[]{
                0F, 0F, 0F,
                (34F * lift + 20F * coil) * amp,
                side * (-18F * lift - 14F * coil),
                side * (-22F * lift - 16F * coil)
        };
    }

    /** 第三人称出击时的刀身朝向：随挥砍向前下方倒，扑击幅度更大。 */
    public static float[] thirdPersonStrikeItem(float t, boolean empowered, int side) {
        t = clamp01(t);
        float sweep = arc((t - STRIKE_WINDUP * 0.5F) / (1F - STRIKE_WINDUP * 0.5F));
        float amp = empowered ? EMPOWERED_SCALE : 1F;
        return new float[]{
                0F, 0F, 0F,
                86F * sweep * amp,
                side * -40F * sweep,
                side * -26F * sweep
        };
    }

    // ───────────────────────── 擦刀（命中后的收刀） ─────────────────────────

    /** 擦刀动作时长占比：甩血（前 35%）、横抹（35–75%）、归位（75–100%）。 */
    public static final float WIPE_FLICK = 0.35F, WIPE_SWEEP = 0.75F;

    /**
     * 第一人称擦刀：命中后把刀向右下方一甩（甩掉血），再贴着袖口横抹一下，最后收正。
     *
     * <p>与出击曲线刻意区分：出击是向前劈，擦刀是「横向 + 下压」的小幅往返，幅度更小、
     * 节奏更慢（0.9 秒），让玩家一眼能看出「这一刀砍实了」。
     *
     * @param t    0~1
     * @param miss true 表示未命中，只做「收刀」不做擦血（幅度更小、无横抹）
     */
    public static float[] firstPersonWipe(float t, boolean miss, int side) {
        t = clamp01(t);
        float flick = arc(t / WIPE_FLICK);
        float sweep = arc((t - WIPE_FLICK) / (WIPE_SWEEP - WIPE_FLICK));
        // settle 是「末段余量」：只在前段之后生效并在 t=1 降到 0，
        // 这样 t=0（起手瞬间）与 t=1（收势）都严格归零。
        float settle = (1F - easeInOut(clamp01((t - WIPE_SWEEP) / (1F - WIPE_SWEEP))))
                * easeOut(clamp01(t / WIPE_FLICK));
        float damp = miss ? 0.35F : 1F;
        // 横抹只与「是否命中」有关：挥空时不该出现擦血的手势。
        float wipe = miss ? 0F : sweep;
        return new float[]{
                side * (0.10F * flick + 0.16F * wipe) * damp,
                -0.10F * flick - 0.05F * wipe - 0.06F * settle * damp,
                -0.06F * flick - 0.02F * wipe,
                18F * flick - 26F * wipe + 8F * settle * damp,
                side * (-34F * flick + 52F * wipe - 16F * settle),
                side * (-46F * flick + 64F * wipe - 12F * settle)
        };
    }

    /** 第三人称擦刀：手臂先向外甩，再横过胸前抹一下，最后垂下。 */
    public static float[] thirdPersonWipe(float t, boolean miss, int side) {
        t = clamp01(t);
        float flick = arc(t / WIPE_FLICK);
        float sweep = arc((t - WIPE_FLICK) / (WIPE_SWEEP - WIPE_FLICK));
        float settle = 1F - easeInOut(clamp01((t - WIPE_SWEEP) / (1F - WIPE_SWEEP)));
        float damp = miss ? 0.35F : 1F;
        float wipe = miss ? 0F : sweep;
        return new float[]{
                (0.55F * flick + 0.95F * wipe) * damp,
                side * (0.55F * flick - 0.85F * wipe),
                side * (-0.25F * flick + 0.55F * wipe)
        };
    }

    /** 第三人称擦刀时的刀身朝向：随甩腕外翻，横抹时压平。 */
    public static float[] thirdPersonWipeItem(float t, boolean miss, int side) {
        t = clamp01(t);
        float flick = arc(t / WIPE_FLICK);
        float sweep = arc((t - WIPE_FLICK) / (WIPE_SWEEP - WIPE_FLICK));
        float damp = miss ? 0.35F : 1F;
        float wipe = miss ? 0F : sweep;
        return new float[]{
                0F, 0F, 0F,
                (-26F * flick + 34F * wipe) * damp,
                side * (44F * flick - 58F * wipe),
                side * (38F * flick - 66F * wipe)
        };
    }
}