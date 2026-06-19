package net.exmo.sre.loading;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 加载界面通用动画 / 绘制工具。
 * <p>
 * 把缓动函数、ARGB 颜色插值、横向渐变、暗角与“星轨光带”等
 * 视觉元素集中到一处，保证 {@link StarRailLoadingOverlay}（资源加载）
 * 与 {@link SREReceivingLevelScreen}（进入世界）拥有一致、衔接的观感。
 */
@Environment(EnvType.CLIENT)
final class LoadingFx {

    private LoadingFx() {}

    // ── 主题色 ────────────────────────────────────────────────
    static final int RAIL_DIM = 0x3A6FB5;   // 轨道暗蓝
    static final int RAIL_BRIGHT = 0x66CCFF; // 轨道亮蓝
    static final int INK = 0x05070E;         // 暗角底色（近黑）

    // ── 缓动 ──────────────────────────────────────────────────

    static float clamp01(float t) {
        return Mth.clamp(t, 0.0F, 1.0F);
    }

    /** smoothstep：首尾速度为 0，最适合淡入淡出。 */
    static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3.0F - 2.0F * t);
    }

    static float easeOutCubic(float t) {
        float f = 1.0F - clamp01(t);
        return 1.0F - f * f * f;
    }

    static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5F
                ? 4.0F * t * t * t
                : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
    }

    /** 把 x 从 [edge0, edge1] 区间映射到 [0,1] 并做 smoothstep。 */
    static float remapSmooth(float x, float edge0, float edge1) {
        if (edge1 == edge0) return x < edge0 ? 0.0F : 1.0F;
        return smoothstep((x - edge0) / (edge1 - edge0));
    }

    // ── 颜色 ──────────────────────────────────────────────────

    /** 取 RGB（低 24 位）并叠加 [0,1] 透明度，返回 ARGB。 */
    static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** 在两个完整 ARGB 颜色之间插值（含 alpha 通道）。 */
    static int lerpArgb(float t, int a, int b) {
        t = clamp01(t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) Mth.lerp(t, aa, ba) << 24)
                | ((int) Mth.lerp(t, ar, br) << 16)
                | ((int) Mth.lerp(t, ag, bg) << 8)
                | (int) Mth.lerp(t, ab, bb);
    }

    // ── 绘制 ──────────────────────────────────────────────────

    /** 横向渐变填充（原版 fillGradient 只能纵向）。区间较小时开销可忽略。 */
    static void hGradient(GuiGraphics g, int x0, int y0, int x1, int y1, int left, int right) {
        if (x1 <= x0) return;
        int n = x1 - x0;
        for (int i = 0; i < n; i++) {
            float t = (i + 0.5F) / n;
            int c = lerpArgb(t, left, right);
            g.fill(x0 + i, y0, x0 + i + 1, y1, c);
        }
    }

    /**
     * 顶部 + 底部暗角渐变。压暗上下边缘以托住文字，
     * 但中央留空，列车视频背景依旧清晰可见。
     */
    static void drawVignette(GuiGraphics g, int w, int h, float alpha) {
        int topH = (int) (h * 0.26F);
        int botH = (int) (h * 0.40F);
        g.fillGradient(0, 0, w, topH,
                withAlpha(INK, 0.50F * alpha), withAlpha(INK, 0.0F));
        g.fillGradient(0, h - botH, w, h,
                withAlpha(INK, 0.0F), withAlpha(INK, 0.80F * alpha));
    }

    /**
     * 星轨光带（确定进度）：暗轨 + 随 headT 点亮的亮段 + 光头辉光。
     *
     * @param headT 0~1，光头沿轨道位置（通常即加载进度）
     */
    static void drawRail(GuiGraphics g, int x0, int x1, int y, float headT, float alpha) {
        g.fill(x0, y, x1, y + 1, withAlpha(RAIL_DIM, 0.22F * alpha));
        int headX = (int) Mth.lerp(clamp01(headT), x0, x1);
        if (headX > x0) {
            hGradient(g, x0, y, headX, y + 1,
                    withAlpha(RAIL_BRIGHT, 0.30F * alpha),
                    withAlpha(RAIL_BRIGHT, 0.75F * alpha));
        }
        drawGlowHead(g, headX, y, alpha);
    }

    /**
     * 彗星指示器（不确定进度）：光头沿轨道往复扫动，带拖尾。
     *
     * @param phase 0~1 循环相位
     */
    static void drawComet(GuiGraphics g, int x0, int x1, int y, float phase, float alpha) {
        g.fill(x0, y, x1, y + 1, withAlpha(RAIL_DIM, 0.22F * alpha));

        // 三角波 + 缓动，让光头在两端平滑折返
        float tri = phase < 0.5F ? phase * 2.0F : (1.0F - phase) * 2.0F;
        float eased = easeInOutCubic(tri);
        int headX = (int) Mth.lerp(eased, x0, x1);
        boolean goingRight = phase < 0.5F;

        int tail = 64;
        if (goingRight) {
            hGradient(g, Math.max(x0, headX - tail), y, headX, y + 1,
                    withAlpha(RAIL_BRIGHT, 0.0F), withAlpha(RAIL_BRIGHT, 0.70F * alpha));
        } else {
            hGradient(g, headX, y, Math.min(x1, headX + tail), y + 1,
                    withAlpha(RAIL_BRIGHT, 0.70F * alpha), withAlpha(RAIL_BRIGHT, 0.0F));
        }
        drawGlowHead(g, headX, y, alpha);
    }

    /** 轨道上的白色光头 + 左右渐隐辉光。 */
    private static void drawGlowHead(GuiGraphics g, int x, int y, float alpha) {
        int half = 26;
        hGradient(g, x - half, y, x, y + 1,
                withAlpha(0xFFFFFF, 0.0F), withAlpha(0xFFFFFF, 0.85F * alpha));
        hGradient(g, x, y, x + half, y + 1,
                withAlpha(0xFFFFFF, 0.85F * alpha), withAlpha(0xFFFFFF, 0.0F));
        // 中心亮点（略高于轨道，凸显光头）
        g.fill(x - 1, y - 2, x + 1, y + 3, withAlpha(0xFFFFFF, 0.95F * alpha));
    }

    /**
     * 居中绘制放大文字（带阴影），用 pose 缩放实现更大的标题字号。
     */
    static void drawCenteredScaled(GuiGraphics g, net.minecraft.client.gui.Font font,
                                   String text, int cx, int y, float scale, int argb) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, -font.width(text) / 2, 0, argb, true);
        g.pose().popPose();
    }
}
