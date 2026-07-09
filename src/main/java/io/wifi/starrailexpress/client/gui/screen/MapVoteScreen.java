package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.network.VoteForMapPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 新版地图投票界面。
 *
 * <p>
 * 布局：程序化生成的 3D 低多边形背景 + 透明叠加层，主轴横向。
 * 左上角 Logo/副标题 + 横向分类选项卡（按游戏模式过滤），右上角投票排行榜；
 * 左侧为竖直排列的地图列表（序号 + 章节名），悬停时行内背景切换为缩略图并放大、
 * 在下方展开描述；选中项在列表内就地展开为半透明金色的横向详情卡片
 * （左缩略图 + 右侧标题/元信息/剧情文案/票数条）。
 *
 * <p>
 * 配色遵循 {@code docs/ui_style.md}：冷灰基调 + 暗红强调色 + 金色选中卡片；
 * 面板半透明以透出背景，悬停/选中/禁用三态齐全，所有过渡使用指数插值。
 * 旧版 {@link MapSelectorScreen} 通过 {@code SREClientConfig.useLegacyMapSelector} 保留。
 */
public class MapVoteScreen extends Screen {

    // ---- 配色（冷灰调 + 暗红强调 + 金色选中卡） ----
    private static final int TEXT = 0xFFE4E8EE;
    private static final int TEXT_DIM = 0xFF8B939F;
    private static final int TEXT_FAINT = 0xFF5C636D;
    private static final int ACCENT = 0xFF9E2B2B;
    private static final int ACCENT_BRIGHT = 0xFFC0392B;
    private static final int GOLD = 0xFFD4AF37;
    private static final int PANEL = 0x99141820;
    private static final int PANEL_DARK = 0xB30D1016;
    private static final int DIVIDER = 0x20FFFFFF;
    // 选中卡片：半透明白色（冷灰调下的高光），不再使用金色
    private static final int CARD_TOP = 0x59FFFFFF;
    private static final int CARD_BOTTOM = 0x2EDCE2EA;
    private static final int CARD_BORDER = 0xFFF2F5F8;
    private static final int CARD_TITLE = 0xFFFFFFFF;
    private static final int CARD_META = 0xFFCBD2DC;
    private static final int CARD_BODY = 0xFFEDF0F4;
    private static final int WARNING = 0xFFE06B65;

    // ---- 布局 ----
    private static final int PAD = 24;
    private static final int TABS_Y = 58;
    private static final int TAB_H = 20;
    private static final int CONTENT_TOP = 88;
    private static final int BOTTOM_BAR = 34;
    private static final int ROW_H = 32;
    private static final int ROW_GAP = 2;
    private static final int ROW_HOVER_EXTRA = 44;
    private static final int ROW_SELECT_EXTRA = 52;
    private static final int THUMB_W = 100;
    private static final int LEADERBOARD_ROWS = 5;
    /** 左侧列表整体缩小 40%。 */
    private static final float LIST_WIDTH_SCALE = 0.6f;

    /** 每张地图的全屏背景图，按 id 自动查找。 */
    private static final String BACKGROUND_PATH = "textures/gui/maps/background/%s.png";
    /** 没有专用全屏背景时退回到缩略图。 */
    private static final String THUMBNAIL_PATH = "textures/gui/maps/%s.png";

    private final List<MapRow> allRows = new ArrayList<>();
    private final List<MapRow> visibleRows = new ArrayList<>();
    private final List<String> tabs = new ArrayList<>();

    private MapRow selectedRow;
    private MapRow hoveredRow;
    private int activeTab;
    private int hoveredTab = -1;

    private float scroll;
    private float scrollTarget;
    private float introProgress;
    private float animTime;
    private long lastFrameNanos;

    private int listX;
    private int listW;
    private int listBottom;
    private int boardX;
    private int boardW;
    private boolean showBoard;

    // 全屏背景：按地图 id 自动解析并缓存，切换地图时交叉淡入淡出
    private final Map<String, ResourceLocation> backgroundCache = new HashMap<>();
    private String backgroundId;
    private String previousBackgroundId;
    /** 新背景的淡入进度：0 = 完全是旧背景，1 = 完全是新背景。 */
    private float backgroundFade = 1.0f;
    /** 切换发生时旧背景的实际不透明度，避免中途换图时旧图突然跳到全亮。 */
    private float previousBackgroundAlpha;

    // 低多边形背景网格（init 时按屏幕尺寸生成一次）
    private int meshCols;
    private int meshRows;
    private float[] meshX;
    private float[] meshY;
    private float[] meshPhase;
    private int[] meshTriColor;

    public MapVoteScreen() {
        super(Component.translatable("gui.sre.map_vote.logo"));
    }

    /**
     * 按客户端配置选择新版 / 旧版地图投票界面。所有打开投票界面的位置都应走这里。
     */
    public static Screen create() {
        return SREClientConfig.instance().useLegacyMapSelector
                ? new MapSelectorScreen()
                : new MapVoteScreen();
    }

    // ------------------------------------------------------------------
    // 初始化
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        lastFrameNanos = System.nanoTime();
        introProgress = 0.0f;
        buildRows();
        buildTabs();
        applyFilter();
        buildMesh();
    }

    private void buildRows() {
        allRows.clear();
        List<MapConfig.MapEntry> maps = MapConfig.getInstance().getMaps();
        if (maps == null) {
            return;
        }
        for (MapConfig.MapEntry entry : maps) {
            allRows.add(new MapRow(entry));
        }
    }

    private void buildTabs() {
        tabs.clear();
        tabs.add("");
        Set<String> modes = new LinkedHashSet<>();
        for (MapRow row : allRows) {
            if (row.entry.gameModes == null) {
                continue;
            }
            for (String mode : row.entry.gameModes) {
                if (mode != null && !mode.isBlank()) {
                    modes.add(mode);
                }
            }
        }
        tabs.addAll(modes);
        activeTab = Mth.clamp(activeTab, 0, tabs.size() - 1);
    }

    private void applyFilter() {
        visibleRows.clear();
        String mode = tabs.get(activeTab);
        for (MapRow row : allRows) {
            if (mode.isEmpty() || row.entry.isSupportedGameMode(mode)) {
                visibleRows.add(row);
            }
        }
        if (SREClientConfig.instance().autoSortVotes) {
            visibleRows.sort(Comparator.comparingInt((MapRow row) -> voteCount(row.entry.getId())).reversed());
        }
        if (selectedRow != null && !visibleRows.contains(selectedRow)) {
            selectedRow = null;
        }
        scrollTarget = 0.0f;
        scroll = 0.0f;
    }

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
                    // 垂直冷灰渐变 + 每片随机明暗，少量三角带暗红色调
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

    private void recalculateLayout() {
        listX = PAD;
        // 左侧列表比初版窄 40%，让背景（低多边形 / 全景）透出更多
        listW = (int) Mth.clamp(width * 0.52f * LIST_WIDTH_SCALE,
                240.0f * LIST_WIDTH_SCALE, 520.0f * LIST_WIDTH_SCALE);
        listBottom = height - BOTTOM_BAR;
        boardW = (int) Mth.clamp(width * 0.30f, 170.0f, 260.0f);
        boardX = width - PAD - boardW;
        showBoard = boardX >= listX + listW + 16;
        if (!showBoard) {
            listW = width - PAD * 2;
        }
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawLowPolyBackground(g);

        // 地图全屏背景：旧图淡出、新图淡入，两张同时在场时交叉溶解
        float intro = introEase();
        float openZoom = (1.0f - intro) * 0.06f;
        if (previousBackgroundId != null) {
            drawFullscreenBackground(g, backgroundTexture(previousBackgroundId), previousBackgroundDrawAlpha(),
                    openZoom + (1.0f - backgroundFade) * 0.02f);
        }
        if (backgroundId != null) {
            drawFullscreenBackground(g, backgroundTexture(backgroundId),
                    backgroundFade, openZoom + (1.0f - backgroundFade) * 0.05f);
        }

        // 顶部/底部压暗，保证叠加层文字可读，同时让背景透出来
        g.fillGradient(0, 0, width, 110, withAlpha(0x000000, 170), withAlpha(0x000000, 0));
        g.fillGradient(0, height - 90, width, height, withAlpha(0x000000, 0), withAlpha(0x000000, 165));
    }

    /** 铺满全屏、以屏幕中心为原点轻微放大的背景图。 */
    private void drawFullscreenBackground(GuiGraphics g, ResourceLocation texture, float alpha, float zoom) {
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

    /** 开屏动画进度（缓出）。 */
    private float introEase() {
        return easeOutCubic(Mth.clamp(introProgress * 1.15f, 0.0f, 1.0f));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        advanceAnimation();
        recalculateLayout();

        super.render(g, mouseX, mouseY, partialTick);

        renderHeader(g);
        renderTabs(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);
        if (showBoard) {
            renderLeaderboard(g);
        }
        renderTimer(g);
        renderBottomHint(g);
        renderOpenTransition(g);
    }

    /** 开屏：黑场淡出 + 上下开幕横条收起。 */
    private void renderOpenTransition(GuiGraphics g) {
        float intro = introEase();
        if (intro >= 0.999f) {
            return;
        }
        int veil = (int) ((1.0f - intro) * 255.0f);
        g.fill(0, 0, width, height, withAlpha(0x05070B, veil));

        int bar = (int) ((1.0f - intro) * (height / 2.0f));
        if (bar > 0) {
            g.fill(0, 0, width, bar, 0xFF05070B);
            g.fill(0, height - bar, width, height, 0xFF05070B);
            g.fill(0, bar, width, bar + 1, withAlpha(ACCENT_BRIGHT, veil));
            g.fill(0, height - bar - 1, width, height - bar, withAlpha(ACCENT_BRIGHT, veil));
        }
    }

    private void advanceAnimation() {
        long now = System.nanoTime();
        float dt = Mth.clamp((now - lastFrameNanos) / 1.0E9f, 0.0f, 0.05f);
        lastFrameNanos = now;
        animTime += dt;

        introProgress = approach(introProgress, 1.0f, dt, 6.0f);
        scroll = Mth.lerp(approachFactor(dt, 16.0f), scroll, scrollTarget);

        for (MapRow row : allRows) {
            boolean hover = row == hoveredRow && row.entry.canSelect;
            row.hoverAnim = approach(row.hoverAnim, hover ? 1.0f : 0.0f, dt, 12.0f);
            row.selectAnim = approach(row.selectAnim, row == selectedRow ? 1.0f : 0.0f, dt, 10.0f);
        }

        // 切换目标地图时交叉淡入：旧背景留在 previousBackgroundId 上淡出
        String target = targetBackgroundId();
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

    /** 当前"目标地图"= 选中项，其次是悬停项；没有可用背景图则返回 null。 */
    private String targetBackgroundId() {
        MapRow row = selectedRow != null ? selectedRow : hoveredRow;
        if (row == null) {
            return null;
        }
        String id = row.entry.getId();
        return backgroundTexture(id) == null ? null : id;
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
            if (location != null && textureExists(location)) {
                resolved = location;
                break;
            }
        }
        backgroundCache.put(id, resolved);
        return resolved;
    }

    private void renderHeader(GuiGraphics g) {
        float reveal = easeOutCubic(Mth.clamp(introProgress * 1.4f, 0.0f, 1.0f));
        int alpha = (int) (reveal * 255.0f);
        int slide = (int) ((1.0f - reveal) * 14.0f);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(PAD - slide, 16.0f, 0.0f);
        pose.scale(1.7f, 1.7f, 1.0f);
        Component logo = Component.translatable("gui.sre.map_vote.logo").withStyle(ChatFormatting.BOLD);
        g.drawString(font, logo, 0, 0, withAlpha(TEXT, alpha), false);
        pose.popPose();

        // Logo 左侧暗红竖条
        int logoH = (int) (font.lineHeight * 1.7f);
        g.fill(PAD - 8 - slide, 16, PAD - 5 - slide, 16 + logoH, withAlpha(ACCENT_BRIGHT, alpha));

        g.drawString(font, Component.translatable("gui.sre.map_vote.subtitle"),
                PAD - slide, 16 + logoH + 4, withAlpha(TEXT_DIM, (int) (alpha * 0.9f)), false);

        MapVotingComponent voting = votingComponent();
        if (voting != null && !voting.getPresetGameMode().isEmpty()) {
            Component mode = Component.translatable("gui.sre.map_selector.preset_mode", gameModeName(voting.getPresetGameMode()));
            g.drawString(font, mode, PAD - slide, 16 + logoH + 16, withAlpha(TEXT_FAINT, (int) (alpha * 0.9f)), false);
        }
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        hoveredTab = -1;
        if (tabs.size() <= 1) {
            return;
        }
        float reveal = easeOutCubic(Mth.clamp((introProgress - 0.12f) * 1.6f, 0.0f, 1.0f));
        int alpha = (int) (reveal * 255.0f);
        int x = PAD;

        for (int i = 0; i < tabs.size(); i++) {
            Component label = tabLabel(i);
            int w = font.width(label) + 20;
            if (x + w > (showBoard ? boardX - 12 : width - PAD)) {
                break;
            }
            boolean active = i == activeTab;
            boolean hover = isInRect(mouseX, mouseY, x, TABS_Y, w, TAB_H);
            if (hover) {
                hoveredTab = i;
            }

            int bg = active
                    ? withAlpha(mix(PANEL_DARK, ACCENT, 0.55f), (int) (alpha * 0.95f))
                    : withAlpha(PANEL, (int) (alpha * (hover ? 0.9f : 0.6f)));
            g.fill(x, TABS_Y, x + w, TABS_Y + TAB_H, bg);
            if (active) {
                g.fill(x, TABS_Y + TAB_H - 2, x + w, TABS_Y + TAB_H, withAlpha(ACCENT_BRIGHT, alpha));
            } else if (hover) {
                g.fill(x, TABS_Y + TAB_H - 1, x + w, TABS_Y + TAB_H, withAlpha(TEXT_DIM, (int) (alpha * 0.6f)));
            }

            int textColor = active ? TEXT : (hover ? TEXT : TEXT_DIM);
            g.drawString(font, label, x + 10, TABS_Y + (TAB_H - font.lineHeight) / 2 + 1,
                    withAlpha(textColor, alpha), false);
            x += w + 4;
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        hoveredRow = null;
        if (visibleRows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.sre.map_vote.empty"),
                    listX, CONTENT_TOP + 8, TEXT_DIM, false);
            return;
        }

        boolean mouseInList = isInRect(mouseX, mouseY, listX, CONTENT_TOP, listW, listBottom - CONTENT_TOP);
        g.enableScissor(listX, CONTENT_TOP, listX + listW, listBottom);

        int y = CONTENT_TOP - (int) scroll;
        for (int i = 0; i < visibleRows.size(); i++) {
            MapRow row = visibleRows.get(i);
            int rowH = rowHeight(row);

            // 严格裁剪：drawRow 内部按行开 scissor，行完全出界会得到反向矩形
            if (y + rowH > CONTENT_TOP && y < listBottom) {
                boolean hover = mouseInList && isInRect(mouseX, mouseY, listX, y, listW, rowH);
                if (hover && row.entry.canSelect) {
                    hoveredRow = row;
                }
                float entrance = easeOutCubic(Mth.clamp((introProgress - 0.16f - i * 0.035f) * 2.4f, 0.0f, 1.0f));
                drawRow(g, row, i, listX, y, listW, rowH, entrance);
            }

            y += rowH + ROW_GAP;
            if (i < visibleRows.size() - 1 && y - ROW_GAP > CONTENT_TOP && y < listBottom) {
                g.fill(listX + 6, y - 1, listX + listW - 6, y, DIVIDER);
            }
        }
        g.disableScissor();

        int contentH = y - ROW_GAP - (CONTENT_TOP - (int) scroll);
        drawScrollbar(g, contentH);
    }

    private void drawRow(GuiGraphics g, MapRow row, int index, int x, int y, int w, int h, float entrance) {
        int alpha = (int) (entrance * 255.0f);
        int slide = (int) ((1.0f - entrance) * 18.0f);
        x -= slide;

        float sel = easeOutCubic(row.selectAnim);
        float hov = easeOutCubic(row.hoverAnim) * (1.0f - sel);
        boolean disabled = !row.entry.canSelect;

        if (sel > 0.02f) {
            drawSelectedCard(g, row, index, x, y, w, h, alpha, sel);
            return;
        }

        // 悬停：行背景换成缩略图并放大，描述在标题下方淡入
        if (hov > 0.01f) {
            g.enableScissor(x, Math.max(y, CONTENT_TOP), x + w, Math.min(y + h, listBottom));
            int zoom = (int) (hov * 10.0f);
            drawThumbnail(g, row.entry.getId(), x - zoom, y - zoom, w + zoom * 2, h + zoom * 2,
                    hov * 0.85f * entrance, row.accent());
            g.fillGradient(x, y, x + w, y + h,
                    withAlpha(0x0B0E14, (int) (alpha * (0.30f + 0.30f * (1.0f - hov)))),
                    withAlpha(0x0B0E14, (int) (alpha * 0.82f)));
            g.disableScissor();
            g.fill(x, y, x + 2, y + h, withAlpha(ACCENT_BRIGHT, (int) (alpha * hov)));
        } else {
            g.fill(x, y, x + w, y + ROW_H, withAlpha(PANEL, (int) (alpha * 0.42f)));
        }

        int textAlpha = (int) (alpha * (disabled ? 0.45f : 1.0f));
        int indexColor = disabled ? TEXT_FAINT : mix(TEXT_FAINT, ACCENT_BRIGHT, hov);
        g.drawString(font, String.format("%02d", index + 1), x + 10, y + 12, withAlpha(indexColor, textAlpha), false);

        int nameColor = disabled ? TEXT_FAINT : mix(TEXT_DIM, TEXT, 0.55f + 0.45f * hov);
        g.drawString(font, clip(row.displayName(), w - 100), x + 38, y + 12, withAlpha(nameColor, textAlpha), false);

        int votes = voteCount(row.entry.getId());
        if (votes > 0) {
            Component voteText = Component.translatable("gui.sre.map_vote.votes", votes);
            g.drawString(font, voteText, x + w - 12 - font.width(voteText), y + 12,
                    withAlpha(GOLD, (int) (alpha * 0.9f)), false);
        }

        if (hov > 0.01f) {
            int descAlpha = (int) (alpha * Mth.clamp((hov - 0.25f) / 0.75f, 0.0f, 1.0f));
            if (descAlpha > 4) {
                List<FormattedCharSequence> lines = font.split(
                        Component.literal(row.description()), w - 48);
                int ly = y + 30;
                for (int i = 0; i < Math.min(2, lines.size()); i++) {
                    g.drawString(font, lines.get(i), x + 38, ly, withAlpha(TEXT_DIM, descAlpha), false);
                    ly += font.lineHeight + 2;
                }
            }
        }
    }

    private void drawSelectedCard(GuiGraphics g, MapRow row, int index, int x, int y, int w, int h,
            int alpha, float sel) {
        int a = (int) (alpha * sel);

        // 轻微阴影
        g.fill(x + 2, y + 4, x + w + 2, y + h + 4, withAlpha(0x000000, (int) (a * 0.35f)));
        // 半透明白色直角卡片
        g.fillGradient(x, y, x + w, y + h,
                withAlpha(CARD_TOP, (int) (a * 0.85f)),
                withAlpha(CARD_BOTTOM, (int) (a * 0.85f)));
        drawRectBorder(g, x, y, w, h, 1, withAlpha(CARD_BORDER, (int) (a * 0.75f)));

        g.enableScissor(x, Math.max(y, CONTENT_TOP), x + w, Math.min(y + h, listBottom));

        int pad = 8;
        int thumbW = Math.min(THUMB_W, w / 3);
        int thumbH = Math.max(1, h - pad * 2);
        drawThumbnail(g, row.entry.getId(), x + pad, y + pad, thumbW, thumbH, sel, row.accent());
        drawRectBorder(g, x + pad, y + pad, thumbW, thumbH, 1, withAlpha(CARD_BORDER, (int) (a * 0.55f)));

        int tx = x + pad + thumbW + 10;
        int maxTextW = x + w - pad - tx;

        Component title = Component.literal(clip(row.displayName(), maxTextW))
                .withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, tx, y + pad + 1, withAlpha(CARD_TITLE, a), false);

        // 元信息行：序号 · 地图ID | 容量
        String meta = String.format("%02d", index + 1);
        if (SREClient.isPlayerSpectatingOrCreative()) {
            meta = meta + " · " + row.entry.getId();
        }
        g.drawString(font, meta, tx, y + pad + 14, withAlpha(CARD_META, (int) (a * 0.85f)), false);
        Component capacity = capacityText(row.entry);
        if (capacity != null) {
            int cw = font.width(capacity);
            g.drawString(font, capacity, x + w - pad - cw, y + pad + 14, withAlpha(CARD_META, (int) (a * 0.85f)),
                    false);
        }

        // 剧情文案
        List<FormattedCharSequence> lines = font.split(Component.literal(row.description()), maxTextW);
        int ly = y + pad + 28;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), tx, ly, withAlpha(CARD_BODY, (int) (a * 0.92f)), false);
            ly += font.lineHeight + 2;
        }

        // 票数条
        int votes = voteCount(row.entry.getId());
        int total = totalVotes();
        int barY = y + h - pad - 5;
        int barW = maxTextW;
        g.fill(tx, barY, tx + barW, barY + 4, withAlpha(0x000000, (int) (a * 0.35f)));
        if (votes > 0 && total > 0) {
            int filled = Math.max(2, barW * votes / total);
            g.fill(tx, barY, tx + filled, barY + 4, withAlpha(ACCENT_BRIGHT, a));
            Component voteText = Component.translatable("gui.sre.map_vote.votes_percent",
                    votes, Mth.floor(votes * 100.0f / total));
            g.drawString(font, voteText, x + w - pad - font.width(voteText), barY - 11,
                    withAlpha(CARD_TITLE, (int) (a * 0.9f)), false);
        }

        g.disableScissor();
    }

    private void renderLeaderboard(GuiGraphics g) {
        float reveal = easeOutCubic(Mth.clamp((introProgress - 0.2f) * 1.6f, 0.0f, 1.0f));
        int alpha = (int) (reveal * 255.0f);
        int slide = (int) ((1.0f - reveal) * 16.0f);
        int x = boardX + slide;

        List<Map.Entry<String, Integer>> ranking = ranking();
        int rows = Math.min(LEADERBOARD_ROWS, ranking.size());
        int bodyH = rows == 0 ? 20 : rows * 20;
        int h = 22 + bodyH + 6;

        g.fillGradient(x, TABS_Y, x + boardW, TABS_Y + h, withAlpha(PANEL, alpha), withAlpha(PANEL_DARK, alpha));
        drawRectBorder(g, x, TABS_Y, boardW, h, 1, withAlpha(mix(PANEL_DARK, ACCENT, 0.5f), alpha));
        g.fill(x + 1, TABS_Y + 1, x + boardW - 1, TABS_Y + 2, withAlpha(0xFFE8C0C0, (int) (alpha * 0.2f)));

        g.drawString(font, Component.translatable("gui.sre.map_vote.leaderboard").withStyle(ChatFormatting.BOLD),
                x + 8, TABS_Y + 7, withAlpha(TEXT, alpha), false);

        if (rows == 0) {
            g.drawString(font, Component.translatable("gui.sre.map_vote.no_votes"),
                    x + 8, TABS_Y + 28, withAlpha(TEXT_FAINT, alpha), false);
            return;
        }

        int total = totalVotes();
        int y = TABS_Y + 24;
        for (int i = 0; i < rows; i++) {
            Map.Entry<String, Integer> item = ranking.get(i);
            int rankColor = switch (i) {
                case 0 -> GOLD;
                case 1 -> 0xFFB8C0CC;
                case 2 -> 0xFFB07A46;
                default -> TEXT_FAINT;
            };
            g.drawString(font, String.valueOf(i + 1), x + 8, y + 5, withAlpha(rankColor, alpha), false);

            String name = displayNameOf(item.getKey());
            g.drawString(font, clip(name, boardW - 60), x + 20, y + 5, withAlpha(TEXT, (int) (alpha * 0.95f)), false);

            String count = String.valueOf(item.getValue());
            g.drawString(font, count, x + boardW - 8 - font.width(count), y + 5,
                    withAlpha(TEXT_DIM, alpha), false);

            int barW = boardW - 28;
            g.fill(x + 20, y + 15, x + 20 + barW, y + 17, withAlpha(0x000000, (int) (alpha * 0.4f)));
            if (total > 0) {
                int filled = Math.max(1, barW * item.getValue() / total);
                g.fill(x + 20, y + 15, x + 20 + filled, y + 17,
                        withAlpha(i == 0 ? GOLD : ACCENT_BRIGHT, alpha));
            }
            y += 20;
        }
    }

    private void renderTimer(GuiGraphics g) {
        MapVotingComponent voting = votingComponent();
        if (voting == null || !voting.isVotingActive()) {
            return;
        }
        int timeLeft = Math.max(0, voting.getVotingTimeLeft() / 20);
        int total = Math.max(1, voting.getTotalVotingTime() / 20);
        float progress = Mth.clamp(timeLeft / (float) total, 0.0f, 1.0f);

        Component text = Component.translatable("gui.sre.map_selector.voting_timer", timeLeft);
        int w = font.width(text) + 20;
        int h = 18;
        int x = width - PAD - w;
        // 有排行榜时挂在它下方，否则贴屏幕右上角
        int y = 16;
        if (showBoard) {
            int rows = Math.min(LEADERBOARD_ROWS, ranking().size());
            y = TABS_Y + 28 + (rows == 0 ? 20 : rows * 20) + 12;
        }

        float pulse = timeLeft <= 10 ? 0.65f + 0.35f * (float) Math.sin(animTime * 8.0f) : 1.0f;
        int frame = timeLeft <= 10 ? WARNING : ACCENT_BRIGHT;

        g.fill(x, y, x + w, y + h, withAlpha(PANEL_DARK, (int) (200 * pulse)));
        drawRectBorder(g, x, y, w, h, 1, withAlpha(frame, (int) (220 * pulse)));
        g.drawString(font, text, x + 10, y + (h - font.lineHeight) / 2 + 1, withAlpha(TEXT, 245), false);
        g.fill(x, y + h, x + w, y + h + 2, withAlpha(0x000000, 200));
        g.fill(x, y + h, x + Math.max(1, (int) (w * progress)), y + h + 2, withAlpha(frame, 245));
    }

    private void renderBottomHint(GuiGraphics g) {
        Component hint = Component.translatable("gui.sre.map_vote.hint");
        g.drawString(font, hint, PAD, height - 20, withAlpha(TEXT_FAINT, 220), false);
        if (selectedRow != null) {
            Component picked = Component.translatable("gui.sre.map_selector.selected", selectedRow.displayName());
            g.drawString(font, picked, width - PAD - font.width(picked), height - 20, withAlpha(CARD_BORDER, 230),
                    false);
        }
    }

    private void drawScrollbar(GuiGraphics g, int contentH) {
        int viewH = listBottom - CONTENT_TOP;
        if (contentH <= viewH) {
            return;
        }
        int trackX = listX + listW - 3;
        g.fill(trackX, CONTENT_TOP, trackX + 3, listBottom, withAlpha(0x000000, 90));
        int thumbH = Math.max(20, viewH * viewH / contentH);
        int maxScroll = contentH - viewH;
        int thumbY = CONTENT_TOP + (int) ((viewH - thumbH) * (scroll / maxScroll));
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, withAlpha(ACCENT_BRIGHT, 210));
    }

    // ------------------------------------------------------------------
    // 低多边形背景
    // ------------------------------------------------------------------

    private void drawLowPolyBackground(GuiGraphics g) {
        if (meshX == null) {
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = pose.last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

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

        // 暗红环境辉光，呼应强调色
        drawSoftGlow(g, (int) (width * 0.24f + Math.sin(animTime * 0.35f) * 42.0f),
                (int) (height * 0.72f), 190, 0x8C2020, 0.07f);
        drawSoftGlow(g, (int) (width * 0.80f), (int) (height * 0.22f + Math.cos(animTime * 0.3f) * 30.0f),
                170, 0x5A6472, 0.06f);
    }

    private void emitTriangle(com.mojang.blaze3d.vertex.BufferBuilder buffer, Matrix4f matrix,
            int a, int b, int c, int color) {
        vertex(buffer, matrix, a, color);
        vertex(buffer, matrix, b, color);
        vertex(buffer, matrix, c, color);
    }

    private void vertex(com.mojang.blaze3d.vertex.BufferBuilder buffer, Matrix4f matrix, int v, int color) {
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

    // ------------------------------------------------------------------
    // 缩略图
    // ------------------------------------------------------------------

    private void drawThumbnail(GuiGraphics g, String id, int x, int y, int w, int h, float alpha, int accent) {
        if (w <= 0 || h <= 0 || alpha <= 0.01f) {
            return;
        }
        ResourceLocation texture = ResourceLocation.tryBuild(SRE.MOD_ID, "textures/gui/maps/" + id + ".png");
        if (texture != null && textureExists(texture)) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Mth.clamp(alpha, 0.0f, 1.0f));
            g.blit(texture, x, y, 0.0f, 0.0f, w, h, w, h);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            return;
        }
        drawThumbnailPlaceholder(g, id, x, y, w, h, alpha, accent);
    }

    private void drawThumbnailPlaceholder(GuiGraphics g, String id, int x, int y, int w, int h,
            float alpha, int accent) {
        int a = (int) (255.0f * Mth.clamp(alpha, 0.0f, 1.0f));
        g.fillGradient(x, y, x + w, y + h,
                withAlpha(mix(0xFF1E232C, accent, 0.18f), (int) (a * 0.92f)),
                withAlpha(0xFF0C0F15, (int) (a * 0.95f)));

        // 低多边形味的斜向条纹，暗示"占位图"
        int step = Math.max(8, h / 4);
        for (int i = -h; i < w; i += step) {
            g.fill(x + Math.max(0, i), y, x + Math.min(w, i + 2), y + h, withAlpha(accent, (int) (a * 0.06f)));
        }

        String initial = id.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(id.charAt(0)));
        PoseStack pose = g.pose();
        pose.pushPose();
        float scale = Math.max(1.0f, Math.min(w, h) / 26.0f);
        pose.translate(x + w / 2.0f, y + h / 2.0f, 0.0f);
        pose.scale(scale, scale, 1.0f);
        g.drawString(font, initial, -font.width(initial) / 2, -font.lineHeight / 2,
                withAlpha(mix(TEXT_FAINT, accent, 0.4f), (int) (a * 0.75f)), false);
        pose.popPose();
    }

    private boolean textureExists(ResourceLocation location) {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredTab >= 0 && hoveredTab != activeTab) {
            activeTab = hoveredTab;
            applyFilter();
            playClick();
            return true;
        }
        if (button == 0 && hoveredRow != null) {
            if (selectedRow == hoveredRow) {
                selectedRow = null;
            } else {
                selectedRow = hoveredRow;
                submitVote(selectedRow);
            }
            playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return false;
        }
        scrollTarget = Mth.clamp(scrollTarget - (float) deltaY * 28.0f, 0.0f, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 257 -> { // Enter
                confirm();
                return true;
            }
            case 256 -> { // ESC
                onClose();
                return true;
            }
            case 264 -> { // Down
                moveSelection(1);
                return true;
            }
            case 265 -> { // Up
                moveSelection(-1);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void moveSelection(int direction) {
        if (visibleRows.isEmpty()) {
            return;
        }
        int current = selectedRow == null ? (direction > 0 ? -1 : visibleRows.size()) : visibleRows.indexOf(selectedRow);
        int target = Mth.clamp(current + direction, 0, visibleRows.size() - 1);
        if (target == current) {
            return;
        }
        selectedRow = visibleRows.get(target);
        ensureVisible(selectedRow);
        playClick();
    }

    private void ensureVisible(MapRow row) {
        int y = CONTENT_TOP;
        for (MapRow candidate : visibleRows) {
            if (candidate == row) {
                break;
            }
            y += rowHeight(candidate) + ROW_GAP;
        }
        int rowTop = y - CONTENT_TOP;
        int rowBottom = rowTop + ROW_H + ROW_SELECT_EXTRA;
        int viewH = listBottom - CONTENT_TOP;
        if (rowTop < scrollTarget) {
            scrollTarget = rowTop;
        } else if (rowBottom > scrollTarget + viewH) {
            scrollTarget = rowBottom - viewH;
        }
        scrollTarget = Mth.clamp(scrollTarget, 0.0f, maxScroll());
    }

    private void confirm() {
        if (selectedRow == null) {
            return;
        }
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
        }
        submitVote(selectedRow);
        onClose();
    }

    private void submitVote(MapRow row) {
        MapVotingComponent voting = votingComponent();
        if (voting == null || !voting.isVotingActive() || minecraft == null || minecraft.player == null) {
            return;
        }
        if (!row.entry.canSelect) {
            return;
        }
        ClientPlayNetworking.send(new VoteForMapPayload(row.entry.getId()));
        minecraft.player.displayClientMessage(
                Component.translatable("gui.sre.map_selector.voted_for", row.displayName())
                        .withStyle(ChatFormatting.GREEN),
                false);
    }

    private void playClick() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.0f + (float) Math.random() * 0.15f);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.2f, 0.8f);
        }
        super.onClose();
    }

    // ------------------------------------------------------------------
    // 数据
    // ------------------------------------------------------------------

    private int rowHeight(MapRow row) {
        float sel = easeOutCubic(row.selectAnim);
        float hov = easeOutCubic(row.hoverAnim) * (1.0f - sel);
        return ROW_H + (int) (ROW_HOVER_EXTRA * hov) + (int) (ROW_SELECT_EXTRA * sel);
    }

    private int maxScroll() {
        int contentH = 0;
        for (MapRow row : visibleRows) {
            contentH += rowHeight(row) + ROW_GAP;
        }
        contentH = Math.max(0, contentH - ROW_GAP);
        return Math.max(0, contentH - (listBottom - CONTENT_TOP));
    }

    private MapVotingComponent votingComponent() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return MapVotingComponent.KEY.get(minecraft.level);
    }

    private int voteCount(String mapId) {
        MapVotingComponent voting = votingComponent();
        return voting == null ? 0 : voting.getVoteCount(mapId);
    }

    private int totalVotes() {
        MapVotingComponent voting = votingComponent();
        if (voting == null) {
            return 0;
        }
        int sum = 0;
        for (int value : voting.getAllVotes().values()) {
            sum += value;
        }
        return sum;
    }

    private List<Map.Entry<String, Integer>> ranking() {
        MapVotingComponent voting = votingComponent();
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        if (voting == null) {
            return list;
        }
        for (Map.Entry<String, Integer> entry : voting.getAllVotes().entrySet()) {
            if (entry.getValue() > 0) {
                list.add(entry);
            }
        }
        list.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return list;
    }

    private String displayNameOf(String mapId) {
        for (MapRow row : allRows) {
            if (row.entry.getId().equals(mapId)) {
                return row.displayName();
            }
        }
        return mapId;
    }

    private Component tabLabel(int index) {
        String mode = tabs.get(index);
        return mode.isEmpty()
                ? Component.translatable("gui.sre.map_vote.tab_all")
                : gameModeName(mode);
    }

    private static Component gameModeName(String mode) {
        String path = mode.contains(":") ? mode.substring(mode.indexOf(':') + 1) : mode;
        return Component.translatableWithFallback("game_mode.noellesroles." + path,
                Component.translatableWithFallback("game_mode.starrailexpress." + path, path).getString());
    }

    private static Component capacityText(MapConfig.MapEntry entry) {
        if (entry.minCount > 0 && entry.maxCount > 0) {
            return Component.translatable("gui.sre.map_vote.capacity", entry.minCount, entry.maxCount);
        }
        if (entry.maxCount > 0) {
            return Component.translatable("gui.sre.map_vote.capacity_max", entry.maxCount);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 绘制工具
    // ------------------------------------------------------------------

    private static void drawRectBorder(GuiGraphics g, int x, int y, int w, int h, int thickness, int color) {
        for (int i = 0; i < thickness; i++) {
            g.fill(x + i, y + i, x + w - i, y + i + 1, color);
            g.fill(x + i, y + h - i - 1, x + w - i, y + h - i, color);
            g.fill(x + i, y + i, x + i + 1, y + h - i, color);
            g.fill(x + w - i - 1, y + i, x + w - i, y + h - i, color);
        }
    }

    private String clip(String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private static boolean isInRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static float easeOutCubic(float t) {
        float f = 1.0f - Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - f * f * f;
    }

    /** 与帧率无关的指数逼近。 */
    private static float approach(float current, float target, float dt, float speed) {
        return Mth.lerp(approachFactor(dt, speed), current, target);
    }

    private static float approachFactor(float dt, float speed) {
        return 1.0f - (float) Math.exp(-dt * speed);
    }

    private static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    /** 只混合 RGB，输出不透明色。 */
    private static int mix(int from, int to, float t) {
        float f = Mth.clamp(t, 0.0f, 1.0f);
        int r = Mth.floor(Mth.lerp(f, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Mth.floor(Mth.lerp(f, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Mth.floor(Mth.lerp(f, from & 0xFF, to & 0xFF));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float hash(int a, int b, int salt) {
        int h = a * 374761393 + b * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0f;
    }

    // ------------------------------------------------------------------

    private static final class MapRow {
        private final MapConfig.MapEntry entry;
        private float hoverAnim;
        private float selectAnim;

        private MapRow(MapConfig.MapEntry entry) {
            this.entry = entry;
        }

        private String displayName() {
            String name = entry.getDisplayName();
            return name == null ? entry.getId() : Component.translatable(name).getString();
        }

        private String description() {
            String desc = entry.getDescription();
            return desc == null ? "" : Component.translatable(desc).getString();
        }

        private int accent() {
            return 0xFF000000 | (entry.getColor() & 0x00FFFFFF);
        }
    }
}
