/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.loading;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 加载界面通用动画 / 绘制工具。
 * <p>
 * 配色对齐 docs/ui_style.md 复古车票：深棕底、米色字、金色星轨。
 * 供 {@link StarRailLoadingOverlay}、{@link TrainLoadingScreen}、
 * {@link SREReceivingLevelScreen} 与菜单 chrome 共用。
 */
@Environment(EnvType.CLIENT)
public final class LoadingFx {

    private LoadingFx() {}

    // ── 主题色（ui_style.md：BORDER / GOLD / 深棕墨）────────────────
    static final int RAIL_DIM = 0x8B6914;
    static final int RAIL_BRIGHT = 0xD4AF37;
    static final int INK = 0x18120A;

    // ── 缓动 ──────────────────────────────────────────────────

    public static float clamp01(float t) {
        return Mth.clamp(t, 0.0F, 1.0F);
    }

    /** smoothstep：首尾速度为 0，最适合淡入淡出。 */
    public static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3.0F - 2.0F * t);
    }

    public static float easeOutCubic(float t) {
        float f = 1.0F - clamp01(t);
        return 1.0F - f * f * f;
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5F
                ? 4.0F * t * t * t
                : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
    }

    /** 把 x 从 [edge0, edge1] 区间映射到 [0,1] 并做 smoothstep。 */
    static float remapSmooth(float x, float edge0, float edge1) {
        if (edge1 == edge0) {
            return x < edge0 ? 0.0F : 1.0F;
        }
        return smoothstep((x - edge0) / (edge1 - edge0));
    }

    // ── 颜色 ──────────────────────────────────────────────────

    /** 取 RGB（低 24 位）并叠加 [0,1] 透明度，返回 ARGB。 */
    public static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** 在两个完整 ARGB 颜色之间插值（含 alpha 通道）。 */
    public static int lerpArgb(float t, int a, int b) {
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
        if (x1 <= x0) {
            return;
        }
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
    public static void drawVignette(GuiGraphics g, int w, int h, float alpha) {
        int topH = (int) (h * 0.26F);
        int botH = (int) (h * 0.40F);
        g.fillGradient(0, 0, w, topH,
                withAlpha(INK, 0.55F * alpha), withAlpha(INK, 0.0F));
        g.fillGradient(0, h - botH, w, h,
                withAlpha(INK, 0.0F), withAlpha(INK, 0.82F * alpha));
    }

    /**
     * 星轨光带（确定进度）：暗金轨 + 随 headT 点亮的亮段 + 光头辉光。
     *
     * @param headT 0~1，光头沿轨道位置（通常即加载进度）
     */
    public static void drawRail(GuiGraphics g, int x0, int x1, int y, float headT, float alpha) {
        g.fill(x0, y, x1, y + 2, withAlpha(RAIL_DIM, 0.32F * alpha));
        int headX = (int) Mth.lerp(clamp01(headT), x0, x1);
        if (headX > x0) {
            hGradient(g, x0, y, headX, y + 2,
                    withAlpha(RAIL_BRIGHT, 0.28F * alpha),
                    withAlpha(RAIL_BRIGHT, 0.86F * alpha));
        }
        drawGlowHead(g, headX, y, alpha);
    }

    /**
     * 彗星指示器（不确定进度）：光头沿轨道往复扫动，带拖尾。
     *
     * @param phase 0~1 循环相位
     */
    public static void drawComet(GuiGraphics g, int x0, int x1, int y, float phase, float alpha) {
        g.fill(x0, y, x1, y + 2, withAlpha(RAIL_DIM, 0.32F * alpha));

        float tri = phase < 0.5F ? phase * 2.0F : (1.0F - phase) * 2.0F;
        float eased = easeInOutCubic(tri);
        int headX = (int) Mth.lerp(eased, x0, x1);
        boolean goingRight = phase < 0.5F;

        int tail = 64;
        if (goingRight) {
            hGradient(g, Math.max(x0, headX - tail), y, headX, y + 2,
                    withAlpha(RAIL_BRIGHT, 0.0F), withAlpha(RAIL_BRIGHT, 0.75F * alpha));
        } else {
            hGradient(g, headX, y, Math.min(x1, headX + tail), y + 2,
                    withAlpha(RAIL_BRIGHT, 0.75F * alpha), withAlpha(RAIL_BRIGHT, 0.0F));
        }
        drawGlowHead(g, headX, y, alpha);
    }

    /** 轨道上的米色光头 + 左右渐隐辉光（轻微呼吸）。 */
    private static void drawGlowHead(GuiGraphics g, int x, int y, float alpha) {
        float pulse = 0.82F + 0.18F * (float) Math.sin(Util.getMillis() / 220.0);
        float a = alpha * pulse;
        int half = 26;
        hGradient(g, x - half, y, x, y + 2,
                withAlpha(0xFFE8C0, 0.0F), withAlpha(0xFFE8C0, 0.88F * a));
        hGradient(g, x, y, x + half, y + 2,
                withAlpha(0xFFE8C0, 0.88F * a), withAlpha(0xFFE8C0, 0.0F));
        g.fill(x - 1, y - 2, x + 1, y + 3, withAlpha(0xFFF4DC, 0.95F * a));
    }

    /**
     * 居中绘制放大文字，用 pose 缩放实现更大的标题字号。
     */
    public static void drawCenteredScaled(GuiGraphics g, Font font,
                                          String text, int cx, int y, float scale, int argb) {
        drawCenteredScaled(g, font, text, cx, y, scale, argb, false);
    }

    public static void drawCenteredScaled(GuiGraphics g, Font font,
                                          String text, int cx, int y, float scale, int argb, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, -font.width(text) / 2, 0, argb, shadow);
        g.pose().popPose();
    }
}
