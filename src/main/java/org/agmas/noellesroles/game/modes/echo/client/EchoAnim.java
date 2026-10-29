package org.agmas.noellesroles.game.modes.echo.client;

/** 界面与 HUD 共用的轻量缓动/淡入/脉冲；只用系统毫秒，不参与任何权威判定。 */
public final class EchoAnim {
    private EchoAnim() { }

    public static float clamp01(float value) { return Math.max(0F, Math.min(1F, value)); }

    /** 0→1 的 cubic-out，用于面板入场。 */
    public static float easeOut(float t) {
        t = clamp01(t);
        float inv = 1F - t;
        return 1F - inv * inv * inv;
    }

    /** 打开后经过 durationMs 返回 0→1 的入场进度。 */
    public static float openProgress(long openedAt, int durationMs) {
        if (durationMs <= 0) return 1F;
        return easeOut((System.currentTimeMillis() - openedAt) / (float) durationMs);
    }

    /** 0.5..1 的呼吸脉冲，周期 periodMs。 */
    public static float breathe(long periodMs) {
        double phase = (System.currentTimeMillis() % Math.max(1L, periodMs)) / (double) Math.max(1L, periodMs);
        return 0.5F + 0.5F * (float) Math.sin(phase * Math.PI * 2);
    }

    /** 最后几秒的急促闪烁，返回 0..1。 */
    public static float urgency(long remainingMs, long warnMs) {
        if (remainingMs <= 0 || remainingMs > warnMs) return 0F;
        double phase = (System.currentTimeMillis() % 500L) / 500D;
        return 0.5F + 0.5F * (float) Math.sin(phase * Math.PI * 2);
    }

    /** 按 alpha 缩放 ARGB 的透明度。 */
    public static int fade(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * clamp01(alpha));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** 两个 ARGB 线性混合。 */
    public static int blend(int from, int to, float t) {
        t = clamp01(t);
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) { return Math.round(from + (to - from) * t); }

    /** 数值变化时的短脉冲：从 1 快速衰减到 0。 */
    public static float bump(long changedAt, int durationMs) {
        if (changedAt <= 0) return 0F;
        long elapsed = System.currentTimeMillis() - changedAt;
        if (elapsed < 0 || elapsed >= durationMs) return 0F;
        return 1F - elapsed / (float) durationMs;
    }
}
