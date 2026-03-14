package net.exmo.sre.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Random;

/**
 * 星穹铁道风格加载界面
 * 包含：列车动画、进度条、随机Tip、进度百分比
 */
@Environment(EnvType.CLIENT)
public class TrainLoadingScreen extends Screen {
    // ===== 配置常量 =====
    private static final long NARRATION_DELAY_MS = 2000L;
    private static final long TIP_CHANGE_INTERVAL = 5000L; // 5秒切换tip
    private static final int TRAIN_SPEED = 3; // 列车移动速度 (像素/帧)
    private static final int TRAIN_WIDTH = 200;  // 列车纹理宽度
    private static final int TRAIN_HEIGHT = 80;  // 列车纹理高度
    private static final int PROGRESS_BAR_HEIGHT = 20;
    private static final int PROGRESS_BAR_WIDTH = 600;

    // ===== 纹理资源 =====
    private static final ResourceLocation TRAIN_TEXTURE = ResourceLocation.tryBuild("starrailexpress", "textures/gui/train.png");
    private static final ResourceLocation PROGRESS_BAR_BG = ResourceLocation.tryBuild("starrailexpress", "textures/gui/progress_bar_bg.png");
    private static final ResourceLocation PROGRESS_BAR_FILL = ResourceLocation.tryBuild("starrailexpress", "textures/gui/progress_bar_fill.png");

    // ===== 状态 =====
    private final StoringChunkProgressListener progressListener;  // 进度提供者
    private boolean done;
    private long lastNarration = -1L;

    // 提示系统
    private final List<Component> tips;
    private int currentTipIndex;
    private long lastTipChangeTime;

    // 列车动画
    private int trainX;          // 当前列车X坐标（屏幕左侧外开始，向右移动）
    private int trainDirection = 1; // 1 向右, -1 向左 (目前只用向右)
    private long lastTickTime;

    // 随机数生成器
    private final Random random = new Random();




    /**
     * 构造函数
     * @param progressListener 进度提供者
     */
    public TrainLoadingScreen(StoringChunkProgressListener progressListener) {
        super(Component.translatable("screen.starrailexpress.loading.title"));
        this.progressListener = progressListener;
        this.tips = buildTips();
        this.currentTipIndex = random.nextInt(tips.size());
        this.lastTipChangeTime = Util.getMillis();
        this.trainX = -TRAIN_WIDTH; // 从左侧外开始
    }

    /**
     * 构建随机提示列表（可从语言文件加载）
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
        return false; // 加载界面不可关闭
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
            narrationElementOutput.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.translatable("narrator.loading.done"));
        } else {
            narrationElementOutput.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getProgressComponent());
        }
    }

    /**
     * 获取带百分比的进度文本
     */
    private Component getProgressComponent() {
        int percent = Mth.clamp((int) (progressListener.getProgress() * 100), 0, 100);
        return Component.translatable("loading.progress", percent);
    }

    @Override
    public void tick() {
        long now = Util.getMillis();

        // 更新tip
        if (now - lastTipChangeTime > TIP_CHANGE_INTERVAL) {
            currentTipIndex = (currentTipIndex + 1) % tips.size();
            lastTipChangeTime = now;
        }

        // 更新列车位置（从左向右匀速移动，超出右侧后重置到左侧）
        trainX += TRAIN_SPEED;
        if (trainX > width) {
            trainX = -TRAIN_WIDTH;
        }

        // 定期叙述（用于辅助功能）
        if (now - lastNarration > NARRATION_DELAY_MS) {
            lastNarration = now;
            this.triggerImmediateNarration(true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. 绘制深色渐变背景（类似星穹铁道）
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fillGradient(0, 0, width, height, 0xC0102030, 0xD0081018);

        // 2. 绘制列车动画（中间偏上）
        renderTrain(graphics);

        // 3. 绘制进度条和百分比（底部）
        renderProgressBar(graphics);

        // 4. 绘制随机提示（进度条上方）
        renderTip(graphics);

        // 5. 可选：绘制一些星光粒子效果（简化版，可省略）
        renderStars(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 绘制列车动画
     */
    private void renderTrain(GuiGraphics graphics) {
        int trainY = height / 3; // 垂直位置：屏幕上方1/3处
        RenderSystem.enableBlend();
        // 使用纹理绘制列车，若无纹理则用彩色矩形占位
        try {
            graphics.blit(TRAIN_TEXTURE, trainX, trainY, 0, 0, TRAIN_WIDTH, TRAIN_HEIGHT, TRAIN_WIDTH, TRAIN_HEIGHT);
        } catch (Exception e) {
            // 降级：绘制一个橙色矩形代表列车
            graphics.fill(trainX, trainY, trainX + TRAIN_WIDTH, trainY + TRAIN_HEIGHT, 0xFFFFAA00);
            // 添加车窗（白色小矩形）
            graphics.fill(trainX + 40, trainY + 20, trainX + 80, trainY + 40, 0xFFFFFFFF);
            graphics.fill(trainX + 120, trainY + 20, trainX + 160, trainY + 40, 0xFFFFFFFF);
        }
        RenderSystem.disableBlend();
    }

    /**
     * 绘制进度条（底部居中）
     */
    private void renderProgressBar(GuiGraphics graphics) {
        int barX = (width - PROGRESS_BAR_WIDTH) / 2;
        int barY = height - 80; // 距离底部80像素

        // 背景
        graphics.fill(barX, barY, barX + PROGRESS_BAR_WIDTH, barY + PROGRESS_BAR_HEIGHT, 0xFF2A2F3F);
        graphics.renderOutline(barX, barY, PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT, 0xFF3A4050);

        // 填充进度（带光泽效果）
        float progress = Mth.clamp(progressListener.getProgress(), 0.0f, 1.0f);
        int fillWidth = (int) (progress * PROGRESS_BAR_WIDTH);
        if (fillWidth > 0) {
            // 主填充色（霓虹蓝渐变）
            graphics.fillGradient(barX, barY, barX + fillWidth, barY + PROGRESS_BAR_HEIGHT,
                    0xFF44AAFF, 0xFF2266CC);
            // 顶部高光
            graphics.fill(barX, barY, barX + fillWidth, barY + 2, 0xAAFFFFFF);
        }

        // 百分比文字
        String percentText = (int)(progress * 100) + "%";
        int textWidth = font.width(percentText);
        graphics.drawString(font, percentText, barX + PROGRESS_BAR_WIDTH + 10, barY + (PROGRESS_BAR_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF);
    }

    /**
     * 绘制随机Tip
     */
    private void renderTip(GuiGraphics graphics) {
        Component tip = tips.get(currentTipIndex);
        int tipWidth = font.width(tip);
        int tipX = (width - tipWidth) / 2;
        int tipY = height - 120; // 进度条上方40像素
        graphics.drawString(font, tip, tipX, tipY, 0xFFAAAAAA);
    }

    /**
     * 绘制简单的星光粒子（装饰）
     */
    private void renderStars(GuiGraphics graphics) {
        // 随机绘制一些白色小点，模拟星空
        long time = System.currentTimeMillis() / 1000;
        for (int i = 0; i < 50; i++) {
            int x = (int) ((i * 37 + time) % width);
            int y = (int) ((i * 73 + time * 2) % (height / 2));
            graphics.fill(x, y, x + 1, y + 1, 0x88FFFFFF);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 调用父类绘制默认背景（可选，但我们已经自己绘制了渐变）
        // super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
}