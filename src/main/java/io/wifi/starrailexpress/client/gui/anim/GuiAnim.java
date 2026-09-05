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

package io.wifi.starrailexpress.client.gui.anim;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;

/**
 * GUI 动画辅助：帧时间驱动的缓动与平滑值。
 * <p>
 * 所有界面动画统一使用真实帧间隔（秒）而不是固定步长，
 * 保证在不同帧率下动画速度一致、观感流畅。
 */
@Environment(EnvType.CLIENT)
public final class GuiAnim {
    private static long lastFrameNanos = 0L;
    private static float lastDelta = 0.016f;

    private GuiAnim() {
    }

    /** 每帧调用一次，返回距上一帧的时间（秒，钳制到 [0, 0.05] 防止卡顿跳变） */
    public static float frameDeltaSeconds() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            lastDelta = 0.016f;
            return lastDelta;
        }
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        lastDelta = Mth.clamp(dt, 0f, 0.05f);
        return lastDelta;
    }

    /**
     * 返回本帧最近一次 {@link #frameDeltaSeconds()} 的计算结果（不推进时钟）。
     * 供同一帧内的其它控件复用同一时间步。
     */
    public static float currentDelta() {
        return lastDelta;
    }

    /**
     * 帧率无关的指数平滑逼近。
     *
     * @param speed 收敛速度（每秒），常用 8~18
     */
    public static float approach(float current, float target, float speed, float dt) {
        float factor = 1f - (float) Math.exp(-speed * dt);
        return current + (target - current) * factor;
    }

    /** 向 1/0 收敛的开关动画值 */
    public static float toggle(float current, boolean on, float speed, float dt) {
        return approach(current, on ? 1f : 0f, speed, dt);
    }

    public static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    public static float easeInOutCubic(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    public static float easeOutBack(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    /**
     * CSS 风格三次贝塞尔缓动：从 (0,0) 到 (1,1)，控制点 ({@code x1},{@code y1})、({@code x2},{@code y2})。
     * {@code t} 为线性时间；内部用牛顿迭代把 x(u)=t 解成曲线参数再取 y。
     */
    public static float cubicBezierEase(float t, float x1, float y1, float x2, float y2) {
        t = Mth.clamp(t, 0f, 1f);
        if (t <= 0f || t >= 1f) {
            return t;
        }
        float u = t;
        for (int i = 0; i < 5; i++) {
            float x = cubicBezier1D(u, x1, x2) - t;
            float dx = cubicBezier1DDerivative(u, x1, x2);
            if (Math.abs(dx) < 1.0e-5f) {
                break;
            }
            u = Mth.clamp(u - x / dx, 0f, 1f);
        }
        return cubicBezier1D(u, y1, y2);
    }

    /** 打开界面常用的减速出场：快起、末端贴停。 */
    public static float openBezier(float t) {
        return cubicBezierEase(t, 0.22f, 1.0f, 0.36f, 1.0f);
    }

    private static float cubicBezier1D(float t, float a, float b) {
        float u = 1f - t;
        return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t;
    }

    private static float cubicBezier1DDerivative(float t, float a, float b) {
        float u = 1f - t;
        return 3f * u * u * a + 6f * u * t * (b - a) + 3f * t * t * (1f - b);
    }

    /** 将进度（0~1）映射为透明度（0~255） */
    public static int alphaOf(float progress) {
        return Mth.clamp((int) (Mth.clamp(progress, 0f, 1f) * 255f), 0, 255);
    }

    /** 用给定透明度覆盖颜色自身的 alpha */
    public static int withAlpha(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** 线性混色（保留两端 alpha 的插值） */
    public static int blend(int colorA, int colorB, float ratio) {
        ratio = Mth.clamp(ratio, 0f, 1f);
        int a1 = (colorA >>> 24), r1 = (colorA >> 16) & 0xFF, g1 = (colorA >> 8) & 0xFF, b1 = colorA & 0xFF;
        int a2 = (colorB >>> 24), r2 = (colorB >> 16) & 0xFF, g2 = (colorB >> 8) & 0xFF, b2 = colorB & 0xFF;
        return ((int) (a1 + (a2 - a1) * ratio) << 24)
                | ((int) (r1 + (r2 - r1) * ratio) << 16)
                | ((int) (g1 + (g2 - g1) * ratio) << 8)
                | (int) (b1 + (b2 - b1) * ratio);
    }
}
