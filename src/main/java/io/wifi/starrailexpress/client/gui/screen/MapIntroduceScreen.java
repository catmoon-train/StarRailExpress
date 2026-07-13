package io.wifi.starrailexpress.client.gui.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.index.SREBlocks;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.network.MapIntroSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModSceneBlocks;

import java.util.*;

public class MapIntroduceScreen extends Screen {
    // ---------- 布局常量（参考 RoleIntroduceScreen 风格） ----------
    private static final int MAX_WIDTH = 700;
    private static final float LEFT_RATIO = 0.30f;
    private static final int PAD = 6;
    private static final int CARD_H = 42;
    private static final int CARD_GAP = 4;
    private static final int ICON_SIZE = 26;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int TOP_BAR_H = 18;
    private static final int CATEGORY_BAR_H = 16;
    private static final int BANNER_H = 26;

    // 面板内部间距
    private static final int PANEL_PAD = PAD;

    // 颜色方案
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int PANEL_OUTLINE = 0xFF8B6914;
    private static final int PANEL_BG_TOP = 0xD81A1008;
    private static final int PANEL_BG_BOTTOM = 0xD820140A;
    private static final int PANEL_HIGHLIGHT = 0x22FFE8C0;
    private static final int CARD_BORDER = 0xFF5A4530;
    private static final int CARD_BG_LEFT = 0xFF1A1008;
    private static final int CARD_BG_RIGHT = 0xFF120A04;
    private static final int CARD_HOVER_GLOW = 0x25FFFFFF;
    private static final int SCROLL_TRACK = 0xFF1A1008;
    private static final int SCROLL_THUMB = 0xFF8B6914;
    private static final int SCROLL_THUMB_HL = 0xFFC9A84C;

    // ---------- 数据 ----------
    private final List<MapEntry> maps = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<FormattedCharSequence> detailLines = new ArrayList<>();
    private final Map<String, MapIntroSyncPayload.VoteMap> voteMaps = new HashMap<>();
    private final Set<String> bagMaps = new HashSet<>();
    private final Set<String> policeMaps = new HashSet<>();
    private final Set<String> underwaterMaps = new HashSet<>();
    private final Set<String> airMaps = new HashSet<>();
    private final Set<String> trapMaps = new HashSet<>();
    private final Set<String> horseMaps = new HashSet<>();

    private static final List<TabInfo> TABS = List.of(
            new TabInfo(Tab.MAP_PROPERTIES, "map_intro.tab.map_properties", 0xFF5EB7D8),
            new TabInfo(Tab.SCENE_BLOCKS, "map_intro.tab.scene_blocks", 0xFF72C17B),
            new TabInfo(Tab.QUEST_BLOCKS, "map_intro.tab.quest_blocks", 0xFFE0AD5B),
            new TabInfo(Tab.MECHANICS, "map_intro.tab.mechanics", 0xFFB18AE6));

    // ---------- 界面状态 ----------
    private Screen parent;
    private EditBox search;
    private Tab currentTab = Tab.MAP_PROPERTIES;
    private int selectedCategoryIndex = 0;          // 与 TABS 索引一致
    private Entry selected;

    private int listScrollOffset = 0;
    private int maxListScroll = 0;

    private int detailScrollOffset = 0;
    private int maxDetailScroll = 0;

    // 布局变量（动态计算）
    private int usableWidth, leftW, rightW;
    private int panelX, panelY, panelH;
    private int leftX, rightX;
    private int topBarY, categoryBarY, listAreaY, listAreaH;
    private int rightContentY, rightContentH;

    // 类别标签动态宽度
    private final int[] tabX = new int[TABS.size()];
    private final int[] tabW = new int[TABS.size()];

    // ---------- 构造 ----------
    public MapIntroduceScreen(Screen parent) {
        super(Component.translatable("map_intro.title"));
        this.parent = parent;
    }

    public void updateFromPacket(MapIntroSyncPayload payload) {
        maps.clear();
        voteMaps.clear();
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
            if (map.id() != null && !map.id().isBlank()) {
                voteMaps.put(map.id(), map);
            }
        }
        for (MapIntroSyncPayload.MapJson map : payload.maps()) {
            try {
                JsonObject root = JsonParser.parseString(map.json()).getAsJsonObject();
                maps.add(new MapEntry(map.id(), root, voteMaps.get(map.id())));
            } catch (Exception ignored) {
                maps.add(new MapEntry(map.id(), new JsonObject(), voteMaps.get(map.id())));
            }
        }
        maps.sort(Comparator.comparing(m -> m.name.getString()));
        rebuildEntries();
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0);
        }
        rebuildDetail();
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        // 搜索框
        search = new EditBox(font, leftX + PANEL_PAD, topBarY, leftW - PANEL_PAD * 2, TOP_BAR_H,
                Component.translatable("map_intro.search"));
        search.setHint(Component.translatable("map_intro.search"));
        search.setMaxLength(64);
        search.setResponder(text -> {
            listScrollOffset = 0;
            rebuildEntries();
            if (selected != null && !entries.contains(selected)) {
                selected = entries.isEmpty() ? null : entries.get(0);
            }
            rebuildDetail();
        });
        addRenderableWidget(search);

        rebuildEntries();
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0);
        }
        rebuildDetail();
    }

    private void computeLayout() {
        usableWidth = Math.min(MAX_WIDTH, (int) (width * 0.9f));
        leftW = (int) (usableWidth * LEFT_RATIO);
        rightW = usableWidth - leftW;
        panelX = (width - usableWidth) / 2;
        panelY = (height - panelH) / 2;
        panelH = Math.min(360, Math.max(230, (int) (height * 0.78f)));
        panelY = (height - panelH) / 2;
        leftX = panelX;
        rightX = panelX + leftW;
        topBarY = panelY + PANEL_PAD;
        categoryBarY = topBarY + TOP_BAR_H + 2;
        listAreaY = categoryBarY + CATEGORY_BAR_H + 2;
        listAreaH = panelY + panelH - listAreaY - PANEL_PAD;
        rightContentY = panelY + BANNER_H + PANEL_PAD + 4;
        rightContentH = panelY + panelH - rightContentY - PANEL_PAD;
    }

    // ---------- 条目重建 ----------
    private void rebuildEntries() {
        entries.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        switch (currentTab) {
            case MAP_PROPERTIES -> {
                for (MapEntry map : maps) {
                    if (matches(q, map.id, map.name.getString())) {
                        entries.add(Entry.map(map, map.name));
                    }
                }
            }
            case SCENE_BLOCKS -> sceneBlockItems().forEach(item -> addItemEntry(q, item));
            case QUEST_BLOCKS -> questBlockItems().forEach(item -> addItemEntry(q, item));
            case MECHANICS -> {
                String[] mechIds = { "tasks", "status_bar", "sabotage", "conduit_core", "game_currency", "train_target",
                        "special_roles" };
                for (String id : mechIds) {
                    Component name = Component.translatable("map_intro.mechanic." + id + ".title");
                    if (matches(q, id, name.getString())) {
                        entries.add(Entry.text(id, name));
                    }
                }
            }
        }
        if (selected != null && entries.stream().noneMatch(e -> e.sameTarget(selected))) {
            selected = entries.isEmpty() ? null : entries.get(0);
        }
        updateListScrollBounds();
    }

    private void addItemEntry(String query, Item item) {
        Component name = item.getDescription();
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (matches(query, id, name.getString())) {
            entries.add(Entry.item(item, name));
        }
    }

    private static boolean matches(String query, String id, String name) {
        return query.isBlank()
                || id.toLowerCase(Locale.ROOT).contains(query)
                || name.toLowerCase(Locale.ROOT).contains(query);
    }

    private void updateListScrollBounds() {
        int totalH = entries.size() * (CARD_H + CARD_GAP) - CARD_GAP;
        maxListScroll = Math.max(0, totalH - listAreaH);
        listScrollOffset = Mth.clamp(listScrollOffset, 0, maxListScroll);
    }

    // ---------- 详情构建 ----------
    private void rebuildDetail() {
        detailLines.clear();
        detailScrollOffset = 0;
        int wrapW = Math.max(80, rightW - PANEL_PAD * 2 - SCROLL_W - 4);
        if (selected == null) {
            addWrapped(Component.translatable("map_intro.loading").withStyle(ChatFormatting.GRAY), wrapW);
            updateDetailScrollBounds();
            return;
        }
        if (selected.map != null) {
            buildMapDetail(selected.map, wrapW);
        } else if (selected.item != null) {
            buildBlockDetail(selected.item, wrapW);
        } else {
            buildMechanicDetail(selected.id, wrapW);
        }
        updateDetailScrollBounds();
    }

    private void updateDetailScrollBounds() {
        int lineH = font.lineHeight + 2;
        int totalH = detailLines.size() * lineH;
        maxDetailScroll = Math.max(0, totalH - rightContentH);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, maxDetailScroll);
    }

    // 沿用原始的地图详情构建方法（略作调整）
    private void buildMapDetail(MapEntry map, int wrapW) { /* ... 与原来相同 ... */
        addWrapped(Component.literal(map.name.getString()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), wrapW);
        addWrapped(Component.translatable("map_intro.map.id", map.id).withStyle(ChatFormatting.GRAY), wrapW);
        addBlank();
        if (map.voteMap != null) {
            addSection("map_intro.section.vote_config", wrapW);
            addLine("map_intro.vote.display_name", map.name.getString(), wrapW);
            addLine("map_intro.vote.min_count",
                    Component.translatable(map.voteMap.minCount() == -1
                            ? "map_intro.vote.no_min_count"
                            : "map_intro.vote.count_value", map.voteMap.minCount()),
                    wrapW);
            addLine("map_intro.vote.max_count",
                    Component.translatable(map.voteMap.maxCount() == -1
                            ? "map_intro.vote.no_max_count"
                            : "map_intro.vote.count_value", map.voteMap.maxCount()),
                    wrapW);
            addLine(map.voteMap.canSelect() ? "map_intro.vote.can_select.true" : "map_intro.vote.can_select.false",
                    wrapW);
            addLine("map_intro.vote.game_modes", gameModesText(map.voteMap.gameModes()), wrapW);
            addBlank();
        }
        addSection("map_intro.section.special_roles", wrapW);
        List<Component> specialLines = MapSpecialRoleLines.build(map.id, bagMaps, policeMaps,
                underwaterMaps, airMaps, trapMaps, horseMaps, map.json);
        if (specialLines.isEmpty()) {
            addWrapped(Component.translatable("map_intro.special.none").withStyle(ChatFormatting.GRAY), wrapW);
        } else {
            for (Component specialLine : specialLines) {
                addWrapped(specialLine, wrapW);
            }
        }
        addBlank();
        addSection("map_intro.section.properties", wrapW);
        JsonObject json = map.json;
        addLine("map_intro.property.room_count", intValue(json, "roomCount", 1), wrapW);
        addTaskSet(json, "disabledTasks", "map_intro.property.disabled_tasks", false, wrapW);
        addRoleSet(json, "disabledRoles", "map_intro.property.disabled_roles", wrapW);
        addTaskSet(json, "enableSceneTask", "map_intro.property.scene_tasks", true, wrapW);
        if (boolValue(json, "minigameQuestEnabled", false))
            addLine("map_intro.property.minigame_quest", wrapW);
        if (meetingBoolValue(json, "meetingEnabled", false))
            addLine("map_intro.property.meeting_enabled", wrapW);
        if (meetingBoolValue(json, "meetingVoteEnabled", false))
            addLine("map_intro.property.meeting_vote_enabled", wrapW);
        if (meetingBoolValue(json, "bellMeetingEnabled", false))
            addLine("map_intro.property.bell_meeting_enabled", wrapW);
        String status = stringValue(json, "mapStatusBar", "NONE");
        if (!status.equalsIgnoreCase("NONE") && !status.isBlank())
            addLine("map_intro.property.status_bar", statusName(status), wrapW);
        addLine(boolValue(json, "canSwim", false) ? "map_intro.property.can_swim.true"
                : "map_intro.property.can_swim.false", wrapW);
        if (boolValue(json, "enableOxygenDrowning", false))
            addLine("map_intro.property.oxygen_drowning", wrapW);
        addLine(boolValue(json, "canJump", false) ? "map_intro.property.can_jump.true"
                : "map_intro.property.can_jump.false", wrapW);
        if (boolValue(json, "snowEnabled", false))
            addLine("map_intro.property.snow", wrapW);
        if (boolValue(json, "sandEnabled", false))
            addLine("map_intro.property.sand", wrapW);
        if (!boolValue(json, "fogEnabled", true))
            addLine("map_intro.property.no_fog", wrapW);
        addLine("map_intro.property.fog_end", trimNumber(doubleValue(json, "fogEnd", 200.0D)), wrapW);
        String weather = stringValue(json, "weather", "clear");
        if (!weather.equalsIgnoreCase("clear"))
            addLine("map_intro.property.weather", weatherName(weather), wrapW);
        double gravity = doubleValue(json, "gravity", 0.08D);
        if (Math.abs(gravity - 0.08D) > 0.0001D) {
            addLine("map_intro.property.gravity",
                    Component.translatable(gravity < 0.08D ? "map_intro.gravity.low" : "map_intro.gravity.high"),
                    wrapW);
        }
        addEffects(json, wrapW);
        addInitialItems(json, wrapW);
        long time = longValue(json, "time", 18000L);
        if (time != 18000L)
            addLine("map_intro.property.time", Component.translatable(timeName(time)), wrapW);
        if (boolValue(json, "daylightCycle", false))
            addLine("map_intro.property.daylight_cycle", wrapW);
        if (boolValue(json, "weatherCycle", false))
            addLine("map_intro.property.weather_cycle", wrapW);
    }

    private void buildBlockDetail(Item item, int wrapW) {
        addWrapped(item.getDescription().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), wrapW);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        addWrapped(Component.translatable("map_intro.block.id", id.toString()).withStyle(ChatFormatting.GRAY), wrapW);
        addBlank();
        String descKey = "map_intro.block." + id.getNamespace() + "." + id.getPath() + ".desc";
        String desc = Language.getInstance().getOrDefault(descKey);
        if (!desc.equals(descKey)) {
            for (String part : desc.split("\\\\n|\\n")) {
                addWrapped(Component.literal(part), wrapW);
            }
        } else {
            addWrapped(Component.translatable("map_intro.block.no_desc").withStyle(ChatFormatting.GRAY), wrapW);
        }
    }

    private void buildMechanicDetail(String id, int wrapW) {
        addWrapped(Component.translatable("map_intro.mechanic." + id + ".title").withStyle(ChatFormatting.AQUA,
                ChatFormatting.BOLD), wrapW);
        addBlank();
        String text = Language.getInstance().getOrDefault("map_intro.mechanic." + id + ".body");
        for (String part : text.split("\\\\n|\\n")) {
            addWrapped(Component.literal(part), wrapW);
            addBlank();
        }
    }

    // 辅助格式化方法（与原版一致）
    private void addSection(String key, int wrapW) {
        addWrapped(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), wrapW);
    }

    private void addLine(String key, int wrapW) {
        addWrapped(Component.translatable(key), wrapW);
    }

    private void addLine(String key, Object value, int wrapW) {
        addWrapped(Component.translatable(key, value), wrapW);
    }

    private void addWrapped(Component text, int wrapW) {
        detailLines.addAll(font.split(text, wrapW));
    }

    private void addBlank() {
        detailLines.add(FormattedCharSequence.EMPTY);
    }

    // ========== 渲染 ==========
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        computeLayout();
        drawPanelBg(graphics, leftX, panelY, leftW, panelH);
        drawPanelBg(graphics, rightX, panelY, rightW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.fillGradient(0, 0, width, panelY - 4, 0xBB000000, 0x00000000);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xF5E8C8);

        renderCategoryBar(graphics, mouseX, mouseY);
        renderLeftList(graphics, mouseX, mouseY);
        renderRightPanel(graphics, mouseX, mouseY);

        graphics.drawCenteredString(font, Component.translatable("map_intro.hint").withStyle(ChatFormatting.GRAY),
                width / 2, height - 24, MUTED);
    }

    // 分类标签栏（左侧顶部）
    private void renderCategoryBar(GuiGraphics g, int mouseX, int mouseY) {
        int barX = leftX + PANEL_PAD;
        int barW = leftW - PANEL_PAD * 2;
        int n = TABS.size();
        int[] naturalW = new int[n];
        int totalNatural = (n - 1) * 2; // 间隔
        for (int i = 0; i < n; i++) {
            naturalW[i] = font.width(Component.translatable(TABS.get(i).labelKey)) + 10;
            totalNatural += naturalW[i];
        }
        float scale = totalNatural > barW ? (float) barW / totalNatural : 1f;
        int curX = barX;
        for (int i = 0; i < n; i++) {
            int tw = (int) (naturalW[i] * scale);
            tabX[i] = curX;
            tabW[i] = tw;

            boolean active = (i == selectedCategoryIndex);
            boolean hovered = !active && inside(mouseX, mouseY, curX, categoryBarY, tw, CATEGORY_BAR_H);
            int baseColor = TABS.get(i).color;

            if (active) {
                g.fillGradient(curX, categoryBarY, curX + tw, categoryBarY + CATEGORY_BAR_H,
                        blendColors(0xFF1A1008, baseColor, 0.55f), blendColors(0xFF120A04, baseColor, 0.30f));
                g.fill(curX, categoryBarY + CATEGORY_BAR_H - 2, curX + tw, categoryBarY + CATEGORY_BAR_H, baseColor);
                g.fill(curX, categoryBarY, curX + 1, categoryBarY + CATEGORY_BAR_H,
                        (baseColor & 0x00FFFFFF) | 0xAA000000);
                g.fill(curX + tw - 1, categoryBarY, curX + tw, categoryBarY + CATEGORY_BAR_H,
                        (baseColor & 0x00FFFFFF) | 0xAA000000);
            } else if (hovered) {
                g.fillGradient(curX, categoryBarY, curX + tw, categoryBarY + CATEGORY_BAR_H,
                        blendColors(0xFF1A1008, baseColor, 0.25f), blendColors(0xFF120A04, baseColor, 0.12f));
                g.renderOutline(curX, categoryBarY, tw, CATEGORY_BAR_H, (baseColor & 0x00FFFFFF) | 0x44000000);
            } else {
                g.fill(curX, categoryBarY, curX + tw, categoryBarY + CATEGORY_BAR_H, 0x331A1008);
                g.renderOutline(curX, categoryBarY, tw, CATEGORY_BAR_H, 0x338B6914);
            }

            String label = Component.translatable(TABS.get(i).labelKey).getString();
            String truncated = font.plainSubstrByWidth(label, tw - 4);
            int textColor = active ? (baseColor | 0xFF000000) : hovered ? TEXT : MUTED;
            g.drawCenteredString(font, truncated, curX + tw / 2,
                    categoryBarY + (CATEGORY_BAR_H - font.lineHeight) / 2, textColor);
            curX += tw + 2;
        }
    }

    // 左侧列表（卡片式）
    private void renderLeftList(GuiGraphics g, int mouseX, int mouseY) {
        int areaX = leftX + PANEL_PAD;
        int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2;
        g.enableScissor(areaX, listAreaY, areaX + areaW, listAreaY + listAreaH);

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int cardY = listAreaY + i * (CARD_H + CARD_GAP) - listScrollOffset;
            if (cardY + CARD_H < listAreaY || cardY > listAreaY + listAreaH)
                continue;

            boolean active = selected != null && entry.sameTarget(selected);
            boolean hovered = inside(mouseX, mouseY, areaX, cardY, areaW, CARD_H);
            renderCard(g, entry, areaX, cardY, areaW, CARD_H, active, hovered);
        }
        g.disableScissor();

        int sbX = leftX + leftW - PANEL_PAD - SCROLL_W;
        renderVScrollbar(g, sbX, listAreaY, listAreaH, listScrollOffset, maxListScroll,
                entries.size() * (CARD_H + CARD_GAP), mouseX, mouseY, false);
    }

    private void renderCard(GuiGraphics g, Entry entry, int x, int y, int w, int h, boolean active, boolean hovered) {
        int rawColor = getEntryColor(entry);
        int borderColor = active ? 0xFFD4AF37 : (hovered ? blendColors(CARD_BORDER, 0xFFC9A84C, 0.5f) : CARD_BORDER);
        g.fill(x, y, x + w, y + h, borderColor);

        int bgL = active ? 0xFF5A4520 : (hovered ? blendColors(CARD_BG_LEFT, 0xFF5A4520, 0.6f) : CARD_BG_LEFT);
        int bgR = active ? 0xFF3A2A10 : (hovered ? blendColors(CARD_BG_RIGHT, 0xFF3A2A10, 0.6f) : CARD_BG_RIGHT);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, bgL, bgR);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, active ? 0x44FFE8C0 : (hovered ? CARD_HOVER_GLOW : 0x10FFFFFF));

        // 左侧竖线
        int barW = 3;
        g.fill(x + 1, y + 1, x + 1 + barW, y + h - 1, rawColor | 0xFF000000);

        // 图标
        int iconX = x + 1 + barW + 5;
        int iconY = y + (h - ICON_SIZE) / 2;
        g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE,
                blendColors(0xFF120A04, rawColor | 0xFF000000, 0.25f));
        if (entry.item != null) {
            g.renderItem(new ItemStack(entry.item), iconX + 5, iconY + 5);
        } else {
            // 默认图标：地图或书本
            Item iconItem = entry.map != null ? Items.FILLED_MAP : Items.BOOK;
            g.renderItem(new ItemStack(iconItem), iconX + 5, iconY + 5);
        }
        g.renderOutline(iconX, iconY, ICON_SIZE, ICON_SIZE,
                blendColors(rawColor | 0xFF000000, 0xFFFFFFFF, 0.3f));

        // 文字
        int textX = iconX + ICON_SIZE + 5;
        int textMaxW = x + w - textX - 4;
        String name = entry.name.getString();
        String id = entry.id;
        g.drawString(font, font.plainSubstrByWidth(name, textMaxW), textX, y + 5,
                active ? 0xFFD4AF37 : (hovered ? TEXT : 0xFFE8D8B0), false);
        g.drawString(font, font.plainSubstrByWidth(id, textMaxW), textX, y + 5 + font.lineHeight + 1,
                MUTED, false);

        if (active) {
            int indX = x + w - 4;
            g.fill(indX, y + 3, indX + 3, y + h - 3, blendColors(rawColor | 0xFF000000, 0xFFFFFFFF, 0.7f));
        }
    }

    private int getEntryColor(Entry entry) {
        if (entry.map != null)
            return 0xFF5EB7D8; // 地图蓝
        if (entry.item != null) {
            return switch (currentTab) {
                case SCENE_BLOCKS -> 0xFF72C17B;
                case QUEST_BLOCKS -> 0xFFE0AD5B;
                default -> 0xFF9E8B6E;
            };
        }
        return 0xFFB18AE6; // 机制紫
    }

    // 右侧面板
    private void renderRightPanel(GuiGraphics g, int mouseX, int mouseY) {
        // Banner 背景
        if (selected != null) {
            int rawColor = getEntryColor(selected);
            g.fillGradient(rightX + 1, panelY + 1, rightX + rightW / 2, panelY + BANNER_H,
                    (rawColor & 0x00FFFFFF) | 0xCC000000, (rawColor & 0x00FFFFFF) | 0x44000000);
            // 右半渐变透明
            fillGradient2D(g, rightX + rightW / 2, panelY + 1, rightX + rightW - 1, panelY + BANNER_H,
                    (rawColor & 0x00FFFFFF) | 0xCC000000, 0x00000000,
                    (rawColor & 0x00FFFFFF) | 0x44000000, 0x00000000);

            int iconSize = BANNER_H - 6;
            int iconX = rightX + PANEL_PAD, iconY = panelY + 3;
            g.fill(iconX, iconY, iconX + iconSize, iconY + iconSize,
                    blendColors(0xFF120A04, rawColor | 0xFF000000, 0.3f));
            Item iconItem = selected.item != null ? selected.item
                    : (selected.map != null ? Items.FILLED_MAP : Items.BOOK);
            g.renderItem(new ItemStack(iconItem), iconX + (iconSize - 16) / 2, iconY + (iconSize - 16) / 2);
            g.renderOutline(iconX, iconY, iconSize, iconSize, (rawColor & 0x00FFFFFF) | 0xAA000000);

            Component nameText = selected.name;
            g.drawString(font, nameText, iconX + iconSize + 5, panelY + (BANNER_H - font.lineHeight) / 2, TEXT, true);
        } else {
            g.drawCenteredString(font, Component.translatable("map_intro.loading").withStyle(ChatFormatting.GRAY),
                    rightX + rightW / 2, panelY + panelH / 2, MUTED);
        }

        // 详情文本区域
        int contentX = rightX + PANEL_PAD;
        int contentW = rightW - PANEL_PAD * 2 - SCROLL_W - 2;
        g.enableScissor(contentX, rightContentY, contentX + contentW, rightContentY + rightContentH);
        int lineH = font.lineHeight + 2;
        int lineCount = detailLines.size();
        for (int i = 0; i < lineCount; i++) {
            int lineY = rightContentY + i * lineH - detailScrollOffset;
            if (lineY + lineH > rightContentY && lineY < rightContentY + rightContentH) {
                g.drawString(font, detailLines.get(i), contentX, lineY, TEXT, false);
            }
        }
        g.disableScissor();

        int sbX = rightX + rightW - PANEL_PAD - SCROLL_W;
        renderVScrollbar(g, sbX, rightContentY, rightContentH, detailScrollOffset, maxDetailScroll,
                lineCount * lineH, mouseX, mouseY, false);
    }

    // ========== 滚动条 ==========
    private void renderVScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int maxScroll, int totalContentH,
            int mouseX, int mouseY, boolean dragging) {
        g.fill(x, y, x + SCROLL_W, y + h, SCROLL_TRACK);
        g.fill(x + 1, y + 1, x + SCROLL_W - 1, y + h - 1, 0x558B6914);
        if (maxScroll <= 0)
            return;
        float ratio = Math.min(1f, (float) h / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) scroll / maxScroll));
        boolean hl = dragging || inside(mouseX, mouseY, x, thumbY, SCROLL_W, thumbH);
        g.fill(x, thumbY, x + SCROLL_W, thumbY + thumbH, hl ? SCROLL_THUMB_HL : SCROLL_THUMB);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + thumbH - 1, hl ? 0xFFD4AF37 : 0xFFB8960C);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    // 二维渐变辅助（从 RoleIntroduceScreen 移植）
    private void fillGradient2D(GuiGraphics g, int x1, int y1, int x2, int y2,
            int colorTL, int colorTR, int colorBL, int colorBR) {
        var consumer = g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        var matrix = g.pose().last().pose();
        int z = 0;
        consumer.addVertex(matrix, (float) x1, (float) y1, z).setColor(colorTL);
        consumer.addVertex(matrix, (float) x1, (float) y2, z).setColor(colorBL);
        consumer.addVertex(matrix, (float) x2, (float) y2, z).setColor(colorBR);
        consumer.addVertex(matrix, (float) x2, (float) y1, z).setColor(colorTR);
        g.flush();
    }

    // ========== 鼠标事件 ==========
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // 分类标签
            for (int i = 0; i < TABS.size(); i++) {
                if (tabW[i] > 0 && inside(mx, my, tabX[i], categoryBarY, tabW[i], CATEGORY_BAR_H)) {
                    if (selectedCategoryIndex != i) {
                        selectedCategoryIndex = i;
                        currentTab = TABS.get(i).tab;
                        listScrollOffset = 0;
                        playClickSound();
                        rebuildEntries();
                        if (selected != null && !entries.contains(selected)) {
                            selected = entries.isEmpty() ? null : entries.get(0);
                        }
                        rebuildDetail();
                    }
                    return true;
                }
            }

            // 左侧列表
            int areaX = leftX + PANEL_PAD;
            int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2;
            if (inside(mx, my, areaX, listAreaY, areaW, listAreaH)) {
                int idx = (int) ((my - listAreaY + listScrollOffset) / (CARD_H + CARD_GAP));
                if (idx >= 0 && idx < entries.size()) {
                    Entry clicked = entries.get(idx);
                    if (!clicked.sameTarget(selected)) {
                        selected = clicked;
                        playClickSound();
                        rebuildDetail();
                    }
                    return true;
                }
            }

            // 左侧滚动条
            int lsbX = leftX + leftW - PANEL_PAD - SCROLL_W;
            if (inside(mx, my, lsbX, listAreaY, SCROLL_W, listAreaH) && maxListScroll > 0) {
                // 开始拖拽（简化：直接计算位置）
                listScrollOffset = (int) ((my - listAreaY - (listAreaH * (1 - (float) listAreaH / (entries.size() * (CARD_H + CARD_GAP)))) * (float) listScrollOffset / maxListScroll) * maxListScroll / (listAreaH - SCROLL_MIN_THUMB)); // 太复杂，省略拖拽实现，用简单方式
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= leftX && mx < leftX + leftW && my >= listAreaY && my < listAreaY + listAreaH) {
            listScrollOffset = Mth.clamp(listScrollOffset - (int) (scrollY * (CARD_H + CARD_GAP)), 0, maxListScroll);
            return true;
        }
        if (mx >= rightX && mx < rightX + rightW && my >= rightContentY && my < rightContentY + rightContentH) {
            detailScrollOffset = Mth.clamp(detailScrollOffset - (int) (scrollY * (font.lineHeight + 2) * 3), 0,
                    maxDetailScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = super.charTyped(codePoint, modifiers);
        rebuildEntries();
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        rebuildEntries();
        return handled;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void playClickSound() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    // ========== 工具方法 ==========
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        g.renderOutline(x, y, w, h, PANEL_OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_HIGHLIGHT);
    }

    private static int blendColors(int c1, int c2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a = (int) ((c1 >>> 24) + ((c2 >>> 24) - (c1 >>> 24)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ========== 内部类型 ==========
    private enum Tab {
        MAP_PROPERTIES, SCENE_BLOCKS, QUEST_BLOCKS, MECHANICS
    }

    private record TabInfo(Tab tab, String labelKey, int color) {
    }

    private static final class MapEntry {
        final String id;
        final JsonObject json;
        final Component name;
        final MapIntroSyncPayload.VoteMap voteMap;

        MapEntry(String id, JsonObject json, MapIntroSyncPayload.VoteMap voteMap) {
            this.id = id;
            this.json = json;
            this.voteMap = voteMap;
            this.name = mapDisplayName(id, voteMap);
        }
    }

    private static final class Entry {
        final String id;
        final Component name;
        final MapEntry map;
        final Item item;

        private Entry(String id, Component name, MapEntry map, Item item) {
            this.id = id;
            this.name = name;
            this.map = map;
            this.item = item;
        }

        static Entry map(MapEntry map, Component name) {
            return new Entry(map.id, name, map, null);
        }

        static Entry item(Item item, Component name) {
            return new Entry(BuiltInRegistries.ITEM.getKey(item).toString(), name, null, item);
        }

        static Entry text(String id, Component name) {
            return new Entry(id, name, null, null);
        }

        boolean sameTarget(Entry other) {
            return other != null && id.equals(other.id)
                    && ((map == null && other.map == null) || (map != null && map.equals(other.map)))
                    && ((item == null && other.item == null) || (item != null && item.equals(other.item)));
        }
    }

    // 原版数据静态方法（保留）
    private static List<Item> sceneBlockItems() {
        return List.of(
                ModSceneBlocks.POISON_ZONE.asItem(), ModSceneBlocks.BREAKING_BRIDGE.asItem(),
                ModSceneBlocks.SABOTAGE_BRIDGE.asItem(), ModSceneBlocks.DRIPPING_STALACTITE.asItem(),
                ModSceneBlocks.FOG_ZONE.asItem(), ModSceneBlocks.MANHOLE.asItem(), ModSceneBlocks.CELLAR.asItem(),
                ModSceneBlocks.SCENE_GATE.asItem(), ModSceneBlocks.FLAMETHROWER.asItem(),
                ModSceneBlocks.ROLLING_STONE_TRIGGER.asItem(), ModSceneBlocks.TRAIN_TARGET.asItem(),
                ModSceneBlocks.INCINERATOR.asItem(), ModSceneBlocks.MOVING_PLATFORM.asItem(),
                ModSceneBlocks.HURRICANE_DEVICE.asItem(), ModSceneBlocks.COFFIN.asItem(),
                ModSceneBlocks.WATER_PUMP.asItem(), ModSceneBlocks.TRASH_CAN.asItem(),
                ModBlocks.VENDING_MACHINES_BLOCK.asItem(), ModBlocks.LOTTERY_MACHINE_BLOCK.asItem(),
                ModBlocks.DEVIL_ROULETTE_TABLE.asItem(), ModBlocks.HOTBAR_STORAGE.asItem(),
                ModBlocks.SUPPLY_CRATE_BLOCK.asItem(), ModBlocks.KILL_BLOCK.asItem(),
                ModBlocks.KILL_BLOCK_PANEL.asItem(),
                SREBlocks.TRAIN_LIGHT.asItem(), SREBlocks.REMOTE_REDSTONE.asItem(),
                TMMBlocks.TRIMMED_LANTERN.asItem(), TMMBlocks.WALL_LAMP.asItem(), TMMBlocks.NEON_PILLAR.asItem(),
                TMMBlocks.NEON_TUBE.asItem(), TMMBlocks.ENTITY_INTERACTION_BLOCK_ITEM,
                TMMBlocks.ENTITY_INTERACTION_PANEL_ITEM, TMMBlocks.TICKET_OFFICE_ITEM,
                TMMBlocks.TICKET_GATE_ITEM, TMMBlocks.EFFECT_GENERATOR_ITEM);
    }

    private static List<Item> questBlockItems() {
        return List.of(
                ModSceneBlocks.REACTOR.asItem(), ModSceneBlocks.WATER_VALVE.asItem(),
                ModSceneBlocks.DEBRIS_PILE.asItem(),
                ModSceneBlocks.STOVE.asItem(), ModSceneBlocks.DUST.asItem(), ModSceneBlocks.TRANSPORT_POINT.asItem(),
                ModSceneBlocks.STATUE.asItem(), ModSceneBlocks.BUSH.asItem(), ModSceneBlocks.CROP.asItem(),
                Items.BLACK_CONCRETE, Items.NOTE_BLOCK, Items.LECTERN,
                TMMBlocks.LIGHT_TOILET.asItem(), TMMBlocks.DARK_TOILET.asItem(),
                TMMBlocks.WHITE_TRIMMED_BED.asItem(), TMMBlocks.RED_TRIMMED_BED.asItem(),
                TMMBlocks.STAINLESS_STEEL_SPRINKLER.asItem(), TMMBlocks.GOLD_SPRINKLER.asItem(),
                TMMBlocks.FOOD_PLATTER.asItem(), TMMBlocks.DRINK_TRAY.asItem(),
                TMMBlocks.CAMERA.asItem(), TMMBlocks.SECURITY_MONITOR.asItem(),
                TMMBlocks.MINIGAME_QUEST_BLOCK_ITEM, TMMBlocks.MINIGAME_QUEST_PANEL_ITEM);
    }

    private static Component gameModesText(List<String> values) {
        if (values == null || values.isEmpty()
                || values.stream().allMatch(value -> value == null || value.isBlank())) {
            return Component.translatable("map_intro.vote.all_game_modes");
        }
        List<String> names = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank())
                continue;
            String path = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
            names.add(Component.translatableWithFallback("game_mode.noellesroles." + path,
                    Component.translatableWithFallback("game_mode.starrailexpress." + path, value).getString())
                    .getString());
        }
        if (names.isEmpty())
            return Component.translatable("map_intro.vote.all_game_modes");
        return Component.literal(String.join(", ", names));
    }

    private static Component mapDisplayName(String id, MapIntroSyncPayload.VoteMap voteMap) {
        if (voteMap != null && voteMap.displayName() != null && !voteMap.displayName().isBlank()) {
            return translateConfiguredText(voteMap.displayName());
        }
        return Component.translatableWithFallback("map." + id + ".name", id);
    }

    private static Component translateConfiguredText(String value) {
        String trimmed = value.trim();
        List<String> candidates = new ArrayList<>();
        candidates.add(trimmed);
        if (trimmed.startsWith("gui.tmm.map_selector.")) {
            candidates.add("gui.sre.map_selector." + trimmed.substring("gui.tmm.map_selector.".length()));
        } else if (trimmed.startsWith("gui.sre.map_selector.")) {
            candidates.add("gui.tmm.map_selector." + trimmed.substring("gui.sre.map_selector.".length()));
        }
        Language language = Language.getInstance();
        for (String key : candidates) {
            String translated = language.getOrDefault(key);
            if (!translated.equals(key)) {
                return Component.literal(translated);
            }
        }
        return Component.literal(trimmed);
    }

    private static String statusName(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "COLD", "WARM", "WARMTH" -> Component.translatable("map_intro.status.warmth").getString();
            case "THIRST" -> Component.translatable("map_intro.status.thirst").getString();
            case "HUNGER" -> Component.translatable("map_intro.status.hunger").getString();
            default -> value;
        };
    }

    private static Component weatherName(String value) {
        return Component.translatableWithFallback("map_intro.weather." + value.toLowerCase(Locale.ROOT), value);
    }

    private static String timeName(long time) {
        long t = Math.floorMod(time, 24000L);
        long[] points = { 6000L, 12000L, 18000L, 23000L };
        String[] keys = { "map_intro.time.noon", "map_intro.time.dusk", "map_intro.time.midnight",
                "map_intro.time.dawn" };
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            long dist = Math.min(Math.abs(t - points[i]), 24000L - Math.abs(t - points[i]));
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return keys[best];
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static long longValue(JsonObject json, String key, long fallback) {
        return json.has(key) ? json.get(key).getAsLong() : fallback;
    }

    private static double doubleValue(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static boolean boolValue(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static boolean meetingBoolValue(JsonObject json, String key, boolean fallback) {
        if (json.has(key))
            return json.get(key).getAsBoolean();
        if (json.has("settings") && json.get("settings").isJsonObject()) {
            JsonObject settings = json.getAsJsonObject("settings");
            if (settings.has(key))
                return settings.get(key).getAsBoolean();
        }
        return fallback;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String trimNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001D ? String.valueOf((int) Math.rint(value))
                : String.format(Locale.ROOT, "%.2f", value);
    }

    // 保留原始的任务/角色辅助方法
    private void addTaskSet(JsonObject json, String key, String labelKey, boolean scene, int wrapW) {
        if (!json.has(key) || !json.get(key).isJsonArray() || json.getAsJsonArray(key).isEmpty())
            return;
        List<String> names = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            String id = element.getAsString();
            names.add(taskName(id, scene));
        }
        addLine(labelKey, String.join(", ", names), wrapW);
    }

    private String taskName(String id, boolean scene) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (scene) {
            return Component.translatableWithFallback("scene_task.noellesroles." + normalized,
                    Component.translatableWithFallback("task." + normalized, id).getString()).getString();
        }
        if ("raed_book".equals(normalized))
            normalized = "read_book";
        return Component.translatableWithFallback("task." + normalized, id).getString();
    }

    private void addRoleSet(JsonObject json, String key, String labelKey, int wrapW) {
        if (!json.has(key) || !json.get(key).isJsonArray() || json.getAsJsonArray(key).isEmpty())
            return;
        List<String> names = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            names.add(roleName(element.getAsString()));
        }
        addLine(labelKey, String.join(", ", names), wrapW);
    }

    private String roleName(String id) {
        SRERole role = null;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null) {
            role = TMMRoles.getRole(location);
        }
        if (role == null) {
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            for (SRERole candidate : TMMRoles.ROLES.values()) {
                if (candidate.identifier().getPath().equals(path)) {
                    role = candidate;
                    break;
                }
            }
        }
        return role == null ? id : role.getName().getString();
    }

    private void addEffects(JsonObject json, int wrapW) {
        if (!json.has("effect") || !json.get("effect").isJsonArray() || json.getAsJsonArray("effect").isEmpty())
            return;
        List<String> parts = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("effect")) {
            String[] split = element.getAsString().split(",", 2);
            ResourceLocation id = ResourceLocation.tryParse(split[0]);
            int level = split.length > 1 ? parseInt(split[1], 1) : 1;
            String name = split[0];
            if (id != null) {
                var effect = BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
                if (effect != null)
                    name = Component.translatable(effect.value().getDescriptionId()).getString();
            }
            parts.add(Component.translatable("map_intro.effect.entry", name, level).getString());
        }
        addLine("map_intro.property.effects", String.join(", ", parts), wrapW);
    }

    private void addInitialItems(JsonObject json, int wrapW) {
        if (!json.has("initialItems") || !json.get("initialItems").isJsonArray()
                || json.getAsJsonArray("initialItems").isEmpty())
            return;
        List<String> parts = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("initialItems")) {
            String[] split = element.getAsString().split("[;,]", 2);
            ResourceLocation id = ResourceLocation.tryParse(split[0]);
            int count = split.length > 1 ? parseInt(split[1], 1) : 1;
            if (id == null)
                continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR)
                continue;
            String name = item.getDescription().getString();
            parts.add(count > 1 ? Component.translatable("map_intro.item.entry", name, count).getString() : name);
        }
        if (!parts.isEmpty())
            addLine("map_intro.property.initial_items", String.join(", ", parts), wrapW);
    }
}