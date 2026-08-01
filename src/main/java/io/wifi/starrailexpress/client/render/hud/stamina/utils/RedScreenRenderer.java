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

package io.wifi.starrailexpress.client.render.hud.stamina.utils;

import org.jetbrains.annotations.NotNull;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;

public class RedScreenRenderer {
    
    // 添加屏幕边缘红色效果相关变量
    public static long screenRedEffectStartTime = 0L; // 屏幕红色效果开始时间（毫秒）
    private static final long SCREEN_RED_EFFECT_DURATION_MS = 300L; // 屏幕红色效果持续时间（毫秒）
    private static final float MAX_RED_INTENSITY = 0.5f; // 最大红色强度（0-1）

    // 新增：通用屏幕边缘效果相关变量
    private static long generalScreenEffectStartTime = 0L; // 通用屏幕效果开始时间（毫秒）
    private static long GENERAL_SCREEN_EFFECT_DURATION_MS = 300L; // 通用屏幕效果持续时间（毫秒）
    private static int generalScreenEffectColor = 0xFF0000; // 通用屏幕效果颜色，默认为红色
    private static float generalScreenEffectIntensity = 0.5f; // 通用屏幕效果强度
     /**
     * 渲染屏幕边缘红色效果（刀蓄力完毕时）
     */
    public static void renderScreenRedEffect(@NotNull GuiGraphics context, float delta) {
        if (isScreenRedEffectActive()) {
            renderScreenEdgeEffect(context, screenRedEffectStartTime, SCREEN_RED_EFFECT_DURATION_MS, 0xFF0000,
                    MAX_RED_INTENSITY);
        }

        // 同时渲染通用屏幕边缘效果
        if (isGeneralScreenEffectActive()) {
            renderScreenEdgeEffect(context, generalScreenEffectStartTime, GENERAL_SCREEN_EFFECT_DURATION_MS,
                    generalScreenEffectColor, generalScreenEffectIntensity);
        }
    }

    /**
     * 通用的屏幕边缘效果渲染方法
     */
    private static void renderScreenEdgeEffect(@NotNull GuiGraphics context, long effectStartTime,
            long effectDurationMs, int color, float maxIntensity) {
        long elapsed = System.currentTimeMillis() - effectStartTime;
        float progress = Math.max(0.0f, 1.0f - (float) elapsed / effectDurationMs);
        float intensity = maxIntensity * progress;
        if (intensity <= 0f)
            return;

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int edgeAlpha = (int) (intensity * 255);
        int opaqueColor = (edgeAlpha << 24) | (r << 16) | (g << 8) | b;
        int transparent = 0x00000000;

        int edgeW = Math.max(1, (int) (screenWidth * 0.12f));
        int edgeH = Math.max(1, (int) (screenHeight * 0.15f));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 顶部（fillGradient 竖向渐变，1次 draw call）
        context.fillGradient(0, 0, screenWidth, edgeH, opaqueColor, transparent);
        // 底部（fillGradient 竖向渐变，1次 draw call）
        context.fillGradient(0, screenHeight - edgeH, screenWidth, screenHeight, transparent, opaqueColor);
        // 左侧（逐列，延伸全高，角落与上下边缘自然叠加不产生接缝）
        for (int i = 0; i < edgeW; i++) {
            float alpha = (1f - (float) i / edgeW) * intensity;
            int col = (int) (alpha * 255) << 24 | (r << 16) | (g << 8) | b;
            context.fill(i, 0, i + 1, screenHeight, col);
        }
        // 右侧（逐列，延伸全高）
        for (int i = 0; i < edgeW; i++) {
            float alpha = (1f - (float) i / edgeW) * intensity;
            int col = (int) (alpha * 255) << 24 | (r << 16) | (g << 8) | b;
            context.fill(screenWidth - i - 1, 0, screenWidth - i, screenHeight, col);
        }

        RenderSystem.disableBlend();
    }

    /**
     * 检查屏幕红色效果是否仍然活跃
     */
    private static boolean isScreenRedEffectActive() {
        if (screenRedEffectStartTime == 0L) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        return (currentTime - screenRedEffectStartTime) < SCREEN_RED_EFFECT_DURATION_MS;
    }

    /**
     * 检查通用屏幕效果是否仍然活跃
     */
    private static boolean isGeneralScreenEffectActive() {
        if (generalScreenEffectStartTime == 0L) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        return (currentTime - generalScreenEffectStartTime) < GENERAL_SCREEN_EFFECT_DURATION_MS;
    }

    /**
     * 启动通用屏幕边缘效果
     * 
     * @param color      颜色值，例如 0xFF0000 为红色
     * @param durationMs 效果持续时间（毫秒）
     * @param intensity  最大强度（0.0-1.0）
     */
    public static void triggerScreenEdgeEffect(int color, long durationMs, float intensity) {
        generalScreenEffectStartTime = System.currentTimeMillis();
        generalScreenEffectColor = color;
        generalScreenEffectIntensity = intensity;
        GENERAL_SCREEN_EFFECT_DURATION_MS = (int) durationMs; // 注意：这里会修改常量，需要重构
    }

    /**
     * 启动通用屏幕边缘效果（使用默认参数）
     * 
     * @param color 颜色值，例如 0xFF0000 为红色
     */
    public static void triggerScreenEdgeEffect(int color) {
        triggerScreenEdgeEffect(color, 300L, 0.5f);
    }

    /**
     * 启动通用屏幕边缘效果（使用默认红色和持续时间）
     * 
     * @param intensity 最大强度（0.0-1.0）
     */
    public static void triggerScreenEdgeEffect(float intensity) {
        triggerScreenEdgeEffect(0xFF0000, 300L, intensity);
    }

    /**
     * 启动通用屏幕边缘效果（使用指定颜色和持续时间）
     * 
     * @param color      颜色值，例如 0xFF0000 为红色
     * @param durationMs 效果持续时间（毫秒）
     */
    public static void triggerScreenEdgeEffect(int color, long durationMs) {
        triggerScreenEdgeEffect(color, durationMs, 0.5f);
    }

}
