package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.data.MapConfig;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MapDetailsRenderer {
    public static String mapId = "";
    public static String mapDescription = "";
    public static String mapAuthor = "";
    private static long displayStartTime = 0L;
    private static final long DISPLAY_DURATION = 10000L; // 12秒显示时间
    private static final long FADE_DURATION = 1000L; // 1秒淡入淡出时间
    private static final long FINAL_ANIMATION_START = 10000L; // 最后4秒开始合拢再张开动画

    // 文字颜色 - 电影风格黑白
    private static final int TITLE_COLOR = 0xFFFFFFFF; // 纯白标题
    private static final int AUTHOR_COLOR = 0xFFCCCCCC; // 浅灰作者
    private static final int DESC_COLOR = 0xFFAAAAAA; // 中灰描述

    // 字体大小
    private static final float TITLE_SCALE = 2.0f; // 标题放大100%
    private static final float AUTHOR_SCALE = 1.2f; // 作者放大20%
    private static final float DESC_SCALE = 0.9f; // 描述缩小10%

    // 布局位置 - 左上角
    private static final int LEFT_MARGIN = 30;
    private static final int TOP_MARGIN = 30;
    private static final int LINE_SPACING = 5;

    // 全屏效果参数
    private static final float VIGNETTE_INTENSITY = 0.3f; // 暗角强度
    private static final int VIGNETTE_COLOR = 0x80000000; // 黑色暗角

    // 上下黑框参数
    private static final int BLACK_BAR_COLOR = 0xFF000000; // 纯黑色
    private static final float MAX_BLACK_BAR_HEIGHT = 0.25f; // 最大覆盖1/4屏幕
    private static float currentBarHeightRatio = 0f; // 当前黑框高度比例
    private static boolean barsAnimatingOut = false; // 标记黑边是否在合拢阶段
    private static boolean finalAnimationPlayed = false; // 标记最终动画是否已播放

    // 动画效果
    private static float titleOffsetX = 0f; // 标题偏移动画
    private static float titlePulse = 0f; // 标题脉动动画

    public static void renderHud(Font font, @NotNull LocalPlayer player, GuiGraphics context, float delta) {
        if (mapId.isEmpty() || System.currentTimeMillis() - displayStartTime > DISPLAY_DURATION) {
            // 重置动画状态
            if (!mapId.isEmpty()) {
                clearMapDetails();
            }
            return;
        }

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        // 计算透明度和动画进度
        long elapsed = System.currentTimeMillis() - displayStartTime;
        float alpha = 1.0f;

        // 计算淡入淡出透明度
        if (elapsed < FADE_DURATION) {
            alpha = (float) elapsed / FADE_DURATION; // 淡入
        } else if (elapsed > DISPLAY_DURATION - FADE_DURATION) {
            alpha = (float) (DISPLAY_DURATION - elapsed) / FADE_DURATION; // 淡出
        }

        int alphaInt = (int) (alpha * 255);
        if (alphaInt <= 0) return;

        // 更新动画
        updateAnimations(delta, elapsed);

        // 更新黑框动画
        updateBlackBarAnimation(elapsed);

        // 保存当前矩阵状态
        context.pose().pushPose();

        // 渲染上下黑框
        renderBlackBars(context, screenWidth, screenHeight);

        // 渲染全屏电影效果
        renderFullscreenEffects(context, screenWidth, screenHeight, alphaInt);

        // 获取地图信息
        AtomicReference<String> mapNameKey = new AtomicReference<>("map." + mapId + ".name");
        AtomicReference<String> mapDescKey = new AtomicReference<>("map." + mapId + ".desc");

        MapConfig.getInstance().getMaps().stream()
                .filter(map -> map.id.equals(mapId))
                .findFirst()
                .ifPresent(map -> {
                    mapNameKey.set(map.displayName);
                    mapDescKey.set(map.description);
                });

        String mapName = Language.getInstance().getOrDefault(mapNameKey.get());
        if (mapName.equals(mapNameKey.get())) {
            mapName = mapId;
        }

        String mapDesc = Language.getInstance().getOrDefault(mapDescKey.get());

        // 渲染左上角地图信息
        renderTopLeftContent(context, font, mapName, mapDesc, alphaInt);

        // 恢复矩阵状态
        context.pose().popPose();
    }

    /**
     * 更新动画效果
     */
    private static void updateAnimations(float delta, long elapsed) {
        // 标题轻微偏移动画（模拟胶片抖动）
        titleOffsetX = (float) Math.sin(elapsed * 0.001f) * 0.5f;

        // 标题脉动动画
        titlePulse = (float) Math.sin(elapsed * 0.002f) * 0.05f + 1.0f;
    }

    /**
     * 更新黑框动画
     */
    private static void updateBlackBarAnimation(long elapsed) {
        // 确保时间在有效范围内
        if (elapsed > DISPLAY_DURATION) {
            currentBarHeightRatio = 0f;
            return;
        }

        // 第一阶段：淡入时黑边展开到最大（0-1秒）
        if (elapsed < FADE_DURATION) {
            float progress = (float) elapsed / FADE_DURATION;
            currentBarHeightRatio = MAX_BLACK_BAR_HEIGHT * progress;
            barsAnimatingOut = false;
            finalAnimationPlayed = false;
            return;
        }

        // 第二阶段：保持黑边在最大位置（1-8秒）
        if (elapsed < FINAL_ANIMATION_START) {
            currentBarHeightRatio = MAX_BLACK_BAR_HEIGHT;
            barsAnimatingOut = false;
            return;
        }

        // 第三阶段：最后4秒，执行一次合拢再张开的动画（8-12秒）
        if (!finalAnimationPlayed) {
            float progress = (float) (elapsed - FINAL_ANIMATION_START) / (DISPLAY_DURATION - FINAL_ANIMATION_START);

            // 前半部分：合拢（8-10秒）
            if (progress < 0.5f) {
                float closeProgress = progress * 2f; // 0到1
                currentBarHeightRatio = MAX_BLACK_BAR_HEIGHT * (1f - closeProgress);
                barsAnimatingOut = true;
            }
            // 后半部分：张开（10-12秒）
            else {
                float openProgress = (progress - 0.5f) * 2f; // 0到1
                currentBarHeightRatio = MAX_BLACK_BAR_HEIGHT * openProgress;
                barsAnimatingOut = false;

                // 标记动画已播放完成
                if (progress >= 0.99f) {
                    finalAnimationPlayed = true;
                }
            }
        }
    }

    /**
     * 渲染上下黑框
     */
    private static void renderBlackBars(GuiGraphics context, int screenWidth, int screenHeight) {
        if (currentBarHeightRatio <= 0f) return;

        int barHeight = (int) (screenHeight * currentBarHeightRatio);

        // 上黑框 - 纯黑色，不使用透明度
        context.fill(0, 0, screenWidth, barHeight, BLACK_BAR_COLOR);

        // 下黑框 - 纯黑色，不使用透明度
        int bottomBarY = screenHeight - barHeight;
        context.fill(0, bottomBarY, screenWidth, screenHeight, BLACK_BAR_COLOR);
    }

    /**
     * 渲染全屏电影效果
     */
    private static void renderFullscreenEffects(GuiGraphics context, int screenWidth, int screenHeight, int alpha) {
        // 渲染暗角效果（模拟电影镜头）
        renderVignette(context, screenWidth, screenHeight, alpha);

        // 渲染胶片颗粒效果（可选，增强电影感）
        renderFilmGrain(context, screenWidth, screenHeight, alpha);

        // 渲染扫描线效果（模拟CRT显示器）
        renderScanlines(context, screenWidth, screenHeight, alpha);

        // 添加老电影噪点效果
        renderVintageNoise(context, screenWidth, screenHeight, alpha);
    }

    /**
     * 渲染暗角效果（优化版本）
     */
    private static void renderVignette(GuiGraphics context, int screenWidth, int screenHeight, int alpha) {
        // 使用径向渐变实现更自然的暗角效果
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int maxDistance = (int) Math.sqrt(centerX * centerX + centerY * centerY);

        int vignetteAlpha = (int)(VIGNETTE_INTENSITY * alpha * 0.7f); // 降低整体强度

        // 在屏幕边缘绘制多个同心圆实现渐变
        for (int radius = maxDistance; radius > maxDistance * 0.7; radius--) {
            float distanceRatio = (float)(maxDistance - radius) / (maxDistance * 0.3f);
            int circleAlpha = (int)(vignetteAlpha * distanceRatio * 0.3f);
            int circleColor = (circleAlpha << 24) | 0x000000;

            // 绘制圆形边缘（简化为矩形近似）
            int left = centerX - radius;
            int top = centerY - radius;
            int right = centerX + radius;
            int bottom = centerY + radius;

            if (left < 0) left = 0;
            if (top < 0) top = 0;
            if (right > screenWidth) right = screenWidth;
            if (bottom > screenHeight) bottom = screenHeight;

            // 绘制四条边
            context.fill(left, top, right, top + 1, circleColor); // 上边
            context.fill(left, bottom - 1, right, bottom, circleColor); // 下边
            context.fill(left, top, left + 1, bottom, circleColor); // 左边
            context.fill(right - 1, top, right, bottom, circleColor); // 右边
        }
    }

    /**
     * 渲染胶片颗粒效果
     */
    private static void renderFilmGrain(GuiGraphics context, int screenWidth, int screenHeight, int alpha) {
        // 随机颗粒效果（简化实现）
        long time = System.currentTimeMillis();
        int grainAlpha = (int)(alpha * 0.1f); // 非常淡的颗粒

        for (int i = 0; i < 50; i++) {
            float x = (float) ((time * 0.5 + i * 100) % screenWidth);
            float y = (float) ((Math.sin(time * 0.001 + i) * 100 + i * 20) % screenHeight);
            int grainColor = (grainAlpha << 24) | 0xFFFFFF;
            context.fill((int)x, (int)y, (int)x + 1, (int)y + 1, grainColor);
        }
    }

    /**
     * 渲染扫描线效果
     */
    private static void renderScanlines(GuiGraphics context, int screenWidth, int screenHeight, int alpha) {
        // 每隔2像素绘制一条细线
        int lineAlpha = (int)(alpha * 0.05f); // 非常淡的扫描线

        for (int y = 0; y < screenHeight; y += 2) {
            context.fill(0, y, screenWidth, y + 1, (lineAlpha << 24) | 0x000000);
        }
    }

    /**
     * 渲染老电影噪点效果
     */
    private static void renderVintageNoise(GuiGraphics context, int screenWidth, int screenHeight, int alpha) {
        // 模拟老电影的随机噪点
        long time = System.currentTimeMillis();
        java.util.Random random = new java.util.Random(time / 100);

        int noiseCount = 30; // 噪点数量
        int noiseAlpha = (int)(alpha * 0.04f); // 很淡的噪点

        for (int i = 0; i < noiseCount; i++) {
            int x = random.nextInt(screenWidth);
            int y = random.nextInt(screenHeight);

            // 噪点大小随机
            int size = random.nextInt(2) + 1;
            int noiseColor = (noiseAlpha << 24) | (random.nextBoolean() ? 0xFFFFFF : 0x000000);

            context.fill(x, y, x + size, y + size, noiseColor);
        }
    }

    /**
     * 渲染左上角内容
     */
    private static void renderTopLeftContent(GuiGraphics context, Font font,
                                             String mapName, String mapDesc, int alpha) {
        int currentY = TOP_MARGIN;

        // 1. 渲染地图标题（大字体，带动画效果）
        renderTitle(context, font, mapName, currentY, alpha);
        currentY += (int)(font.lineHeight * TITLE_SCALE) + LINE_SPACING;

        // 2. 渲染作者信息
        renderAuthor(context, font, currentY, alpha);
        currentY += (int)(font.lineHeight * AUTHOR_SCALE) + LINE_SPACING;

        // 3. 渲染地图描述
        if (!mapDesc.isEmpty() && !mapDesc.equals("map." + mapId + ".desc")) {
            renderDescription(context, font, mapDesc, currentY, alpha);
        }

        // 4. 渲染地图ID（最小号）
        renderMapId(context, font, alpha);
    }

    /**
     * 渲染地图标题
     */
    private static void renderTitle(GuiGraphics context, Font font, String title, int y, int alpha) {
        context.pose().pushPose();

        // 应用位置和动画效果
        int titleX = LEFT_MARGIN + (int)titleOffsetX;
        context.pose().translate(titleX, y, 0);

        // 应用脉动缩放
        float finalScale = TITLE_SCALE * titlePulse;
        context.pose().scale(finalScale, finalScale, 1.0f);

        // 标题阴影（轻微偏移）
        int shadowColor = (alpha << 24) | 0x000000;
        context.drawString(font, title, 1, 1, shadowColor, false);

        // 标题主体（纯白）
        int titleColor = (alpha << 24) | TITLE_COLOR;
        context.drawString(font, title, 0, 0, titleColor, false);

        context.pose().popPose();
    }

    /**
     * 渲染作者信息
     */
    private static void renderAuthor(GuiGraphics context, Font font, int y, int alpha) {
        context.pose().pushPose();

        int authorX = LEFT_MARGIN;
        context.pose().translate(authorX, y, 0);
        context.pose().scale(AUTHOR_SCALE, AUTHOR_SCALE, 1.0f);

        String author = "allinTOKYO canyuesama haiman wifi_left guanzheqwq biantwin";

        // 作者阴影
        int shadowColor = (alpha << 24) | 0x000000;
        context.drawString(font, author, 1, 1, shadowColor, false);

        // 作者主体（浅灰）
        int authorColor = (alpha << 24) | AUTHOR_COLOR;
        context.drawString(font, author, 0, 0, authorColor, false);

        context.pose().popPose();
    }

    /**
     * 渲染地图描述
     */
    private static void renderDescription(GuiGraphics context, Font font, String description, int y, int alpha) {
        // 计算描述的最大宽度（屏幕宽度减去左边距）
        int maxDescWidth = context.guiWidth() - LEFT_MARGIN - 20;

        // 分割描述为多行
        List<FormattedCharSequence> lines = font.split(Component.literal(description), maxDescWidth);

        // 限制显示行数（最多2行）
        int linesToShow = Math.min(2, lines.size());

        context.pose().pushPose();
        int descX = LEFT_MARGIN;
        context.pose().translate(descX, y, 0);
        context.pose().scale(DESC_SCALE, DESC_SCALE, 1.0f);

        // 渲染每一行描述
        for (int i = 0; i < linesToShow; i++) {
            FormattedCharSequence line = lines.get(i);

            // 如果是最后一行且有多行，添加省略号
            if (i == linesToShow - 1 && lines.size() > linesToShow) {
                String text = line.toString();
                if (text.length() > 3) {
                    text = text.substring(0, text.length() - 3) + "...";
                    line = FormattedCharSequence.forward(text, Style.EMPTY);
                }
            }

            // 行阴影
            int shadowColor = (alpha << 24) | 0x000000;
            context.drawString(font, line, 1, 1 + i * (font.lineHeight + LINE_SPACING), shadowColor, false);

            // 行主体（中灰色）
            int lineColor = (alpha << 24) | DESC_COLOR;
            context.drawString(font, line, 0, 0 + i * (font.lineHeight + LINE_SPACING), lineColor, false);
        }

        context.pose().popPose();
    }

    /**
     * 渲染地图ID
     */
    private static void renderMapId(GuiGraphics context, Font font, int alpha) {
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        // 地图ID显示在右下角（小字）
        String mapIdDisplay = "MAP: " + mapId;
        int mapIdWidth = font.width(mapIdDisplay);
        int mapIdX = screenWidth - mapIdWidth - 20;
        int mapIdY = screenHeight - 30;

        // ID阴影
        int shadowColor = (alpha << 24) | 0x000000;
        context.drawString(font, mapIdDisplay, mapIdX + 1, mapIdY + 1, shadowColor, false);

        // ID主体（浅灰色）
        int idColor = (alpha << 24) | 0x888888;
        context.drawString(font, mapIdDisplay, mapIdX, mapIdY, idColor, false);
    }

    /**
     * 设置要显示的地图详情
     */
    public static void setMapDetails(String mapId, String mapDescription, String mapAuthor) {
        MapDetailsRenderer.mapId = mapId;
        MapDetailsRenderer.mapDescription = mapDescription;
        MapDetailsRenderer.mapAuthor = mapAuthor != null ? mapAuthor : "";
        displayStartTime = System.currentTimeMillis() + GameConstants.FADE_TIME /20 * 1000;

        // 重置动画状态
        titleOffsetX = 0f;
        titlePulse = 1.0f;
        currentBarHeightRatio = 0f;
        barsAnimatingOut = false;
        finalAnimationPlayed = false;
    }

    /**
     * 设置要显示的地图详情（兼容旧版本）
     */
    public static void setMapDetails(String mapId, String mapDescription) {
        setMapDetails(mapId, mapDescription, "");
    }

    /**
     * 清除当前显示的地图详情
     */
    public static void clearMapDetails() {
        mapId = "";
        mapDescription = "";
        mapAuthor = "";
        currentBarHeightRatio = 0f;
        barsAnimatingOut = false;
        finalAnimationPlayed = false;
    }

    /**
     * 触发显示当前地图详情
     */
    public static void triggerMapDetails(String mapId) {
        AtomicReference<String> displayName = new AtomicReference<>(mapId);
        AtomicReference<String> description = new AtomicReference<>("");
        AtomicReference<String> author = new AtomicReference<>("");

        MapConfig.getInstance().getMaps().stream()
                .filter(map -> map.id.equals(mapId))
                .findFirst()
                .ifPresent(map -> {
                    displayName.set(map.displayName);
                    description.set(map.description);
                    author.set("allinTOKYO canyuesama haiman wifi_left guanzheqwq biantwin");
                });

        setMapDetails(mapId, description.get(), author.get());
    }
}