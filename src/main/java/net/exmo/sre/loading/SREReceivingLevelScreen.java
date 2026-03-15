package net.exmo.sre.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static io.wifi.starrailexpress.client.gui.MoodRenderer.random;
import static net.exmo.sre.loading.TrainLoadingScreen.STAR_COUNT;

@Environment(EnvType.CLIENT)
public class SREReceivingLevelScreen extends ReceivingLevelScreen {
    public SREReceivingLevelScreen(BooleanSupplier booleanSupplier, Reason reason) {
        super(booleanSupplier, reason);
        initStars();
        tips = buildTips();
        this.currentTipIndex = random.nextInt(tips.size());

    }
    private int currentTipIndex;
    private final List<Component> tips;
    private List<Component> buildTips() {
        return List.of(
                Component.translatable("loading.tip.starrailexpress.1"),
                Component.translatable("loading.tip.starrailexpress.2"),
                Component.translatable("loading.tip.starrailexpress.3"),
                Component.translatable("loading.tip.starrailexpress.4"),
                Component.translatable("loading.tip.starrailexpress.5")
        );
    }
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
            stars.add(new TrainLoadingScreen.StarParticle(x, y, vx, vy, size, brightness, phase));
        }
    }
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PANORAMA.render(graphics, mouseY, mouseX, mouseY, partialTick);
        // 不绘制默认背景，我们已自己绘制渐变
    }
    private final List<TrainLoadingScreen.StarParticle> stars = new ArrayList<>();
    private void renderStars(GuiGraphics graphics, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        long time = System.currentTimeMillis();

        for (TrainLoadingScreen.StarParticle star : stars) {
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
    private int ellipsisState = 0;
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

            renderIndeterminateProgress(graphics);


        // 5. 绘制随机提示
        renderTip(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }
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
    private void renderTip(GuiGraphics graphics) {
        Component tip = tips.get(currentTipIndex);
        int tipWidth = font.width(tip);
        int tipX = (width - tipWidth) / 2;
        int tipY = height - 110; // 进度条上方
        graphics.drawString(font, tip, tipX, tipY, 0xFFAAAAAA);
    }
}
