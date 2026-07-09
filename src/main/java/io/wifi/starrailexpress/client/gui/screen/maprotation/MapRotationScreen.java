package io.wifi.starrailexpress.client.gui.screen.maprotation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapBackdropRenderer;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import io.wifi.starrailexpress.network.MapIntroRequestPayload;
import io.wifi.starrailexpress.network.MapIntroSyncPayload;
import io.wifi.starrailexpress.network.MapRotationSyncPayload;
import io.wifi.starrailexpress.network.MapRotationTogglePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.accentFromId;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.approachFactor;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.drawRectBorder;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.easeOutCubic;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.isInRect;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.mix;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.withAlpha;

/**
 * 地图轮换界面。与 {@code MapVoteScreen} 共用背景与配色。
 *
 * <p>
 * 玩家侧：只列出当前启用（{@code canSelect}）的地图，右侧是糅合了地图介绍屏数据的详情页。
 * 管理员侧：列出全部地图（停用的置灰并标 OFF），并在分类选项卡左侧多一枚纯色半透明按钮，
 * 用来启用 / 停用当前选中的地图。切换通过 {@link MapRotationTogglePayload} 上行，
 * 服务端校验权限、写盘后广播 {@link MapRotationSyncPayload}。
 */
public class MapRotationScreen extends Screen {

    // ---- 配色（与 MapVoteScreen 一致：冷灰基调 + 暗红强调） ----
    private static final int TEXT = 0xFFE4E8EE;
    private static final int TEXT_DIM = 0xFF8B939F;
    private static final int TEXT_FAINT = 0xFF5C636D;
    private static final int ACCENT = 0xFF9E2B2B;
    private static final int ACCENT_BRIGHT = 0xFFC0392B;
    private static final int GREEN = 0xFF72C17B;
    private static final int PANEL = 0x99141820;
    private static final int PANEL_DARK = 0xB30D1016;
    private static final int DIVIDER = 0x20FFFFFF;
    private static final int SELECT_BORDER = 0xFFF2F5F8;

    // ---- 布局 ----
    private static final int PAD = 24;
    private static final int TABS_Y = 58;
    private static final int TAB_H = 20;
    private static final int CONTENT_TOP = 88;
    private static final int BOTTOM_BAR = 34;
    private static final int ROW_H = 30;
    private static final int ROW_GAP = 2;
    private static final int ROW_HOVER_EXTRA = 40;
    private static final int DETAIL_PAD = 12;
    private static final int DETAIL_MIN_W = 180;
    private static final int LINE_H = 11;

    private final MapBackdropRenderer backdrop = new MapBackdropRenderer();

    private final List<MapRow> allRows = new ArrayList<>();
    private final List<MapRow> visibleRows = new ArrayList<>();
    private final List<String> tabs = new ArrayList<>();

    private MapIntroDetail.SpecialSets specialSets;
    private boolean loaded;
    private boolean requested;
    private boolean admin;

    private MapRow selectedRow;
    private MapRow hoveredRow;
    private int activeTab;
    private int hoveredTab = -1;
    private boolean hoveredToggle;

    private float introProgress;
    private float listScroll;
    private float listScrollTarget;
    private float detailScroll;
    private float detailScrollTarget;
    private float toggleHoverAnim;

    // 详情文本缓存：只在选中项 / 面板宽度 / 启用状态变化时重建
    private List<FormattedCharSequence> detailLines = List.of();
    private String detailCacheKey = "";

    private int listX;
    private int listW;
    private int listBottom;
    private int detailX;
    private int detailW;
    private boolean showDetail;
    private int toggleX;
    private int toggleW;

    public MapRotationScreen() {
        super(Component.translatable("gui.sre.map_rotation.logo"));
    }

    // ------------------------------------------------------------------
    // 初始化 / 数据
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        introProgress = 0.0f;
        admin = minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
        backdrop.resize(width, height);
        // resize 会再次调用 init()，只在第一次请求数据
        if (!requested) {
            requested = true;
            ClientPlayNetworking.send(new MapIntroRequestPayload());
        }
    }

    /** 收到地图介绍数据（复用地图介绍屏的同一个 S2C 包）。 */
    public void updateFromPacket(MapIntroSyncPayload payload) {
        String previousId = selectedRow == null ? null : selectedRow.id;

        Map<String, JsonObject> jsonById = new HashMap<>();
        for (MapIntroSyncPayload.MapJson map : payload.maps()) {
            try {
                jsonById.put(map.id(), JsonParser.parseString(map.json()).getAsJsonObject());
            } catch (Exception ignored) {
                // 单张地图 JSON 损坏不该拖垮整个列表
            }
        }

        specialSets = new MapIntroDetail.SpecialSets(
                new HashSet<>(payload.bagMaps()),
                new HashSet<>(payload.policeMaps()),
                new HashSet<>(payload.underwaterMaps()),
                new HashSet<>(payload.airMaps()),
                new HashSet<>(payload.trapMaps()));

        allRows.clear();
        for (MapIntroSyncPayload.VoteMap voteMap : payload.voteMaps()) {
            if (voteMap.id() == null || voteMap.id().isBlank()) {
                continue;
            }
            allRows.add(new MapRow(voteMap, jsonById.get(voteMap.id())));
        }
        loaded = true;

        buildTabs();
        applyFilter();
        resetListScroll();
        restoreSelection(previousId);
        invalidateDetail();
    }

    /** 管理员切换后服务端广播的启用状态。 */
    public void applyRotationSync(MapRotationSyncPayload payload) {
        Map<String, Boolean> states = new HashMap<>();
        for (MapRotationSyncPayload.Entry entry : payload.entries()) {
            states.put(entry.id(), entry.enabled());
        }
        for (MapRow row : allRows) {
            Boolean enabled = states.get(row.id);
            if (enabled != null) {
                row.enabled = enabled;
            }
        }
        String previousId = selectedRow == null ? null : selectedRow.id;
        applyFilter();
        restoreSelection(previousId);
        invalidateDetail();
    }

    /** 列表重建后按 id 找回原来选中的行（玩家侧地图被停用时会消失）。 */
    private void restoreSelection(String previousId) {
        selectedRow = null;
        if (previousId != null) {
            for (MapRow row : visibleRows) {
                if (row.id.equals(previousId)) {
                    selectedRow = row;
                    break;
                }
            }
        }
        if (selectedRow == null && !visibleRows.isEmpty()) {
            selectedRow = visibleRows.getFirst();
        }
    }

    private void buildTabs() {
        String previous = tabs.isEmpty() ? "" : tabs.get(Mth.clamp(activeTab, 0, tabs.size() - 1));
        tabs.clear();
        tabs.add("");
        Set<String> modes = new LinkedHashSet<>();
        for (MapRow row : allRows) {
            for (String mode : row.voteMap.gameModes()) {
                if (mode != null && !mode.isBlank()) {
                    modes.add(mode);
                }
            }
        }
        tabs.addAll(modes);
        activeTab = Math.max(0, tabs.indexOf(previous));
    }

    private void applyFilter() {
        visibleRows.clear();
        String mode = tabs.isEmpty() ? "" : tabs.get(Mth.clamp(activeTab, 0, tabs.size() - 1));
        for (MapRow row : allRows) {
            // 玩家只看得到启用中的地图；管理员看得到全部，以便重新启用
            if (!admin && !row.enabled) {
                continue;
            }
            if (mode.isEmpty() || row.supportsMode(mode)) {
                visibleRows.add(row);
            }
        }
        // 不在这里重置滚动：管理员每次切换都会广播 applyRotationSync，
        // 否则所有玩家的列表都会被拽回顶部。滚动位置由 render 里按新内容高度 clamp。
    }

    private void resetListScroll() {
        listScroll = 0.0f;
        listScrollTarget = 0.0f;
    }

    private void invalidateDetail() {
        detailCacheKey = "";
        detailScroll = 0.0f;
        detailScrollTarget = 0.0f;
    }

    private void recalculateLayout() {
        listX = PAD;
        listW = (int) Mth.clamp(width * 0.312f, 144.0f, 312.0f);
        // 极端 GUI 缩放下（如 640x480 @ 4x → 160x120）height - BOTTOM_BAR 会低于 CONTENT_TOP，
        // 直接用会得到反向的 scissor 矩形；宁可让内容压到底部提示上，也不能出现非法矩形
        listBottom = Math.max(CONTENT_TOP + 24, height - BOTTOM_BAR);
        detailX = listX + listW + 16;
        detailW = width - PAD - detailX;
        showDetail = detailW >= DETAIL_MIN_W && listBottom - CONTENT_TOP >= 100;
        if (!showDetail) {
            listW = width - PAD * 2;
            detailW = 0;
        }
    }

    /** 详情面板顶部缩略图的高度，保证正文区至少还剩 40px。 */
    private int heroHeight() {
        int h = listBottom - CONTENT_TOP;
        return Math.min((int) Mth.clamp(h / 3.0f, 60.0f, 110.0f), h - 40);
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        backdrop.renderBackdrop(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        advanceAnimation();
        recalculateLayout();
        // 地图被停用后列表会变短，把滚动位置收回合法范围
        listScrollTarget = Mth.clamp(listScrollTarget, 0.0f, maxListScroll());

        super.render(g, mouseX, mouseY, partialTick);

        renderHeader(g);
        renderToggleButton(g, mouseX, mouseY);
        renderTabs(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);
        if (showDetail) {
            renderDetail(g, mouseX, mouseY);
        }
        renderBottomHint(g);
        backdrop.renderOpenTransition(g);
    }

    private void advanceAnimation() {
        float dt = backdrop.advance(selectedRow == null ? null : selectedRow.id);
        introProgress = backdrop.introProgress();

        listScroll = Mth.lerp(approachFactor(dt, 16.0f), listScroll, listScrollTarget);
        detailScroll = Mth.lerp(approachFactor(dt, 16.0f), detailScroll, detailScrollTarget);
        toggleHoverAnim = MapUiGraphics.approach(toggleHoverAnim, hoveredToggle ? 1.0f : 0.0f, dt, 12.0f);

        for (MapRow row : allRows) {
            row.hoverAnim = MapUiGraphics.approach(row.hoverAnim, row == hoveredRow ? 1.0f : 0.0f, dt, 12.0f);
            row.selectAnim = MapUiGraphics.approach(row.selectAnim, row == selectedRow ? 1.0f : 0.0f, dt, 10.0f);
        }
    }

    private void renderHeader(GuiGraphics g) {
        float reveal = easeOutCubic(Mth.clamp(introProgress * 1.4f, 0.0f, 1.0f));
        int alpha = (int) (reveal * 255.0f);
        int slide = (int) ((1.0f - reveal) * 14.0f);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(PAD - slide, 16.0f, 0.0f);
        pose.scale(1.7f, 1.7f, 1.0f);
        g.drawString(font, Component.translatable("gui.sre.map_rotation.logo").withStyle(ChatFormatting.BOLD),
                0, 0, withAlpha(TEXT, alpha), false);
        pose.popPose();

        int logoH = (int) (font.lineHeight * 1.7f);
        g.fill(PAD - 8 - slide, 16, PAD - 5 - slide, 16 + logoH, withAlpha(ACCENT_BRIGHT, alpha));

        Component subtitle = Component.translatable(
                admin ? "gui.sre.map_rotation.subtitle_admin" : "gui.sre.map_rotation.subtitle");
        g.drawString(font, subtitle, PAD - slide, 16 + logoH + 4,
                withAlpha(TEXT_DIM, (int) (alpha * 0.9f)), false);

        Component counter = Component.translatable("gui.sre.map_rotation.enabled_count", enabledCount(),
                allRows.size());
        g.drawString(font, counter, PAD - slide, 16 + logoH + 16, withAlpha(TEXT_FAINT, (int) (alpha * 0.9f)), false);
    }

    /** 分类选项卡左侧的纯色半透明按钮：启用 / 停用当前选中地图。仅管理员可见。 */
    private void renderToggleButton(GuiGraphics g, int mouseX, int mouseY) {
        hoveredToggle = false;
        toggleW = 0;
        if (!admin) {
            return;
        }
        float reveal = easeOutCubic(Mth.clamp((introProgress - 0.12f) * 1.6f, 0.0f, 1.0f));
        int alpha = (int) (reveal * 255.0f);

        boolean actionable = selectedRow != null;
        Component label = Component.translatable(!actionable
                ? "gui.sre.map_rotation.button.none"
                : selectedRow.enabled ? "gui.sre.map_rotation.button.disable" : "gui.sre.map_rotation.button.enable");

        toggleX = PAD;
        toggleW = font.width(label) + 22;
        hoveredToggle = actionable && isInRect(mouseX, mouseY, toggleX, TABS_Y, toggleW, TAB_H);

        int base;
        if (!actionable) {
            base = PANEL_DARK;
        } else if (selectedRow.enabled) {
            base = ACCENT;
        } else {
            base = mix(0xFF13301B, GREEN, 0.55f);
        }
        // 纯色半透明：hover 时只提高不透明度，不换色
        int fillAlpha = (int) (alpha * (actionable ? (0.72f + 0.20f * toggleHoverAnim) : 0.45f));
        g.fill(toggleX, TABS_Y, toggleX + toggleW, TABS_Y + TAB_H, withAlpha(base, fillAlpha));
        drawRectBorder(g, toggleX, TABS_Y, toggleW, TAB_H, 1,
                withAlpha(actionable ? (selectedRow.enabled ? ACCENT_BRIGHT : GREEN) : TEXT_FAINT,
                        (int) (alpha * (0.55f + 0.35f * toggleHoverAnim))));

        int textColor = actionable ? TEXT : TEXT_FAINT;
        g.drawString(font, label, toggleX + 11, TABS_Y + (TAB_H - font.lineHeight) / 2 + 1,
                withAlpha(textColor, alpha), false);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        hoveredTab = -1;
        if (tabs.size() <= 1) {
            return;
        }
        float reveal = easeOutCubic(Mth.clamp((introProgress - 0.12f) * 1.6f, 0.0f, 1.0f));
        int alpha = (int) (reveal * 255.0f);
        int x = PAD + (toggleW > 0 ? toggleW + 8 : 0);
        int limit = width - PAD;

        for (int i = 0; i < tabs.size(); i++) {
            Component label = tabLabel(i);
            int w = font.width(label) + 20;
            if (x + w > limit) {
                break;
            }
            boolean active = i == activeTab;
            boolean hover = isInRect(mouseX, mouseY, x, TABS_Y, w, TAB_H);
            if (hover) {
                hoveredTab = i;
            }

            g.fill(x, TABS_Y, x + w, TABS_Y + TAB_H, active
                    ? withAlpha(mix(PANEL_DARK, ACCENT, 0.55f), (int) (alpha * 0.95f))
                    : withAlpha(PANEL, (int) (alpha * (hover ? 0.9f : 0.6f))));
            if (active) {
                g.fill(x, TABS_Y + TAB_H - 2, x + w, TABS_Y + TAB_H, withAlpha(ACCENT_BRIGHT, alpha));
            } else if (hover) {
                g.fill(x, TABS_Y + TAB_H - 1, x + w, TABS_Y + TAB_H, withAlpha(TEXT_DIM, (int) (alpha * 0.6f)));
            }
            g.drawString(font, label, x + 10, TABS_Y + (TAB_H - font.lineHeight) / 2 + 1,
                    withAlpha(active || hover ? TEXT : TEXT_DIM, alpha), false);
            x += w + 4;
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        hoveredRow = null;
        if (!loaded) {
            g.drawString(font, Component.translatable("gui.sre.map_rotation.loading"),
                    listX, CONTENT_TOP + 8, TEXT_DIM, false);
            return;
        }
        if (visibleRows.isEmpty()) {
            g.drawString(font, Component.translatable("gui.sre.map_rotation.empty"),
                    listX, CONTENT_TOP + 8, TEXT_DIM, false);
            return;
        }

        boolean mouseInList = isInRect(mouseX, mouseY, listX, CONTENT_TOP, listW, listBottom - CONTENT_TOP);
        g.enableScissor(listX, CONTENT_TOP, listX + listW, listBottom);

        int y = CONTENT_TOP - (int) listScroll;
        for (int i = 0; i < visibleRows.size(); i++) {
            MapRow row = visibleRows.get(i);
            int rowH = rowHeight(row);

            // 严格裁剪：行完全出界时按行开 scissor 会得到反向矩形
            if (y + rowH > CONTENT_TOP && y < listBottom) {
                if (mouseInList && isInRect(mouseX, mouseY, listX, y, listW, rowH)) {
                    hoveredRow = row;
                }
                float entrance = easeOutCubic(Mth.clamp((introProgress - 0.16f - i * 0.03f) * 2.4f, 0.0f, 1.0f));
                drawRow(g, row, i, listX, y, listW, rowH, entrance);
            }

            y += rowH + ROW_GAP;
            if (i < visibleRows.size() - 1 && y - ROW_GAP > CONTENT_TOP && y < listBottom) {
                g.fill(listX + 6, y - 1, listX + listW - 6, y, DIVIDER);
            }
        }
        g.disableScissor();

        int contentH = y - ROW_GAP - (CONTENT_TOP - (int) listScroll);
        drawScrollbar(g, listX + listW - 3, CONTENT_TOP, listBottom, contentH, listScroll);
    }

    private void drawRow(GuiGraphics g, MapRow row, int index, int x, int y, int w, int h, float entrance) {
        int alpha = (int) (entrance * 255.0f);
        x -= (int) ((1.0f - entrance) * 18.0f);

        float hov = easeOutCubic(row.hoverAnim);
        float sel = easeOutCubic(row.selectAnim);
        boolean off = !row.enabled;

        if (hov > 0.01f) {
            g.enableScissor(x, Math.max(y, CONTENT_TOP), x + w, Math.min(y + h, listBottom));
            int zoom = (int) (hov * 10.0f);
            MapUiGraphics.drawThumbnail(g, font, row.id, x - zoom, y - zoom, w + zoom * 2, h + zoom * 2,
                    hov * 0.85f * entrance, row.accent);
            g.fillGradient(x, y, x + w, y + h,
                    withAlpha(0x0B0E14, (int) (alpha * (0.30f + 0.30f * (1.0f - hov)))),
                    withAlpha(0x0B0E14, (int) (alpha * 0.82f)));
            g.disableScissor();
        } else {
            g.fill(x, y, x + w, y + ROW_H, withAlpha(PANEL, (int) (alpha * (0.42f + 0.25f * sel))));
        }

        if (sel > 0.01f) {
            g.fill(x, y, x + 2, y + h, withAlpha(SELECT_BORDER, (int) (alpha * sel)));
        } else if (hov > 0.01f) {
            g.fill(x, y, x + 2, y + h, withAlpha(ACCENT_BRIGHT, (int) (alpha * hov)));
        }

        int dim = off ? (int) (alpha * 0.5f) : alpha;
        g.drawString(font, String.format("%02d", index + 1), x + 10, y + 11,
                withAlpha(off ? TEXT_FAINT : mix(TEXT_FAINT, ACCENT_BRIGHT, Math.max(hov, sel)), dim), false);

        int pillW = 26;
        int nameW = w - 48 - pillW;
        g.drawString(font, MapUiGraphics.clip(font, row.name.getString(), nameW), x + 38, y + 11,
                withAlpha(off ? TEXT_FAINT : mix(TEXT_DIM, TEXT, 0.55f + 0.45f * Math.max(hov, sel)), dim), false);

        // ON / OFF 状态标
        Component pill = Component.translatable(row.enabled
                ? "gui.sre.map_rotation.state.on"
                : "gui.sre.map_rotation.state.off");
        int pillX = x + w - 10 - font.width(pill);
        g.drawString(font, pill, pillX, y + 11, withAlpha(row.enabled ? GREEN : ACCENT_BRIGHT, dim), false);

        if (hov > 0.01f) {
            int descAlpha = (int) (alpha * Mth.clamp((hov - 0.25f) / 0.75f, 0.0f, 1.0f));
            if (descAlpha > 4) {
                g.drawString(font, MapUiGraphics.clip(font, row.subtitle().getString(), w - 48), x + 38, y + 28,
                        withAlpha(TEXT_DIM, descAlpha), false);
            }
        }
    }

    private void renderDetail(GuiGraphics g, int mouseX, int mouseY) {
        int top = CONTENT_TOP;
        int h = listBottom - top;

        g.fillGradient(detailX, top, detailX + detailW, top + h, PANEL, PANEL_DARK);
        drawRectBorder(g, detailX, top, detailW, h, 1, withAlpha(mix(PANEL_DARK, ACCENT, 0.5f), 220));
        g.fill(detailX + 1, top + 1, detailX + detailW - 1, top + 2, 0x22FFE8C0);

        if (selectedRow == null) {
            g.drawString(font, Component.translatable("gui.sre.map_rotation.detail_placeholder"),
                    detailX + DETAIL_PAD, top + DETAIL_PAD, TEXT_FAINT, false);
            return;
        }

        // 顶部缩略图 + 名称 + 状态
        int heroH = heroHeight();
        g.enableScissor(detailX + 1, top + 2, detailX + detailW - 1, top + heroH);
        MapUiGraphics.drawThumbnail(g, font, selectedRow.id, detailX + 1, top + 2, detailW - 2, heroH - 2,
                1.0f, selectedRow.accent);
        g.fillGradient(detailX + 1, top + heroH - 34, detailX + detailW - 1, top + heroH,
                withAlpha(0x0B0E14, 0), withAlpha(0x0B0E14, 230));
        g.disableScissor();

        g.drawString(font, MapUiGraphics.clip(font, selectedRow.name.getString(), detailW - 24 - 30),
                detailX + DETAIL_PAD, top + heroH - 14,
                selectedRow.enabled ? 0xFFFFFFFF : withAlpha(TEXT_DIM, 255), false);
        Component pill = Component.translatable(selectedRow.enabled
                ? "gui.sre.map_rotation.state.on"
                : "gui.sre.map_rotation.state.off");
        g.drawString(font, pill, detailX + detailW - DETAIL_PAD - font.width(pill), top + heroH - 14,
                selectedRow.enabled ? GREEN : ACCENT_BRIGHT, false);

        // 正文
        int bodyTop = top + heroH + 6;
        int bodyBottom = top + h - 6;
        int wrapW = detailW - DETAIL_PAD * 2 - 6;
        rebuildDetailIfNeeded(wrapW);

        g.enableScissor(detailX + 1, bodyTop, detailX + detailW - 1, bodyBottom);
        int y = bodyTop - (int) detailScroll;
        for (FormattedCharSequence line : detailLines) {
            if (y + LINE_H > bodyTop - LINE_H && y < bodyBottom) {
                g.drawString(font, line, detailX + DETAIL_PAD, y, TEXT, false);
            }
            y += LINE_H;
        }
        g.disableScissor();

        int contentH = detailLines.size() * LINE_H;
        drawScrollbar(g, detailX + detailW - 4, bodyTop, bodyBottom, contentH, detailScroll);
    }

    private void rebuildDetailIfNeeded(int wrapW) {
        String key = selectedRow.id + "|" + wrapW + "|" + selectedRow.enabled;
        if (key.equals(detailCacheKey)) {
            return;
        }
        detailCacheKey = key;
        detailLines = MapIntroDetail.build(font, wrapW, selectedRow.id, selectedRow.name,
                selectedRow.json, selectedRow.currentVoteMap(), specialSets);
        detailScrollTarget = Mth.clamp(detailScrollTarget, 0.0f, maxDetailScroll());
    }

    private void renderBottomHint(GuiGraphics g) {
        Component hint = Component.translatable(admin
                ? "gui.sre.map_rotation.hint_admin"
                : "gui.sre.map_rotation.hint");
        g.drawString(font, hint, PAD, height - 20, withAlpha(TEXT_FAINT, 220), false);
    }

    private void drawScrollbar(GuiGraphics g, int trackX, int top, int bottom, int contentH, float scroll) {
        int viewH = bottom - top;
        if (contentH <= viewH || viewH <= 0) {
            return;
        }
        g.fill(trackX, top, trackX + 3, bottom, withAlpha(0x000000, 90));
        int thumbH = Math.max(20, viewH * viewH / contentH);
        int maxScroll = contentH - viewH;
        int thumbY = top + (int) ((viewH - thumbH) * (scroll / maxScroll));
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, withAlpha(ACCENT_BRIGHT, 210));
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (hoveredToggle && selectedRow != null) {
            submitToggle(selectedRow);
            playClick();
            return true;
        }
        if (hoveredTab >= 0 && hoveredTab != activeTab) {
            activeTab = hoveredTab;
            String previousId = selectedRow == null ? null : selectedRow.id;
            applyFilter();
            resetListScroll();
            restoreSelection(previousId);
            invalidateDetail();
            playClick();
            return true;
        }
        if (hoveredRow != null && hoveredRow != selectedRow) {
            selectedRow = hoveredRow;
            invalidateDetail();
            playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (showDetail && isInRect(mouseX, mouseY, detailX, CONTENT_TOP, detailW, listBottom - CONTENT_TOP)) {
            int max = maxDetailScroll();
            if (max <= 0) {
                return false;
            }
            detailScrollTarget = Mth.clamp(detailScrollTarget - (float) deltaY * 22.0f, 0.0f, max);
            return true;
        }
        int max = maxListScroll();
        if (max <= 0) {
            return false;
        }
        listScrollTarget = Mth.clamp(listScrollTarget - (float) deltaY * 28.0f, 0.0f, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
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
            case 257 -> { // Enter：管理员快捷切换
                if (admin && selectedRow != null) {
                    submitToggle(selectedRow);
                    playClick();
                }
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
        int current = selectedRow == null ? -1 : visibleRows.indexOf(selectedRow);
        int target = Mth.clamp(current + direction, 0, visibleRows.size() - 1);
        if (target == current) {
            return;
        }
        selectedRow = visibleRows.get(target);
        invalidateDetail();
        ensureVisible(selectedRow);
        playClick();
    }

    private void ensureVisible(MapRow row) {
        int y = 0;
        for (MapRow candidate : visibleRows) {
            if (candidate == row) {
                break;
            }
            y += rowHeight(candidate) + ROW_GAP;
        }
        int viewH = listBottom - CONTENT_TOP;
        if (y < listScrollTarget) {
            listScrollTarget = y;
        } else if (y + ROW_H + ROW_HOVER_EXTRA > listScrollTarget + viewH) {
            listScrollTarget = y + ROW_H + ROW_HOVER_EXTRA - viewH;
        }
        listScrollTarget = Mth.clamp(listScrollTarget, 0.0f, maxListScroll());
    }

    private void submitToggle(MapRow row) {
        if (!admin || minecraft == null || minecraft.player == null) {
            return;
        }
        // 不做本地乐观更新：等服务端校验权限、写盘后广播回来再改 UI
        ClientPlayNetworking.send(new MapRotationTogglePayload(row.id, !row.enabled));
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
    // 计算
    // ------------------------------------------------------------------

    private int rowHeight(MapRow row) {
        return ROW_H + (int) (ROW_HOVER_EXTRA * easeOutCubic(row.hoverAnim));
    }

    private int maxListScroll() {
        int contentH = 0;
        for (MapRow row : visibleRows) {
            contentH += rowHeight(row) + ROW_GAP;
        }
        contentH = Math.max(0, contentH - ROW_GAP);
        return Math.max(0, contentH - (listBottom - CONTENT_TOP));
    }

    private int maxDetailScroll() {
        if (!showDetail) {
            return 0;
        }
        int viewH = (listBottom - 6) - (CONTENT_TOP + heroHeight() + 6);
        return Math.max(0, detailLines.size() * LINE_H - viewH);
    }

    private int enabledCount() {
        int count = 0;
        for (MapRow row : allRows) {
            if (row.enabled) {
                count++;
            }
        }
        return count;
    }

    private Component tabLabel(int index) {
        String mode = tabs.get(index);
        return mode.isEmpty()
                ? Component.translatable("gui.sre.map_rotation.tab_all")
                : MapIntroDetail.gameModeName(mode);
    }

    // ------------------------------------------------------------------

    private static final class MapRow {
        private final String id;
        private final MapIntroSyncPayload.VoteMap voteMap;
        private final JsonObject json;
        private final Component name;
        private final int accent;
        private boolean enabled;
        private float hoverAnim;
        private float selectAnim;

        private MapRow(MapIntroSyncPayload.VoteMap voteMap, JsonObject json) {
            this.id = voteMap.id();
            this.voteMap = voteMap;
            this.json = json;
            this.name = MapIntroDetail.mapDisplayName(voteMap.id(), voteMap);
            this.accent = accentFromId(voteMap.id());
            this.enabled = voteMap.canSelect();
        }

        /**
         * 详情页要用当前的启用状态，而不是包里那份不可变的 canSelect ——
         * 管理员切换后只更新了 {@link #enabled}，否则"可被选择"那一行会一直显示旧值。
         */
        private MapIntroSyncPayload.VoteMap currentVoteMap() {
            if (voteMap.canSelect() == enabled) {
                return voteMap;
            }
            return new MapIntroSyncPayload.VoteMap(voteMap.id(), voteMap.displayName(), voteMap.minCount(),
                    voteMap.maxCount(), enabled, voteMap.gameModes());
        }

        /** 空的 gameModes 表示支持所有模式，与 {@code MapConfig.MapEntry.isSupportedGameMode} 一致。 */
        private boolean supportsMode(String mode) {
            List<String> modes = voteMap.gameModes();
            if (modes == null || modes.isEmpty() || modes.getFirst().isBlank()) {
                return true;
            }
            return modes.contains(mode);
        }

        /** 悬停时展开的副标题：游戏模式 + 人数区间。 */
        private Component subtitle() {
            Component modes = MapIntroDetail.gameModesText(voteMap.gameModes());
            if (voteMap.minCount() > 0 && voteMap.maxCount() > 0) {
                return Component.translatable("gui.sre.map_rotation.row_subtitle", modes,
                        Component.translatable("gui.sre.map_rotation.capacity", voteMap.minCount(),
                                voteMap.maxCount()));
            }
            if (voteMap.maxCount() > 0) {
                return Component.translatable("gui.sre.map_rotation.row_subtitle", modes,
                        Component.translatable("gui.sre.map_rotation.capacity_max", voteMap.maxCount()));
            }
            return modes;
        }
    }
}
