package io.wifi.starrailexpress.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class StatusBarHUD {
    private static final StatusBarHUD INSTANCE = new StatusBarHUD();
    private final Map<String, StatusBar> statusBars = new ConcurrentHashMap<>();
    private final Map<String, Long> removalTimers = new ConcurrentHashMap<>();

    // 现代化配置参数
    private static final float BAR_HEIGHT = 12.0f;
    private static final float BAR_WIDTH = 220.0f;
    private static final float BAR_SPACING = 12.0f;
    private static final long FADE_DURATION = 500L;
    private static final long DEFAULT_DURATION = 500000L;

    // 现代化颜色方案 (深色主题)
    private static final int BACKGROUND_COLOR = 0x1A1A2E;
    private static final int BACKGROUND_BORDER = 0xFF3A3F5E;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFB0B0B0;
    private static final int ACCENT_COLOR = 0xFF00D4FF;

    // 现代化进度条颜色梯度 (绿色到红色)
    private static final int[] PROGRESS_COLORS = {
            0xFF00FF00, // 亮绿 (100%)
            0xFF7FFF00, // 黄绿
            0xFFFFFF00, // 黄色
            0xFFFF7F00, // 橙色
            0xFFFF3F00, // 橙红
            0xFFFF0000  // 红色 (0%)
    };

    private StatusBarHUD() {
    }

    public static StatusBarHUD getInstance() {
        return INSTANCE;
    }

    /**
     * 添加或更新状态条（使用动态进度供应商）
     * 
     * @param id               唯一标识符
     * @param name             显示名称
     * @param progressSupplier 动态获取进度的供应商 (返回值范围: 0.0 - 1.0)
     */
    public void addStatusBar(String id, String name, Supplier<Float> progressSupplier) {
        addStatusBar(id, name, progressSupplier, DEFAULT_DURATION);
    }

    public void addStatusBar(StatusInit.StatusBar statusbar) {
        addStatusBar(statusbar.id(), statusbar.name(), statusbar.progressSupplier(), DEFAULT_DURATION);
    }

    /**
     * 添加或更新状态条（使用动态进度供应商和自定义持续时间）
     * 
     * @param id               唯一标识符
     * @param name             显示名称
     * @param progressSupplier 动态获取进度的供应商 (返回值范围: 0.0 - 1.0)
     * @param durationMs       显示持续时间（毫秒）
     */
    public void addStatusBar(String id, String name, Supplier<Float> progressSupplier, long durationMs) {
        StatusBar bar = statusBars.computeIfAbsent(id, k -> new StatusBar());
        bar.name = name;
        bar.progressSupplier = progressSupplier;
        bar.lastUpdateTime = System.currentTimeMillis();
        bar.duration = durationMs;
        bar.creationTime = System.currentTimeMillis();

        // 重置移除计时器
        removalTimers.put(id, System.currentTimeMillis() + durationMs);
    }

    /**
     * 添加或更新状态条（快速API，用于非动态进度）
     * 
     * @param id              唯一标识符
     * @param name            显示名称
     * @param currentProgress 当前进度 (0.0 - 1.0)
     */
    public void updateStatusBar(String id, String name, float currentProgress) {
        StatusBar bar = statusBars.get(id);
        if (bar != null) {
            bar.name = name;
            bar.progressSupplier = () -> Mth.clamp(currentProgress, 0, 1);
            bar.lastUpdateTime = System.currentTimeMillis();
        }
    }

    /**
     * 移除状态条
     * 
     * @param id 状态条ID
     */
    public void removeStatusBar(String id) {
        statusBars.remove(id);
        removalTimers.remove(id);
    }

    /**
     * 清除所有状态条
     */
    public void clearAllStatusBars() {
        statusBars.clear();
        removalTimers.clear();
    }

    /**
     * 渲染所有状态条
     */
    public void render(GuiGraphics guiGraphics, float partialTicks) {
        if (statusBars.isEmpty())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null)
            return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 计算渲染位置（屏幕顶部中央）
        int startY = 20;
        int currentY = startY;

        // 清理过期的状态条
        cleanupExpiredBars();

        // 获取排序后的状态条列表（按添加时间排序）
        List<Map.Entry<String, StatusBar>> sortedBars = new ArrayList<>(statusBars.entrySet());
        sortedBars.sort(Comparator.comparingLong(entry -> entry.getValue().creationTime));

        // 渲染每个状态条
        for (Map.Entry<String, StatusBar> entry : sortedBars) {
            StatusBar bar = entry.getValue();
            float progressRatio = bar.getProgress();

            // 计算淡出效果
            long elapsedTime = System.currentTimeMillis() - bar.creationTime;
            long remainingTime = bar.duration - elapsedTime;
            float alpha = 1.0f;

            if (remainingTime < FADE_DURATION) {
                alpha = Math.max(0, remainingTime / (float) FADE_DURATION);
            }

            // 跳过完全透明的条
            if (alpha <= 0.01f)
                continue;

            renderStatusBar(guiGraphics, screenWidth, currentY, bar, progressRatio, alpha);
            currentY += BAR_HEIGHT + BAR_SPACING;
        }
    }

    /**
     * 渲染单个状态条（现代化设计）
     */
    private void renderStatusBar(GuiGraphics guiGraphics, int screenWidth, int y, StatusBar bar, float progressRatio,
            float alpha) {
        int barWidth = (int) BAR_WIDTH;
        int barHeight = (int) BAR_HEIGHT;
        int barX = (screenWidth - barWidth) / 2;
        int barY = y;

        progressRatio = Mth.clamp(progressRatio, 0.0f, 1.0f);

        // 计算颜色（绿色到红色的渐变）
        int colorIndex = (int) (progressRatio * (PROGRESS_COLORS.length - 1));
        colorIndex = Mth.clamp(colorIndex, 0, PROGRESS_COLORS.length - 1);
        int progressColor = PROGRESS_COLORS[colorIndex];

        // 应用透明度
        int bgColor = applyAlpha(BACKGROUND_COLOR, alpha);
        int borderColor = applyAlpha(BACKGROUND_BORDER, alpha);
        int fillColor = applyAlpha(progressColor, alpha);
        int textColor = applyAlpha(TEXT_COLOR, alpha);
        int secondaryTextColor = applyAlpha(TEXT_SECONDARY, alpha);

        // 绘制背景框（带边框）
        drawFilledRect(guiGraphics, barX, barY, barX + barWidth, barY + barHeight, bgColor);
        drawBorder(guiGraphics, barX, barY, barWidth, barHeight, borderColor);

        // 绘制进度条填充（带平滑过渡）
        if (progressRatio > 0) {
            int filledWidth = (int) ((barWidth - 4) * progressRatio);
            drawFilledRect(guiGraphics, barX + 2, barY + 2, barX + 2 + filledWidth, barY + barHeight - 2, fillColor);
        }

        // 绘制标题和百分比
        Minecraft mc = Minecraft.getInstance();
        String titleText = bar.name;
        String percentText = String.format("%.0f%%", progressRatio * 100);

        int titleWidth = mc.font.width(titleText);
        int percentWidth = mc.font.width(percentText);

        int titleX = barX + 6;
        int titleY = barY + 2;
        int percentX = barX + barWidth - percentWidth - 6;

        // 绘制文字
        guiGraphics.drawString(mc.font, titleText, titleX, titleY, textColor, false);
        guiGraphics.drawString(mc.font, percentText, percentX, titleY, secondaryTextColor, false);
    }

    /**
     * 绘制填充矩形
     */
    private void drawFilledRect(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        guiGraphics.fill(x1, y1, x2, y2, color);
    }

    /**
     * 绘制边框
     */
    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        int borderThickness = 1;

        // 上边框
        guiGraphics.fill(x, y, x + width, y + borderThickness, color);
        // 下边框
        guiGraphics.fill(x, y + height - borderThickness, x + width, y + height, color);
        // 左边框
        guiGraphics.fill(x, y, x + borderThickness, y + height, color);
        // 右边框
        guiGraphics.fill(x + width - borderThickness, y, x + width, y + height, color);
    }

    /**
     * 应用透明度到颜色
     */
    private int applyAlpha(int color, float alpha) {
        int a = (int) ((color >> 24 & 255) * alpha);
        int r = (color >> 16 & 255);
        int g = (color >> 8 & 255);
        int b = (color & 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 清理过期的状态条
     */
    private void cleanupExpiredBars() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = removalTimers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (currentTime > entry.getValue()) {
                statusBars.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    /**
     * 状态条数据类
     */
    private static class StatusBar {
        public String name = "";
        public Supplier<Float> progressSupplier = () -> 0.0f;
        public long lastUpdateTime = 0L;
        public long creationTime = 0L;
        public long duration = DEFAULT_DURATION;
        private float smoothProgress = 0.0f;

        /**
         * 获取当前进度（带平滑过渡）
         */
        public float getProgress() {
            float targetProgress = Mth.clamp(progressSupplier.get(), 0.0f, 1.0f);
            // 平滑插值 (0.1 = 较平滑, 0.2 = 较快)
            smoothProgress = Mth.lerp(0.15f, smoothProgress, targetProgress);
            return smoothProgress;
        }
    }
}