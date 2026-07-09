package io.wifi.starrailexpress.client.gui.screen.mapui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.approach;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.easeOutCubic;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.hash;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.mix;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.textureExists;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.withAlpha;

/**
 * 地图界面的共用背景：程序化 3D 低多边形网格 + 每张地图的全屏背景图（交叉淡入）+ 开屏动画。
 * 由 {@code MapVoteScreen} 与 {@code MapRotationScreen} 共用。
 *
 * <p>
 * 用法：{@link #resize(int, int)} 于 Screen.init，{@link #advance(String)} 于每帧 render 开头，
 * {@link #renderBackdrop(GuiGraphics)} 于 renderBackground，{@link #renderOpenTransition(GuiGraphics)} 于 render 末尾。
 */
public final class MapBackdropRenderer {

    /** 每张地图的全屏背景图，按 id 自动查找。 */
    private static final String BACKGROUND_PATH = "textures/gui/maps/background/%s.png";
    /** 没有专用全屏背景时退回到缩略图。 */
    private static final String THUMBNAIL_PATH = "textures/gui/maps/%s.png";

    private static final int ACCENT = 0xFF8B6914;
    private static final int ACCENT_BRIGHT = 0xFFC9A84C;
    private static final int VEIL = 0x05070B;

    // 全屏背景：按地图 id 解析并缓存（含"没有图"这个结果），切换时交叉淡入
    private final Map<String, ResourceLocation> backgroundCache = new HashMap<>();
    private String backgroundId;
    private String previousBackgroundId;
    /** 新背景的淡入进度：0 = 完全是旧背景，1 = 完全是新背景。 */
    private float backgroundFade = 1.0f;
    /** 切换发生时旧背景的实际不透明度，避免中途换图时旧图突然跳到全亮。 */
    private float previousBackgroundAlpha;
    /** 当前是否有全屏背景图。 */
    private boolean hasBackgroundTexture;

    private float introProgress;
    private float animTime;
    private long lastFrameNanos = System.nanoTime();

    private int width;
    private int height;

    // 低多边形网格（按屏幕尺寸生成一次）
    private int meshCols;
    private int meshRows;
    private float[] meshX;
    private float[] meshY;
    private float[] meshPhase;
    private int[] meshTriColor;

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /** Screen.init 调用；重置开屏动画并按新尺寸重建网格。 */
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        this.introProgress = 0.0f;
        this.lastFrameNanos = System.nanoTime();
        buildMesh();
    }

    /**
     * 每帧推进动画。
     *
     * @param targetMapId 当前"目标地图"的 id，null 表示不显示地图背景
     * @return 本帧的 dt（秒，已 clamp），供调用方推进自己的动画
     */
    public float advance(String targetMapId) {
        long now = System.nanoTime();
        float dt = Mth.clamp((now - lastFrameNanos) / 1.0E9f, 0.0f, 0.05f);
        lastFrameNanos = now;
        animTime += dt;
        introProgress = approach(introProgress, 1.0f, dt, 6.0f);

        String target = targetMapId != null && backgroundTexture(targetMapId) != null ? targetMapId : null;
        hasBackgroundTexture = target != null;
        if (!Objects.equals(target, backgroundId)) {
            // 折叠成单张"旧图"：它的不透明度取当前屏幕上背景图的实际覆盖率，
            // 否则在上一次淡入还没结束时换图，会丢掉底下那层、亮度骤降。
            float coverage = backgroundCoverage();
            previousBackgroundId = backgroundId != null ? backgroundId : previousBackgroundId;
            previousBackgroundAlpha = coverage;
            backgroundId = target;
            backgroundFade = 0.0f;
        }
        backgroundFade = approach(backgroundFade, 1.0f, dt, 5.0f);
        if (backgroundFade > 0.995f) {
            previousBackgroundId = null;
        }
        return dt;
    }

    /** 原始开屏进度（未缓动），用于元素错峰入场。 */
    public float introProgress() {
        return introProgress;
    }

    /** 缓动后的开屏进度。 */
    public float intro() {
        return easeOutCubic(Mth.clamp(introProgress * 1.15f, 0.0f, 1.0f));
    }

    /** 界面打开以来累计的秒数，用于脉冲 / 漂移。 */
    public float animTime() {
        return animTime;
    }

    // ------------------------------------------------------------------
    // 背景
    // ------------------------------------------------------------------

    public void renderBackdrop(GuiGraphics g) {
        if (hasBackgroundTexture) {
            drawLowPoly(g);
        } else {
            g.fill(0, 0, width, height, 0xFF000000);
        }

        float openZoom = (1.0f - intro()) * 0.06f;
        if (previousBackgroundId != null) {
            drawFullscreen(g, backgroundTexture(previousBackgroundId), previousBackgroundDrawAlpha(),
                    openZoom + (1.0f - backgroundFade) * 0.02f);
        }
        if (backgroundId != null) {
            drawFullscreen(g, backgroundTexture(backgroundId), backgroundFade,
                    openZoom + (1.0f - backgroundFade) * 0.05f);
        }

        // 底部压暗，顶部轻微（不遮盖倒计时文字）
        g.fillGradient(0, height - 90, width, height, withAlpha(0x000000, 0), withAlpha(0x000000, 165));
    }

    /** 检查是否有全屏背景或缩略图可用。 */
    public boolean hasBackground() {
        return hasBackgroundTexture;
    }

    /** 开屏：黑场淡出 + 上下开幕横条收起。 */
    public void renderOpenTransition(GuiGraphics g) {
        float intro = intro();
        if (intro >= 0.999f) {
            return;
        }
        int veil = (int) ((1.0f - intro) * 255.0f);
        g.fill(0, 0, width, height, withAlpha(VEIL, veil));

        int bar = (int) ((1.0f - intro) * (height / 2.0f));
        if (bar > 0) {
            g.fill(0, 0, width, bar, 0xFF000000 | VEIL);
            g.fill(0, height - bar, width, height, 0xFF000000 | VEIL);
            g.fill(0, bar, width, bar + 1, withAlpha(ACCENT_BRIGHT, veil));
            g.fill(0, height - bar - 1, width, height - bar, withAlpha(ACCENT_BRIGHT, veil));
        }
    }

    /** 旧图实际绘制的不透明度：有新图接替时保持不变，否则随淡入进度退场。 */
    private float previousBackgroundDrawAlpha() {
        if (previousBackgroundId == null) {
            return 0.0f;
        }
        return previousBackgroundAlpha * (backgroundId != null ? 1.0f : 1.0f - backgroundFade);
    }

    /** 当前背景图对屏幕的总覆盖率（0 = 只剩低多边形，1 = 完全被地图背景遮住）。 */
    private float backgroundCoverage() {
        float previous = previousBackgroundDrawAlpha();
        float current = backgroundId != null ? backgroundFade : 0.0f;
        return Mth.clamp(previous * (1.0f - current) + current, 0.0f, 1.0f);
    }

    /**
     * 按地图 id 自动解析全屏背景：优先 {@code maps/background/<id>.png}，
     * 退回缩略图 {@code maps/<id>.png}；都没有则返回 null（保留低多边形背景）。
     */
    private ResourceLocation backgroundTexture(String id) {
        // 注意：不能用 computeIfAbsent —— 它不缓存 null，会导致每帧重查资源包
        if (backgroundCache.containsKey(id)) {
            return backgroundCache.get(id);
        }
        ResourceLocation resolved = null;
        for (String pattern : new String[] { BACKGROUND_PATH, THUMBNAIL_PATH }) {
            ResourceLocation location = ResourceLocation.tryBuild(SRE.MOD_ID, String.format(pattern, id));
            if (textureExists(location)) {
                resolved = location;
                break;
            }
        }
        backgroundCache.put(id, resolved);
        return resolved;
    }

    /** 铺满全屏、以屏幕中心为原点轻微放大的背景图。 */
    private void drawFullscreen(GuiGraphics g, ResourceLocation texture, float alpha, float zoom) {
        if (texture == null || alpha <= 0.01f) {
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2.0f, height / 2.0f, 0.0f);
        pose.scale(1.0f + zoom, 1.0f + zoom, 1.0f);
        pose.translate(-width / 2.0f, -height / 2.0f, 0.0f);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Mth.clamp(alpha, 0.0f, 1.0f));
        g.blit(texture, 0, 0, 0.0f, 0.0f, width, height, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();

        pose.popPose();
    }

    // ------------------------------------------------------------------
    // 低多边形网格
    // ------------------------------------------------------------------

    private void buildMesh() {
        meshCols = Math.max(8, width / 88);
        meshRows = Math.max(5, height / 88);
        int vertices = (meshCols + 1) * (meshRows + 1);
        meshX = new float[vertices];
        meshY = new float[vertices];
        meshPhase = new float[vertices];
        meshTriColor = new int[meshCols * meshRows * 2];

        float cellW = (float) width / meshCols;
        float cellH = (float) height / meshRows;
        float jitter = 0.36f;

        for (int j = 0; j <= meshRows; j++) {
            for (int i = 0; i <= meshCols; i++) {
                int v = j * (meshCols + 1) + i;
                float x = i * cellW;
                float y = j * cellH;
                // 边缘顶点外扩且不抖动，避免屏幕边界露出空隙
                if (i == 0) {
                    x = -cellW * 0.5f;
                } else if (i == meshCols) {
                    x = width + cellW * 0.5f;
                } else {
                    x += (hash(i, j, 1) - 0.5f) * cellW * jitter * 2.0f;
                }
                if (j == 0) {
                    y = -cellH * 0.5f;
                } else if (j == meshRows) {
                    y = height + cellH * 0.5f;
                } else {
                    y += (hash(i, j, 2) - 0.5f) * cellH * jitter * 2.0f;
                }
                meshX[v] = x;
                meshY[v] = y;
                meshPhase[v] = hash(i, j, 3) * Mth.TWO_PI;
            }
        }

        for (int j = 0; j < meshRows; j++) {
            for (int i = 0; i < meshCols; i++) {
                for (int t = 0; t < 2; t++) {
                    int tri = (j * meshCols + i) * 2 + t;
                    // 垂直冷灰渐变 + 每片随机明暗，少量三角带暗金色调
                    float depth = (j + t * 0.5f) / meshRows;
                    int base = mix(0xFF272C35, 0xFF10131A, depth);
                    float shade = (hash(i, j, 10 + t) - 0.5f) * 0.26f;
                    int color = shade >= 0
                            ? mix(base, 0xFF3C424D, shade * 2.0f)
                            : mix(base, 0xFF080A0E, -shade * 2.0f);
                    if (hash(i, j, 20 + t) > 0.90f) {
                        color = mix(color, ACCENT, 0.30f);
                    }
                    meshTriColor[tri] = color;
                }
            }
        }
    }

    private void drawLowPoly(GuiGraphics g) {
        if (meshX == null) {
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = pose.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int j = 0; j < meshRows; j++) {
            for (int i = 0; i < meshCols; i++) {
                int v00 = j * (meshCols + 1) + i;
                int v10 = v00 + 1;
                int v01 = v00 + (meshCols + 1);
                int v11 = v01 + 1;
                int tri = (j * meshCols + i) * 2;
                emitTriangle(buffer, matrix, v00, v10, v11, meshTriColor[tri]);
                emitTriangle(buffer, matrix, v00, v11, v01, meshTriColor[tri + 1]);
            }
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
        pose.popPose();

        // 暗金环境辉光，呼应强调色
        drawSoftGlow(g, (int) (width * 0.24f + Math.sin(animTime * 0.35f) * 42.0f),
                (int) (height * 0.72f), 190, 0x6B4F14, 0.07f);
        drawSoftGlow(g, (int) (width * 0.80f), (int) (height * 0.22f + Math.cos(animTime * 0.3f) * 30.0f),
                170, 0x5A6472, 0.06f);
    }

    private void emitTriangle(BufferBuilder buffer, Matrix4f matrix, int a, int b, int c, int color) {
        vertex(buffer, matrix, a, color);
        vertex(buffer, matrix, b, color);
        vertex(buffer, matrix, c, color);
    }

    private void vertex(BufferBuilder buffer, Matrix4f matrix, int v, int color) {
        float wobble = (float) Math.sin(animTime * 0.55f + meshPhase[v]) * 3.0f;
        float wobbleX = (float) Math.cos(animTime * 0.42f + meshPhase[v]) * 2.2f;
        buffer.addVertex(matrix, meshX[v] + wobbleX, meshY[v] + wobble, 0.0f).setColor(color);
    }

    private void drawSoftGlow(GuiGraphics g, int cx, int cy, int radius, int rgb, float intensity) {
        for (int layer = 8; layer >= 1; layer--) {
            float ratio = layer / 8.0f;
            int a = (int) (255.0f * intensity * ratio * ratio);
            int r = (int) (radius * ratio);
            g.fill(cx - r, cy - r, cx + r, cy + r, withAlpha(rgb, a));
        }
    }
}
