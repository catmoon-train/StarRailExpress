package net.exmo.sre.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * 现代化星穹铁道风格加载界面
 * 纯图形绘制，无纹理依赖，包含动态星空粒子、列车动画、进度提示
 */
@Environment(EnvType.CLIENT)
public class TrainLoadingScreen extends Screen {
    // ===== 常量 =====
    private static final long NARRATION_DELAY_MS = 2000L;
    private static final long TIP_CHANGE_INTERVAL = 5000L;      // 提示切换间隔
    private static final long ELLIPSIS_UPDATE_INTERVAL = 500L; // “加载世界中”省略号更新间隔
    private static final int TRAIN_SPEED = 3;                   // 列车移动速度
    private static final int TRAIN_WIDTH = 240;                 // 列车图形宽度
    private static final int TRAIN_HEIGHT = 80;                 // 列车图形高度
    private static final int PROGRESS_BAR_HEIGHT = 8;
    private static final int PROGRESS_BAR_WIDTH = 500;
    public static final int STAR_COUNT = 120;                  // 星星数量

    // ===== 状态 =====
    private final StoringChunkProgressListener progressListener;
    private final boolean hasProgressListener;
    private boolean done;
    private long lastNarration = -1L;
    private final BooleanSupplier levelReceived;
    private final long createdAt;

    // 提示系统
    private final List<Component> tips;
    private int currentTipIndex;
    private long lastTipChangeTime;

    // 列车动画
    private int trainX;          // 当前X坐标（从左侧外开始）
    private long lastTickTime;

    // 不确定进度动画（加载世界中）
    private int ellipsisState = 0;           // 0-3 表示省略号个数
    private long lastEllipsisUpdate;

    // 星空粒子系统
    private final List<StarParticle> stars = new ArrayList<>();
    private final Random random = new Random();

    // ===== 内部粒子类 =====
    public static class StarParticle {
        double x, y;           // 当前位置
        double vx, vy;         // 速度（像素/秒）
        float size;            // 大小
        float brightness;      // 亮度（0~1）
        float phase;           // 闪烁相位

        StarParticle(double x, double y, double vx, double vy, float size, float brightness, float phase) {
            this.x = x; this.y = y;
            this.vx = vx; this.vy = vy;
            this.size = size; this.brightness = brightness;
            this.phase = phase;
        }
    }

    public TrainLoadingScreen(StoringChunkProgressListener progressListener, BooleanSupplier levelReceived) {
        super(Component.translatable("screen.starrailexpress.loading.title"));
        this.levelReceived = levelReceived;
        this.progressListener = progressListener;
        this.hasProgressListener = progressListener != null;
        this.tips = buildTips();
        this.currentTipIndex = random.nextInt(tips.size());
        this.lastTipChangeTime = Util.getMillis();
        this.trainX = -TRAIN_WIDTH; // 从左侧外开始
        this.createdAt = System.currentTimeMillis();
        // 初始化星空粒子
        initStars();
    }

    /**
     * 初始化星空粒子
     */
    private void initStars() {
        stars.clear();
        for (int i = 0; i < STAR_COUNT; i++) {
            double x = random.nextDouble() * 10000; // 使用大范围，在渲染时取模适应屏幕
            double y = random.nextDouble() * 10000;
            double vx = (random.nextDouble() - 0.5) * 2;  // 缓慢漂移
            double vy = (random.nextDouble() - 0.5) * 2;
            float size = 0.5f + random.nextFloat() * 2.5f;
            float brightness = 0.3f + random.nextFloat() * 0.7f;
            float phase = random.nextFloat() * (float)Math.PI * 2;
            stars.add(new StarParticle(x, y, vx, vy, size, brightness, phase));
        }
    }

    /**
     * 构建随机提示列表
     */
    private List<Component> buildTips() {
        return List.of(
                Component.translatable("loading.tip.starrailexpress.1"),
                Component.translatable("loading.tip.starrailexpress.2"),
                Component.translatable("loading.tip.starrailexpress.3"),
                Component.translatable("loading.tip.starrailexpress.4"),
                Component.translatable("loading.tip.starrailexpress.5")
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public void removed() {
        this.done = true;
        this.triggerImmediateNarration(true);
    }

    @Override
    protected void updateNarratedWidget(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
        if (done) {
            narrationElementOutput.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                    Component.translatable("narrator.loading.done"));
        } else if (hasProgressListener) {
            narrationElementOutput.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                    getProgressComponent());
        } else {
            narrationElementOutput.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                    Component.translatable("loading.world.generating"));
        }
    }

    private Component getProgressComponent() {
        int percent = Mth.clamp((int) (progressListener.getProgress() * 100), 0, 100);
        return Component.translatable("loading.progress", percent);
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        if (this.levelReceived.getAsBoolean() || System.currentTimeMillis() > this.createdAt + 30000L) {
            this.onClose();
        }
        // 更新tip
        if (now - lastTipChangeTime > TIP_CHANGE_INTERVAL) {
            currentTipIndex = (currentTipIndex + 1) % tips.size();
            lastTipChangeTime = now;
        }

        // 更新列车位置
        trainX += TRAIN_SPEED;
        if (trainX > width) {
            trainX = -TRAIN_WIDTH;
        }

        // 更新星空粒子（模拟缓慢漂移）
        float deltaTime = 0.05f; // 每tick约50ms，简化处理
        for (StarParticle star : stars) {
            star.x += star.vx * deltaTime;
            star.y += star.vy * deltaTime;

            // 边界环绕（让星星无限延续）
            if (star.x < 0) star.x += 10000;
            if (star.x > 10000) star.x -= 10000;
            if (star.y < 0) star.y += 10000;
            if (star.y > 10000) star.y -= 10000;

            // 闪烁：亮度随相位变化
            star.phase += 0.02f;
            if (star.phase > Math.PI * 2) star.phase -= Math.PI * 2;
        }

        // 如果不确定进度，更新省略号动画
        if (!hasProgressListener) {
            if (now - lastEllipsisUpdate > ELLIPSIS_UPDATE_INTERVAL) {
                ellipsisState = (ellipsisState + 1) % 4;
                lastEllipsisUpdate = now;
            }
        }

        // 辅助功能叙述
        if (now - lastNarration > NARRATION_DELAY_MS) {
            lastNarration = now;
            this.triggerImmediateNarration(true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. 绘制深邃的太空背景
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fillGradient(0, 0, width, height, 0x420A0A1A, 0x48101020);

        // 2. 绘制星空粒子
        renderStars(graphics, partialTick);

//        // 3. 绘制列车（中间偏上）
//        renderTrain(graphics);

        // 4. 绘制进度区域（底部）
        if (hasProgressListener) {
            renderProgressBar(graphics);
        } else {
            renderIndeterminateProgress(graphics);
        }

        // 5. 绘制随机提示
        renderTip(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 绘制星空粒子
     */
    private void renderStars(GuiGraphics graphics, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        long time = System.currentTimeMillis();

        for (StarParticle star : stars) {
            // 将粒子坐标映射到当前屏幕（通过取模）
            int screenX = (int) (star.x % width);
            int screenY = (int) (star.y % height);

            // 亮度闪烁（利用正弦波）
            float brightness = star.brightness * (0.6f + 0.4f * Mth.sin((time / 300f) + star.phase));

            // 颜色：白色带淡蓝/淡黄
            int alpha = (int) (brightness * 255) << 24;
            int color = alpha | 0xFFFFFF;

            // 绘制像素点（大小可变）
            int sizeInt = Math.max(1, (int) star.size);
            graphics.fill(screenX, screenY, screenX + sizeInt, screenY + sizeInt, color);
        }
        RenderSystem.disableBlend();
    }

    /**
     * 绘制列车（纯图形）
     */
    private void renderTrain(GuiGraphics graphics) {
        int trainY = height / 3; // 垂直位置：屏幕上方1/3处

        // 启用混合以绘制半透明光晕
        RenderSystem.enableBlend();

        // 车身（深色金属质感）
        int bodyColor = 0xFF2A2F3F;
        graphics.fill(trainX, trainY, trainX + TRAIN_WIDTH, trainY + TRAIN_HEIGHT, bodyColor);
        // 车身轮廓
        graphics.renderOutline(trainX, trainY, TRAIN_WIDTH, TRAIN_HEIGHT, 0xFF3A4050);

        // 车窗（半透明蓝色）
        int windowColor = 0xAA88CCFF;
        graphics.fill(trainX + 40, trainY + 20, trainX + 80, trainY + 40, windowColor);
        graphics.fill(trainX + 120, trainY + 20, trainX + 160, trainY + 40, windowColor);
        graphics.fill(trainX + 200, trainY + 25, trainX + 220, trainY + 35, 0xAAFFAA00); // 车灯

        // 车顶装饰（白色线条）
        graphics.fill(trainX + 10, trainY - 4, trainX + TRAIN_WIDTH - 10, trainY, 0xFF4A5060);

        // 添加发光效果（车身周围的光晕）
        int glowColor = 0x3366AAFF;
        graphics.fill(trainX - 5, trainY - 5, trainX + TRAIN_WIDTH + 5, trainY + TRAIN_HEIGHT + 5, glowColor);
        graphics.fill(trainX - 3, trainY - 3, trainX + TRAIN_WIDTH + 3, trainY + TRAIN_HEIGHT + 3, 0x00); // 擦除中间保留轮廓

        RenderSystem.disableBlend();
    }

    /**
     * 绘制进度条（有具体进度）
     */
    private void renderProgressBar(GuiGraphics graphics) {
        int barX = (width - PROGRESS_BAR_WIDTH) / 2;
        int barY = height - 70;

        // 背景
        graphics.fill(barX, barY, barX + PROGRESS_BAR_WIDTH, barY + PROGRESS_BAR_HEIGHT, 0xFF1A1F2A);
        graphics.renderOutline(barX, barY, PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT, 0xFF2A3040);

        float progress = Mth.clamp(progressListener.getProgress(), 0.0f, 1.0f);
        int fillWidth = (int) (progress * PROGRESS_BAR_WIDTH);
        if (fillWidth > 0) {
            // 霓虹蓝渐变填充
            graphics.fillGradient(barX, barY, barX + fillWidth, barY + PROGRESS_BAR_HEIGHT,
                    0xFF44AAFF, 0xFF2266CC);
            // 顶部高光
            graphics.fill(barX, barY, barX + fillWidth, barY + 2, 0xAAFFFFFF);
        }

        // 百分比文字
        String percentText = (int) (progress * 100) + "%";
        graphics.drawString(font, percentText,
                barX + PROGRESS_BAR_WIDTH + 12,
                barY + (PROGRESS_BAR_HEIGHT - font.lineHeight) / 2,
                0xFFFFFFFF);
    }

    /**
     * 绘制不确定进度（加载世界中）
     */
    private void renderIndeterminateProgress(GuiGraphics graphics) {
        int centerX = width / 2;
        int baseY = height - 70;

        // 主文本
        String baseText = Component.translatable("loading.world.generating").getString();
        String ellipsis = ".".repeat(ellipsisState);
        String fullText = baseText + ellipsis;
        int textWidth = font.width(fullText);
        graphics.drawString(font, fullText, centerX - textWidth / 2, baseY, 0xFFAAAAAA);

        // 下方绘制动态光点（模拟加载指示器）
        int dotCount = 5;
        int dotSpacing = 20;
        int dotY = baseY + font.lineHeight + 10;
        long time = System.currentTimeMillis();
        for (int i = 0; i < dotCount; i++) {
            int alpha = (int) (128 + 127 * Mth.sin((float) ((time / 200.0) + i * 2.0)));
            int color = (alpha << 24) | 0x88AAFF;
            int dotX = centerX + (i - dotCount/2) * dotSpacing;
            graphics.fill(dotX - 3, dotY - 3, dotX + 3, dotY + 3, color);
        }
    }

    /**
     * 绘制随机提示
     */
    private void renderTip(GuiGraphics graphics) {
        Component tip = tips.get(currentTipIndex);
        int tipWidth = font.width(tip);
        int tipX = (width - tipWidth) / 2;
        int tipY = height - 110; // 进度条上方
        graphics.drawString(font, tip, tipX, tipY, 0xFFAAAAAA);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PANORAMA.render(graphics, mouseY, mouseX, mouseY, partialTick);
        // 不绘制默认背景，我们已自己绘制渐变
    }
}