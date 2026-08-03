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
