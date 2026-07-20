package io.wifi.starrailexpress.client.gui.screen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.api.AreasSettings.MinecraftWeather;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapBackdropRenderer;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.network.MapIntroRequestPayload;
import io.wifi.starrailexpress.network.MapIntroSyncPayload;
import io.wifi.starrailexpress.network.VoteForMapPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.approachFactor;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.drawRectBorder;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.easeOutCubic;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.isInRect;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.mix;
import static io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.withAlpha;

/**
 * 新版地图投票界面 — 暗金主题。
 *
 * <p>
 * 布局（横轴）：
 * <ul>
 * <li>顶部中央：倒计时（无背景）</li>
 * <li>左侧上方：投票排行（名字/排名，无背景）</li>
 * <li>左侧：分类选项卡</li>
 * <li>左侧：竖直地图列表（序号+地图名），选中后右侧动画展开介绍面板（可滑动）</li>
 * <li>底部中央：地图名（大字暗金色）+ "已选择: XXX"（分行）</li>
 * </ul>
 */
public class MapVoteScreen extends Screen {
    private static final Gson GSON = new Gson();
    // ---- 配色（暗金主题） ----
    private static final int TEXT = 0xFFE4E8EE;
    private static final int TEXT_DIM = 0xFF8B939F;
    private static final int TEXT_FAINT = 0xFF5C636D;
    private static final int ACCENT = 0xFF8B6914;
    private static final int ACCENT_BRIGHT = 0xFFC9A84C;
    private static final int GOLD = 0xFFD4AF37;
    private static final int GOLD_DARK = 0xFF6B4F14;
    private static final int PANEL = 0x99141820;
    private static final int PANEL_DARK = 0xB30D1016;
    private static final int DIVIDER = 0x20FFFFFF;
    private static final int CARD_TOP = 0x59FFFFFF;
    private static final int CARD_BOTTOM = 0x2EDCE2EA;
    private static final int CARD_BORDER = 0xFFF2F5F8;
    private static final int CARD_TITLE = 0xFFFFFFFF;
    private static final int CARD_META = 0xFFCBD2DC;
    private static final int CARD_BODY = 0xFFEDF0F4;
    private static final int WARNING = 0xFFE06B65;

    // ---- 布局常量 ----
    private static final int PAD = 24;
    private static final int TABS_Y = 64;
    private static final int TAB_H = 20;
    private static final int CONTENT_TOP = 94;
    private static final int BOTTOM_AREA = 44;
    private static final int ROW_H = 28;
    private static final int ROW_GAP = 2;
    private static final int THUMB_W = 100;
    private static final int INTRO_PANEL_W = 340;
    private static final float LIST_WIDTH_SCALE = 0.6f;

    private final MapBackdropRenderer backdrop = new MapBackdropRenderer();

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

    /** 右侧介绍面板动画进度（0=收起，1=完全展开）。 */
    private float introPanelAnim;
    /** 右侧介绍面板滚动偏移。 */
    private float introScroll;
    private float introScrollTarget;
    /** 预构建的详细介绍文本行。 */
    private final List<FormattedCharSequence> introDetailLines = new ArrayList<>();

    // ---- MapIntroduceScreen 数据（由服务器推送） ----
    private final Map<String, MapIntroSyncPayload.VoteMap> voteMaps = new HashMap<>();
    private final Map<String, JsonObject> mapJsons = new HashMap<>();
    private final Set<String> bagMaps = new HashSet<>();
    private final Set<String> policeMaps = new HashSet<>();
    private final Set<String> underwaterMaps = new HashSet<>();
    private final Set<String> airMaps = new HashSet<>();
    private final Set<String> trapMaps = new HashSet<>();
    private final Set<String> horseMaps = new HashSet<>();
    private boolean introDataReceived;

    private int listX;
    private int listW;
    private int listBottom;

    public MapVoteScreen() {
        super(Component.translatable("gui.sre.map_vote.logo"));
    }

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
        introProgress = 0.0f;
        buildRows();
        buildTabs();
        applyFilter();
        backdrop.resize(width, height);
        // 请求地图介绍数据（服务器返回 MapIntroSyncPayload）
        if (!introDataReceived) {
            ClientPlayNetworking.send(new MapIntroRequestPayload());
        }
        rebuildIntroDetail();
    }

    /** 接收服务器推送的地图介绍完整数据。 */
    public void updateIntroFromPacket(MapIntroSyncPayload payload) {
        voteMaps.clear();
        mapJsons.clear();
        bagMaps.clear();
        policeMaps.clear();
        underwaterMaps.clear();
        airMaps.clear();
        trapMaps.clear();
        horseMaps.clear();
        bagMaps.addAll(payload.bagMaps());
        policeMaps.addAll(payload.policeMaps());
        underwaterMaps.addAll(payload.underwaterMaps());
        airMaps.addAll(payload.airMaps());
        trapMaps.addAll(payload.trapMaps());
        horseMaps.addAll(payload.horseMaps());
        for (MapIntroSyncPayload.VoteMap map : payload.voteMaps()) {
            voteMaps.put(map.id(), map);
        }
        for (MapIntroSyncPayload.MapJson map : payload.maps()) {
            try {
                mapJsons.put(map.id(), JsonParser.parseString(map.json()).getAsJsonObject());
            } catch (Exception ignored) {
            }
        }
        introDataReceived = true;
        rebuildIntroDetail();
    }

    private void buildRows() {
        allRows.clear();
        List<MapConfig.MapEntry> maps = MapConfig.getInstance().getMaps();
        if (maps == null)
            return;
        for (MapConfig.MapEntry entry : maps) {
            allRows.add(new MapRow(entry));
        }
    }

    private void buildTabs() {
        tabs.clear();
        tabs.add("");
        Set<String> modes = new LinkedHashSet<>();
        for (MapRow row : allRows) {
            if (row.entry.gameModes == null)
                continue;
            for (String mode : row.entry.gameModes) {
                if (mode != null && !mode.isBlank())
                    modes.add(mode);
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
        scrollTarget = 0f;
        scroll = 0f;
    }

    private void recalculateLayout() {
        listX = PAD;
        listW = (int) Mth.clamp(width * 0.52f * LIST_WIDTH_SCALE,
                240f * LIST_WIDTH_SCALE, 520f * LIST_WIDTH_SCALE);
        listBottom = height - BOTTOM_AREA;
    }

    /** 重新构建右侧介绍面板的文本行（MapIntroduceScreen 风格）。 */
    private void rebuildIntroDetail() {
        introDetailLines.clear();
        if (selectedRow == null)
            return;
        MapRow row = selectedRow;
        int wrapW = Math.max(80, INTRO_PANEL_W - 40);

        String id = row.entry.getId();
        String name = row.displayName();
        JsonObject json = mapJsons.get(id);
        MapIntroSyncPayload.VoteMap vm = voteMaps.get(id);

        // 名称 / ID
        addLine(Component.literal(name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), wrapW);
        addLine(Component.literal(id).withStyle(ChatFormatting.GRAY), wrapW);
        addBlank();

        // 投票配置
        if (vm != null) {
            addSection("map_intro.section.vote_config", wrapW);
            addLine("map_intro.vote.display_name", name, wrapW);
            Component min = vm.minCount() == -1
                    ? translate("map_intro.vote.no_min_count")
                    : translate("map_intro.vote.count_value", vm.minCount());
            addLine("map_intro.vote.min_count", min, wrapW);
            Component max = vm.maxCount() == -1
                    ? translate("map_intro.vote.no_max_count")
                    : translate("map_intro.vote.count_value", vm.maxCount());
            addLine("map_intro.vote.max_count", max, wrapW);
            addLine(vm.canSelect() ? "map_intro.vote.can_select.true" : "map_intro.vote.can_select.false", wrapW);
        } else {
            // 回退到 MapConfig 的基础信息
            addSection("map_intro.section.vote_config", wrapW);
            addLine("map_intro.vote.display_name", name, wrapW);
            addLine("map_intro.vote.min_count",
                    row.entry.minCount <= 0 ? translate("map_intro.vote.no_min_count")
                            : translate("map_intro.vote.count_value", row.entry.minCount),
                    wrapW);
            addLine("map_intro.vote.max_count",
                    row.entry.maxCount <= 0 ? translate("map_intro.vote.no_max_count")
                            : translate("map_intro.vote.count_value", row.entry.maxCount),
                    wrapW);
            addLine(row.entry.canSelect ? "map_intro.vote.can_select.true" : "map_intro.vote.can_select.false", wrapW);
        }
        addBlank();

        // 票数
        int votes = voteCount(id);
        int total = totalVotes();
        if (total > 0) {
            addLine(translate("map_intro.vote.vote_count",
                    votes, Mth.floor(votes * 100f / total)), wrapW);
            addBlank();
        }

        // 特殊角色标记（按 setSpecialMapRole 动态生成）
        addSection("map_intro.section.special_roles", wrapW);
        List<Component> specialLines = MapSpecialRoleLines.build(id, bagMaps, policeMaps,
                underwaterMaps, airMaps, trapMaps, horseMaps, mapJsons.get(id));
        if (specialLines.isEmpty()) {
            addLine(translate("map_intro.special.none").copy().withStyle(ChatFormatting.GRAY), wrapW);
        } else {
            for (Component specialLine : specialLines) {
                addLine(specialLine, wrapW);
            }
        }
        addBlank();

        // 描述
        String desc = row.entry.description;
        if (desc != null && !desc.isBlank()) {
            addSection("map_intro.section.info", wrapW);
            addLine(Component.literal(desc), wrapW);
            addBlank();
        }

        // JSON 属性（如果有 intro 数据）
        if (json != null) {
            addSection("map_intro.section.properties", wrapW);
            addLine("map_intro.property.room_count", intValue(json, "roomCount", 1), wrapW);
            if (json.has("areasSettings")) {
                // 新数据
                AreasSettings areasSettings = GSON.fromJson(json.get("settings"), AreasSettings.class);
                addLine(areasSettings.canSimpleSwim && areasSettings.canUnderWater && areasSettings.allowInDeepWater
                        && (areasSettings.canJump || areasSettings.canSwim)
                                ? "map_intro.property.can_swim.true"
                                : "map_intro.property.can_swim.false",
                        wrapW);
                addLine(areasSettings.canJump ? "map_intro.property.can_jump.true"
                        : "map_intro.property.can_jump.false", wrapW);
                if (areasSettings.snowEnabled)
                    addLine("map_intro.property.snow", wrapW);
                if (areasSettings.sandEnabled)
                    addLine("map_intro.property.sand", wrapW);
                if (areasSettings.enableOxygenDrowning)
                    addLine("map_intro.property.oxygen_drowning", wrapW);
                if (!areasSettings.fogEnabled)
                    addLine("map_intro.property.no_fog", wrapW);
                var weather = areasSettings.weather;
                if (!weather.equals(MinecraftWeather.clear))
                    addLine("map_intro.property.weather", weather.name(), wrapW);
            } else {
                // 旧数据，丢转转
                addLine(boolValue(json, "canSwim", false) ? "map_intro.property.can_swim.true"
                        : "map_intro.property.can_swim.false", wrapW);
                addLine(boolValue(json, "canJump", false) ? "map_intro.property.can_jump.true"
                        : "map_intro.property.can_jump.false", wrapW);
                if (boolValue(json, "snowEnabled", false))
                    addLine("map_intro.property.snow", wrapW);
                if (boolValue(json, "sandEnabled", false))
                    addLine("map_intro.property.sand", wrapW);
                if (boolValue(json, "enableOxygenDrowning", false))
                    addLine("map_intro.property.oxygen_drowning", wrapW);
                if (!boolValue(json, "fogEnabled", true))
                    addLine("map_intro.property.no_fog", wrapW);
                String weather = stringValue(json, "weather", "clear");
                if (!weather.equalsIgnoreCase("clear"))
                    addLine("map_intro.property.weather", weather, wrapW);
            }
            if (boolValue(json, "minigameQuestEnabled", false))
                addLine("map_intro.property.minigame_quest", wrapW);
        }

        introScroll = 0;
        introScrollTarget = 0;
    }

    // ---- intro 工具方法 ----

    private void addLine(Component text, int wrapW) {
        introDetailLines.addAll(font.split(text, wrapW));
    }

    private void addLine(String key, Component value, int wrapW) {
        addLine(translate(key, Component.literal("").append(value).getString()), wrapW);
    }

    private void addLine(String key, String value, int wrapW) {
        addLine(translate(key, value), wrapW);
    }

    private void addLine(String key, int value, int wrapW) {
        addLine(translate(key, String.valueOf(value)), wrapW);
    }

    private void addLine(String key, int wrapW) {
        addLine(translate(key), wrapW);
    }

    private void addSection(String key, int wrapW) {
        addLine(translate(key).copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), wrapW);
    }

    private void addBlank() {
        introDetailLines.add(FormattedCharSequence.EMPTY);
    }

    private boolean addIfContains(Set<String> set, String id, String key, int wrapW) {
        if (set.contains(id)) {
            addLine(key, wrapW);
            return true;
        }
        return false;
    }

    private static Component translate(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static boolean boolValue(JsonObject json, String key, boolean def) {
        return json.has(key) ? json.get(key).getAsBoolean() : def;
    }

    private static int intValue(JsonObject json, String key, int def) {
        return json.has(key) ? json.get(key).getAsInt() : def;
    }

    private static String stringValue(JsonObject json, String key, String def) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : def;
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

        super.render(g, mouseX, mouseY, partialTick);

        renderTimer(g);
        renderRanking(g);
        renderTabs(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);
        renderIntroPanel(g, mouseX, mouseY);
        renderBottom(g);
        backdrop.renderOpenTransition(g);
    }

    private void advanceAnimation() {
        float dt = backdrop.advance(targetBackgroundId());
        introProgress = backdrop.introProgress();
        animTime = backdrop.animTime();

        scroll = Mth.lerp(approachFactor(dt, 16f), scroll, scrollTarget);
        introScroll = Mth.lerp(approachFactor(dt, 14f), introScroll, introScrollTarget);

        float panelTarget = selectedRow != null ? 1f : 0f;
        introPanelAnim = MapUiGraphics.approach(introPanelAnim, panelTarget, dt, 8f);

        for (MapRow row : allRows) {
            boolean hover = row == hoveredRow && row.entry.canSelect;
            row.hoverAnim = MapUiGraphics.approach(row.hoverAnim, hover ? 1f : 0f, dt, 12f);
            row.selectAnim = MapUiGraphics.approach(row.selectAnim, row == selectedRow ? 1f : 0f, dt, 10f);
        }
    }

    private String targetBackgroundId() {
        MapRow row = selectedRow != null ? selectedRow : hoveredRow;
        return row == null ? null : row.entry.getId();
    }

    // ------------------------------------------------------------------
    // 倒计时（屏幕中央上部，无背景）
    // ------------------------------------------------------------------

    private void renderTimer(GuiGraphics g) {
        MapVotingComponent voting = votingComponent();
        if (voting == null || !voting.isVotingActive())
            return;

        int timeLeft = Math.max(0, voting.getVotingTimeLeft() / 20);
        int total = Math.max(1, voting.getTotalVotingTime() / 20);
        float progress = Mth.clamp(timeLeft / (float) total, 0f, 1f);

        Component text = Component.translatable("gui.sre.map_selector.voting_timer", timeLeft);
        int textColor = ACCENT_BRIGHT;
        float scale = 1.5f;

        if (timeLeft <= 10) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(animTime * 8f);
            textColor = WARNING;
            scale = 1.5f + 0.1f * (float) Math.sin(animTime * 8f);
        }

        PoseStack pose = g.pose();
        pose.pushPose();
        int tw = font.width(text);
        pose.translate(width / 2f - tw * scale / 2f, 10f, 0f);
        pose.scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, withAlpha(textColor, 245), false);
        pose.popPose();

        // 进度条（细线，居中）
        int barW = Math.min(200, width / 3);
        int barX = width / 2 - barW / 2;
        int barY = 10 + (int) (font.lineHeight * scale) + 4;
        g.fill(barX, barY, barX + barW, barY + 2, withAlpha(0x000000, 120));
        int filled = Math.max(1, (int) (barW * progress));
        g.fill(barX, barY, barX + filled, barY + 2, withAlpha(timeLeft <= 10 ? WARNING : ACCENT_BRIGHT, 220));
    }

    // ------------------------------------------------------------------
    // 投票排行（选项卡上方，仅名字/排名，无背景）
    // ------------------------------------------------------------------

    private void renderRanking(GuiGraphics g) {
        List<Map.Entry<String, Integer>> ranking = ranking();
        if (ranking.isEmpty())
            return;

        int maxShow = 8;
        int count = Math.min(maxShow, ranking.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0)
                sb.append("    ");
            Map.Entry<String, Integer> item = ranking.get(i);
            String name = displayNameOf(item.getKey());
            sb.append(name).append(" / ").append(item.getValue());
        }
        String text = sb.toString();
        if (text.isEmpty())
            return;

        int x = listX;
        int y = TABS_Y - font.lineHeight - 4;
        int rankColor = withAlpha(ACCENT_BRIGHT, 200);
        g.drawString(font, text, x, y, rankColor, false);
    }

    // ------------------------------------------------------------------
    // 选项卡
    // ------------------------------------------------------------------

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        hoveredTab = -1;
        if (tabs.size() <= 1)
            return;
        float reveal = easeOutCubic(Mth.clamp((introProgress - 0.12f) * 1.6f, 0f, 1f));
        int alpha = (int) (reveal * 255f);
        int x = listX;

        for (int i = 0; i < tabs.size(); i++) {
            Component label = tabLabel(i);
            int w = font.width(label) + 20;
            if (x + w > width - PAD)
                break;

            boolean active = i == activeTab;
            boolean hover = isInRect(mouseX, mouseY, x, TABS_Y, w, TAB_H);
            if (hover)
                hoveredTab = i;

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

    // ------------------------------------------------------------------
    // 地图列表
    // ------------------------------------------------------------------

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

            if (y + rowH > CONTENT_TOP && y < listBottom) {
                boolean hover = mouseInList && isInRect(mouseX, mouseY, listX, y, listW, rowH);
                if (hover && row.entry.canSelect)
                    hoveredRow = row;
                float entrance = easeOutCubic(Mth.clamp((introProgress - 0.16f - i * 0.035f) * 2.4f, 0f, 1f));
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
        int alpha = (int) (entrance * 255f);
        int slide = (int) ((1f - entrance) * 18f);
        x -= slide;

        float sel = easeOutCubic(row.selectAnim);
        float hov = easeOutCubic(row.hoverAnim) * (1f - sel);
        boolean disabled = !row.entry.canSelect;

        // 选中项：亮底衬
        if (sel > 0.02f) {
            g.enableScissor(x, Math.max(y, CONTENT_TOP), x + w, Math.min(y + h, listBottom));
            g.fillGradient(x, y, x + w, y + h,
                    withAlpha(mix(PANEL_DARK, ACCENT_BRIGHT, 0.25f), (int) (alpha * 0.85f * sel)),
                    withAlpha(mix(PANEL_DARK, ACCENT_BRIGHT, 0.10f), (int) (alpha * 0.85f * sel)));
            g.fill(x + 1, y, x + 3, y + h, withAlpha(ACCENT_BRIGHT, (int) (alpha * sel)));
            g.disableScissor();
        } else if (hov > 0.01f) {
            g.fillGradient(x, y, x + w, y + h,
                    withAlpha(PANEL, (int) (alpha * (0.45f + 0.20f * hov))),
                    withAlpha(PANEL_DARK, (int) (alpha * 0.50f)));
            g.fill(x, y, x + 2, y + h, withAlpha(ACCENT_BRIGHT, (int) (alpha * hov)));
        } else {
            g.fill(x, y, x + w, y + ROW_H, withAlpha(PANEL, (int) (alpha * 0.42f)));
        }

        int textAlpha = (int) (alpha * (disabled ? 0.45f : 1f));
        int indexColor = disabled ? TEXT_FAINT : mix(TEXT_FAINT, ACCENT_BRIGHT, hov);
        g.drawString(font, String.format("%02d", index + 1), x + 10, y + (ROW_H - font.lineHeight) / 2 + 1,
                withAlpha(indexColor, textAlpha), false);

        int nameColor = disabled ? TEXT_FAINT : mix(TEXT_DIM, TEXT, 0.55f + 0.45f * hov);
        g.drawString(font, clip(row.displayName(), w - 100), x + 38, y + (ROW_H - font.lineHeight) / 2 + 1,
                withAlpha(nameColor, textAlpha), false);

        int votes = voteCount(row.entry.getId());
        if (votes > 0) {
            Component voteText = Component.translatable("gui.sre.map_vote.votes", votes);
            g.drawString(font, voteText, x + w - 12 - font.width(voteText), y + (ROW_H - font.lineHeight) / 2 + 1,
                    withAlpha(GOLD, (int) (alpha * 0.9f)), false);
        }
    }

    // ------------------------------------------------------------------
    // 右侧介绍面板（选中时动画展开，可滚动）
    // ------------------------------------------------------------------

    private void renderIntroPanel(GuiGraphics g, int mouseX, int mouseY) {
        float panelAnim = easeOutCubic(Mth.clamp(introPanelAnim, 0f, 1f));
        if (panelAnim < 0.01f)
            return;

        int panelW = INTRO_PANEL_W;
        int panelH = listBottom - CONTENT_TOP;
        // 面板从右边缘滑入
        int targetX = width - PAD - panelW;
        int slideOffset = (int) ((1f - panelAnim) * (panelW + 40));
        int panelX = targetX + slideOffset;

        int panelY = CONTENT_TOP;

        // 半透明底衬
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH,
                withAlpha(PANEL, (int) (panelAnim * 240)),
                withAlpha(PANEL_DARK, (int) (panelAnim * 250)));
        drawRectBorder(g, panelX, panelY, panelW, panelH, 1,
                withAlpha(ACCENT_BRIGHT, (int) (panelAnim * 200)));

        if (introDetailLines.isEmpty())
            return;

        int pad = 10;
        int textX = panelX + pad;
        int textY = panelY + pad;
        int textW = panelW - pad * 2 - 4; // scrollbar space
        int textH = panelH - pad * 2;
        int lineH = font.lineHeight + 2;
        int visibleLines = textH / lineH;

        // 滚动限制
        int totalContentH = introDetailLines.size() * lineH;
        int maxScroll = Math.max(0, totalContentH - textH);
        introScrollTarget = Mth.clamp(introScrollTarget, 0, maxScroll);
        introScroll = Mth.clamp(introScroll, 0, maxScroll);

        g.enableScissor(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + panelH - 2);
        int startLine = (int) ((int) introScroll / lineH);
        int offsetY = textY - ((int) introScroll % lineH);

        for (int i = 0; i <= visibleLines + 1; i++) {
            int idx = startLine + i;
            if (idx >= 0 && idx < introDetailLines.size()) {
                g.drawString(font, introDetailLines.get(idx), textX, offsetY + i * lineH,
                        withAlpha(TEXT, (int) (panelAnim * 240)), false);
            }
        }
        g.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            int barX = panelX + panelW - 5;
            g.fill(barX, panelY + 2, barX + 3, panelY + panelH - 2, withAlpha(0x000000, 90));
            int thumbH = Math.max(20, textH * textH / totalContentH);
            int thumbY = panelY + 2 + (textH - thumbH) * (int) introScroll / maxScroll;
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH,
                    withAlpha(ACCENT_BRIGHT, (int) (panelAnim * 180)));
        }
    }

    // ------------------------------------------------------------------
    // 底部地图名 + 已选择（分行，暗金色大字）
    // ------------------------------------------------------------------

    private void renderBottom(GuiGraphics g) {
        // 淡入
        float reveal = easeOutCubic(Mth.clamp(introProgress * 1.3f, 0f, 1f));
        int alpha = (int) (reveal * 255f);

        int centerX = width / 2;
        int bottomY = height - BOTTOM_AREA + 4;

        // 地图名（大字，暗金色）
        String mapName = selectedRow != null
                ? selectedRow.displayName()
                : (hoveredRow != null
                        ? hoveredRow.displayName()
                        : "");
        if (!mapName.isEmpty()) {
            PoseStack pose = g.pose();
            pose.pushPose();
            float nameScale = 1.3f;
            int tw = (int) (font.width(mapName) * nameScale);
            pose.translate(centerX - tw / 2f, bottomY + 1f, 0f);
            pose.scale(nameScale, nameScale, 1f);
            g.drawString(font, Component.literal(mapName).withStyle(ChatFormatting.BOLD), 0, 0,
                    withAlpha(ACCENT_BRIGHT, alpha), false);
            pose.popPose();
        }

        // 已选择行
        if (selectedRow != null) {
            Component selectedText = Component.translatable("gui.sre.map_selector.selected",
                    selectedRow.displayName());
            int sw = font.width(selectedText);
            g.drawString(font, selectedText, centerX - sw / 2, bottomY + (int) (font.lineHeight * 1.3f) + 4,
                    withAlpha(CARD_BORDER, (int) (alpha * 0.85f)), false);
        }
    }

    // ------------------------------------------------------------------
    // 滚动条
    // ------------------------------------------------------------------

    private void drawScrollbar(GuiGraphics g, int contentH) {
        int viewH = listBottom - CONTENT_TOP;
        if (contentH <= viewH)
            return;
        int trackX = listX + listW - 3;
        g.fill(trackX, CONTENT_TOP, trackX + 3, listBottom, withAlpha(0x000000, 90));
        int thumbH = Math.max(20, viewH * viewH / contentH);
        int maxScroll = contentH - viewH;
        int thumbY = CONTENT_TOP + (int) ((viewH - thumbH) * (scroll / maxScroll));
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, withAlpha(ACCENT_BRIGHT, 210));
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredTab >= 0 && hoveredTab != activeTab) {
            activeTab = hoveredTab;
            applyFilter();
            rebuildIntroDetail();
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
            rebuildIntroDetail();
            playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        // 检查是否在介绍面板区域内
        int panelW = INTRO_PANEL_W;
        int panelX = width - PAD - panelW;
        if (introPanelAnim > 0.05f && mouseX >= panelX && mouseX <= panelX + panelW
                && mouseY >= CONTENT_TOP && mouseY <= listBottom) {
            introScrollTarget = Mth.clamp(introScrollTarget - (float) deltaY * 20f, 0f, 999999f);
            return true;
        }
        // 左侧列表滚动
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            scrollTarget = Mth.clamp(scrollTarget - (float) deltaY * 28f, 0f, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 257 -> {
                confirm();
                return true;
            }
            case 256 -> {
                onClose();
                return true;
            }
            case 264 -> {
                moveSelection(1);
                return true;
            }
            case 265 -> {
                moveSelection(-1);
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void moveSelection(int direction) {
        if (visibleRows.isEmpty())
            return;
        int current = selectedRow == null ? (direction > 0 ? -1 : visibleRows.size())
                : visibleRows.indexOf(selectedRow);
        int target = Mth.clamp(current + direction, 0, visibleRows.size() - 1);
        if (target == current)
            return;
        selectedRow = visibleRows.get(target);
        ensureVisible(selectedRow);
        rebuildIntroDetail();
        playClick();
    }

    private void ensureVisible(MapRow row) {
        int y = CONTENT_TOP;
        for (MapRow candidate : visibleRows) {
            if (candidate == row)
                break;
            y += rowHeight(candidate) + ROW_GAP;
        }
        int rowTop = y - CONTENT_TOP;
        int rowBottom = rowTop + ROW_H;
        int viewH = listBottom - CONTENT_TOP;
        if (rowTop < scrollTarget)
            scrollTarget = rowTop;
        else if (rowBottom > scrollTarget + viewH)
            scrollTarget = rowBottom - viewH;
        scrollTarget = Mth.clamp(scrollTarget, 0f, maxScroll());
    }

    private void confirm() {
        if (selectedRow == null)
            return;
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1f);
        }
        submitVote(selectedRow);
        onClose();
    }

    private void submitVote(MapRow row) {
        MapVotingComponent voting = votingComponent();
        if (voting == null || !voting.isVotingActive() || minecraft == null || minecraft.player == null)
            return;
        if (!row.entry.canSelect)
            return;
        ClientPlayNetworking.send(new VoteForMapPayload(row.entry.getId()));
        minecraft.player.displayClientMessage(
                Component.translatable("gui.sre.map_selector.voted_for", row.displayName())
                        .withStyle(ChatFormatting.GREEN),
                false);
    }

    private void playClick() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1f + (float) Math.random() * 0.15f);
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
        float hov = easeOutCubic(row.hoverAnim) * (1f - sel);
        return ROW_H + (int) (8f * hov) + (int) (6f * sel);
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
        if (minecraft == null || minecraft.level == null)
            return null;
        return MapVotingComponent.KEY.get(minecraft.level);
    }

    private int voteCount(String mapId) {
        MapVotingComponent voting = votingComponent();
        return voting == null ? 0 : voting.getVoteCount(mapId);
    }

    private int totalVotes() {
        MapVotingComponent voting = votingComponent();
        if (voting == null)
            return 0;
        int sum = 0;
        for (int value : voting.getAllVotes().values())
            sum += value;
        return sum;
    }

    private List<Map.Entry<String, Integer>> ranking() {
        MapVotingComponent voting = votingComponent();
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        if (voting == null)
            return list;
        for (Map.Entry<String, Integer> entry : voting.getAllVotes().entrySet()) {
            if (entry.getValue() > 0)
                list.add(entry);
        }
        list.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return list;
    }

    private String displayNameOf(String mapId) {
        for (MapRow row : allRows) {
            if (row.entry.getId().equals(mapId))
                return row.displayName();
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

    private String clip(String text, int maxWidth) {
        return MapUiGraphics.clip(font, text, maxWidth);
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
    }
}
