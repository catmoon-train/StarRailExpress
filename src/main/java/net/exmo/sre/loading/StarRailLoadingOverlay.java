package net.exmo.sre.loading;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.SRE;
import net.exmo.sre.loading.texture.ConfigTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ReloadInstance;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * 星穹铁道风格的加载覆盖层
 * 包含列车动画、细进度条（光点流动）、动态提示和百分比显示
 */
public class StarRailLoadingOverlay extends Overlay {
    private final Minecraft minecraft;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final boolean fadeIn;

    private float currentProgress;
    private long fadeOutStart = -1L;
    private long fadeInStart = -1L;
    private float animationTime; // 用于列车晃动和光点流动

    // 纹理资源（请将对应图片放入 assets/yourmod/textures/gui/ 下）
    private static final ResourceLocation TRAIN_TEXTURE = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "textures/gui/starrail_train.png");
    private static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "background.png");
    private static final ResourceLocation PROGRESS_BAR_BG = ResourceLocation.fromNamespaceAndPath("yourmod", "textures/gui/progress_bar_bg.png");
    private static final ResourceLocation PROGRESS_BAR_FILL = ResourceLocation.fromNamespaceAndPath("yourmod", "textures/gui/progress_bar_fill.png");
    private static final ResourceLocation SPOT_TEXTURE = ResourceLocation.fromNamespaceAndPath("yourmod", "textures/gui/spot.png"); // 光点纹理（可选）

    public static void registerTextures(Minecraft minecraft) {
        minecraft.getTextureManager().register(BG_TEXTURE, new ConfigTexture(BG_TEXTURE));
        minecraft.getTextureManager().register(TRAIN_TEXTURE, new SimpleTexture(TRAIN_TEXTURE));
    }
    @Environment(EnvType.CLIENT)
    static class LogoTexture extends SimpleTexture {
        public LogoTexture(ResourceLocation resourceLocation) {
            super(resourceLocation);
        }

        protected SimpleTexture.TextureImage getTextureImage(ResourceManager resourceManager) {
            VanillaPackResources vanillaPackResources = Minecraft.getInstance().getVanillaPackResources();
            IoSupplier<InputStream> ioSupplier = vanillaPackResources.getResource(PackType.CLIENT_RESOURCES,location);
            if (ioSupplier == null) {
                return new SimpleTexture.TextureImage(new FileNotFoundException(location.toString()));
            } else {
                try (InputStream inputStream = (InputStream)ioSupplier.get()) {
                    return new SimpleTexture.TextureImage(new TextureMetadataSection(true, true), NativeImage.read(inputStream));
                } catch (IOException iOException) {
                    return new SimpleTexture.TextureImage(iOException);
                }
            }
        }
    }
    // 随机提示列表
    private static final List<String> TIPS = List.of(
            "列车正在穿越星穹……",
            "开拓者，请稍候。",
            "前方站台：未知世界",
            "燃料填充中……",
            "星穹铁道感谢您的等待",
            "正在同步银河坐标",
            "列车组全员准备就绪"
    );
    private String currentTip;
    private long tipChangeTime;
    private static final long TIP_INTERVAL = 5000; // 5秒切换

    public StarRailLoadingOverlay(Minecraft mc, ReloadInstance reloader,
                                  Consumer<Optional<Throwable>> errorConsumer, boolean fadeIn) {
        this.minecraft = mc;
        this.reload = reloader;
        this.onFinish = errorConsumer;
        this.fadeIn = fadeIn;
        this.currentTip = TIPS.get(0);
        this.tipChangeTime = Util.getMillis();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        long currentTime = Util.getMillis();

        // 处理淡入淡出时间
        if (fadeIn && fadeInStart == -1L) {
            fadeInStart = currentTime;
        }

        float fadeOut = fadeOutStart > -1L ? (float) (currentTime - fadeOutStart) / 1000.0F : -1.0F;
        float fadeInProgress = fadeInStart > -1L ? (float) (currentTime - fadeInStart) / 500.0F : -1.0F;

        // 计算当前透明度
        float alpha = 1.0F;
        if (fadeOut >= 0.0F) {
            alpha = 1.0F - Mth.clamp(fadeOut - 1.0F, 0.0F, 1.0F); // 淡出
        } else if (fadeIn) {
            alpha = Mth.clamp(fadeInProgress, 0.0F, 1.0F); // 淡入
        }

        // 如果完全淡出则移除覆盖层
        if (fadeOut >= 2.0F) {
            this.minecraft.setOverlay(null);
            return;
        }

        // 绘制深色半透明背景
        drawBackground(guiGraphics, screenWidth, screenHeight, alpha);

        // 更新动画时间（用于列车晃动和光点流动）
        animationTime += partialTick * 0.05F;

        // 绘制列车（水平晃动）
        drawTrain(guiGraphics, screenWidth, screenHeight, alpha, animationTime);

        // 更新进度（平滑过渡）
        float actualProgress = reload.getActualProgress();
        currentProgress = Mth.clamp(currentProgress * 0.95F + actualProgress * 0.05F, 0.0F, 1.0F);

        // 绘制进度条、光点动画和百分比文本
        drawProgressBar(guiGraphics, screenWidth, screenHeight, currentProgress, alpha, animationTime);

        // 绘制提示文本
        drawTip(guiGraphics, screenWidth, screenHeight, alpha, currentTime);

        // 检查加载是否完成，开始淡出
        if (fadeOutStart == -1L && reload.isDone()) {
            fadeOutStart = Util.getMillis();
            try {
                reload.checkExceptions();
                onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                onFinish.accept(Optional.of(t));
            }
            if (minecraft.screen != null) {
                minecraft.screen.init(minecraft, screenWidth, screenHeight);
            }
        }
    }

    /**
     * 绘制深色背景（带透明度）
     */
    private void drawBackground(GuiGraphics graphics, int width, int height, float alpha) {

        RenderSystem.enableBlend();
        RenderSystem.blendEquation(32774);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, TRAIN_TEXTURE);
        graphics.blit(BG_TEXTURE, 0, 0, 0, 0, width, height, width, height);


//        int color = FastColor.ARGB32.color((int) (alpha * 255), 10, 10, 20); // 深蓝黑
//        graphics.fill(0, 0, width, height, color);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制列车（使用简单纹理 + 水平晃动）
     */
    private void drawTrain(GuiGraphics graphics, int width, int height, float alpha, float time) {
        // 假设纹理原始尺寸，可根据需要调整
        int textureWidth = 400;
        int textureHeight = 200;
        int targetWidth = textureWidth / 2;   // 缩小至一半
        int targetHeight = textureHeight / 2;

        int centerX = width / 2;
        int centerY = height / 2 - 40; // 垂直居中偏上

        // 水平晃动幅度 ±8 像素
        float offsetX = (float) Math.sin(time * 2.5) * 8;

        int x = centerX - targetWidth / 2 + (int) offsetX;
        int y = centerY - targetHeight / 2;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
//        RenderSystem.setShaderTexture(0, TRAIN_TEXTURE);
//        graphics.blit(TRAIN_TEXTURE, x, y, targetWidth, targetHeight,
//                0.0F, 0.0F, textureWidth, textureHeight, textureWidth, textureHeight);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 绘制进度条（细长、带流动光点、百分比）
     */
    private void drawProgressBar(GuiGraphics graphics, int width, int height,
                                 float progress, float alpha, float time) {
        int barWidth = 600;          // 总宽度
        int barHeight = 6;           // 细条高度
        int barX = (width - barWidth) / 2;
        int barY = height - 80;      // 距离底部80像素

        // 背景条（半透明深色）
        int bgColor = FastColor.ARGB32.color((int) (alpha * 150), 30, 30, 40);
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, bgColor);

        // 进度填充（亮蓝色）
        int fillWidth = (int) (barWidth * progress);
        int fillColor = FastColor.ARGB32.color((int) (alpha * 255), 100, 200, 255);
        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);

        // 流动光点效果（在填充区域内移动的白色光条）
        if (fillWidth > 0) {
            RenderSystem.enableBlend();
            // 光点位置：随时间在填充范围内往复移动
            float t = (float) Math.sin(time * 5) * 0.5F + 0.5F; // 0~1 振荡
            float spotCenterX = barX + t * fillWidth;
            int spotWidth = 30;
            int spotColor = FastColor.ARGB32.color((int) (alpha * 220), 255, 255, 255);
            graphics.fill((int) (spotCenterX - spotWidth / 2), barY,
                    (int) (spotCenterX + spotWidth / 2), barY + barHeight, spotColor);
            RenderSystem.disableBlend();
        }

        // 绘制百分比文本
        String percentText = (int) (progress * 100) + "%";
        int textColor = FastColor.ARGB32.color((int) (alpha * 255), 255, 255, 255);
        graphics.drawString(minecraft.font, percentText,
                barX + barWidth + 15, barY - 2, textColor);
    }

    /**
     * 绘制提示文本（定时随机切换）
     */
    private void drawTip(GuiGraphics graphics, int width, int height, float alpha, long currentTime) {
        // 定时更换提示
        if (currentTime - tipChangeTime > TIP_INTERVAL) {
            tipChangeTime = currentTime;
            Random rand = new Random();
            currentTip = TIPS.get(rand.nextInt(TIPS.size()));
        }

        int textColor = FastColor.ARGB32.color((int) (alpha * 200), 200, 200, 200); // 浅灰色
        int x = width / 2 - minecraft.font.width(currentTip) / 2;
        int y = height - 40; // 进度条下方
        graphics.drawString(minecraft.font, currentTip, x, y, textColor);
    }

    @Override
    public boolean isPauseScreen() {
        return true; // 允许暂停游戏
    }

    /**
     * 工厂方法，用于创建实例（可被Mod加载器调用）
     */
    public static StarRailLoadingOverlay newInstance(Minecraft mc, ReloadInstance ri,
                                                     Consumer<Optional<Throwable>> handler, boolean fadeIn) {
        return new StarRailLoadingOverlay(mc, ri, handler, fadeIn);
    }
}