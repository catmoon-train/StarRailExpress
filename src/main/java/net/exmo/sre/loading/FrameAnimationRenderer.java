package net.exmo.sre.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 帧序列动画渲染器 —— 从资源包加载 PNG 帧序列，以指定帧率播放，
 * 并在相邻帧之间做 Alpha 交叉淡入淡出（动态补帧），用作背景。
 * <p>
 * 帧文件放在 {@code assets/starrailexpress/textures/gui/title/video/} 目录下，
 * 文件名按字典序排列即可（如 frame_0000.png, frame_0001.png …）。
 */
@Environment(EnvType.CLIENT)
public class FrameAnimationRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FRAMES_DIR = "textures/gui/title/video";

    private final List<ResourceLocation> frames = new ArrayList<>();
    private final float fps;
    private long startTimeMs = -1;
    private boolean loaded = false;

    /**
     * @param fps 播放帧率（例如 24.0F 或 30.0F）
     */
    public FrameAnimationRenderer(float fps) {
        this.fps = fps;
    }

    // ─── 加载 ────────────────────────────────────────────────────────

    /**
     * 从资源包扫描帧序列。应在 {@code Screen.init()} 或首次渲染时调用。
     */
    public void loadFrames() {
        frames.clear();
        Map<ResourceLocation, Resource> resources =
                Minecraft.getInstance().getResourceManager()
                        .listResources(FRAMES_DIR, loc ->
                                loc.getNamespace().equals(SRE.MOD_ID)
                                        && loc.getPath().endsWith(".png"));

        for (ResourceLocation loc : resources.keySet()) {
            frames.add(loc);
        }

        // 按路径排序确保帧顺序
        frames.sort(Comparator.comparing(ResourceLocation::getPath));
        loaded = true;
        startTimeMs = -1;

        if (frames.isEmpty()) {
            LOGGER.warn("[SRE] No video frames found in assets/{}/{}/",
                    SRE.MOD_ID, FRAMES_DIR);
        } else {
            LOGGER.info("[SRE] Loaded {} video frames for title background", frames.size());
        }
    }

    public boolean hasFrames() {
        return loaded && !frames.isEmpty();
    }

    public int getFrameCount() {
        return frames.size();
    }

    // ─── 渲染 ────────────────────────────────────────────────────────

    /**
     * 渲染动画帧到全屏，带帧间交叉淡入淡出补帧效果。
     *
     * @param g            GuiGraphics
     * @param screenWidth  屏幕宽度（GUI 坐标）
     * @param screenHeight 屏幕高度（GUI 坐标）
     * @param delta         partial tick
     */
    public void render(GuiGraphics g, int screenWidth, int screenHeight, float delta) {
        if (!hasFrames()) return;

        long now = Util.getMillis();
        if (startTimeMs < 0) startTimeMs = now;

        float elapsedSec = (now - startTimeMs) / 1000.0F;
        float totalFrames = frames.size();

        // 当前帧位置（浮点），整数部分 = 帧索引，小数部分 = 补帧插值因子
        float framePos = (elapsedSec * fps) % totalFrames;
        int frameIndex = (int) framePos;
        float blend = framePos - frameIndex; // 0.0 ~ 1.0

        int nextIndex = (frameIndex + 1) % frames.size();
        ResourceLocation currentFrame = frames.get(frameIndex);
        ResourceLocation nextFrame = frames.get(nextIndex);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 渲染当前帧（完全不透明）
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(currentFrame, 0, 0, 0, 0,
                screenWidth, screenHeight, screenWidth, screenHeight);

        // 渲染下一帧（用 blend 因子作为 alpha 实现交叉淡入淡出补帧）
        if (blend > 0.005F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, blend);
            g.blit(nextFrame, 0, 0, 0, 0,
                    screenWidth, screenHeight, screenWidth, screenHeight);
        }

        // 重置
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * 渲染动画帧到全屏，整体带额外透明度（用于淡入淡出过渡）。
     */
    public void render(GuiGraphics g, int screenWidth, int screenHeight,
                       float delta, float overallAlpha) {
        if (!hasFrames()) return;

        long now = Util.getMillis();
        if (startTimeMs < 0) startTimeMs = now;

        float elapsedSec = (now - startTimeMs) / 1000.0F;
        float totalFrames = frames.size();

        float framePos = (elapsedSec * fps) % totalFrames;
        int frameIndex = (int) framePos;
        float blend = framePos - frameIndex;
        int nextIndex = (frameIndex + 1) % frames.size();

        ResourceLocation currentFrame = frames.get(frameIndex);
        ResourceLocation nextFrame = frames.get(nextIndex);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 当前帧叠加整体 alpha
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, overallAlpha);
        g.blit(currentFrame, 0, 0, 0, 0,
                screenWidth, screenHeight, screenWidth, screenHeight);

        // 下一帧：alpha = overallAlpha * blend
        if (blend > 0.005F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, overallAlpha * blend);
            g.blit(nextFrame, 0, 0, 0, 0,
                    screenWidth, screenHeight, screenWidth, screenHeight);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * 重置动画到起始帧。
     */
    public void reset() {
        startTimeMs = -1;
    }
}
