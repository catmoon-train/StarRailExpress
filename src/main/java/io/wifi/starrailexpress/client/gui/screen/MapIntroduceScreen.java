/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.gui.screen;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapIntroClientCache;
import io.wifi.starrailexpress.network.MapIntroRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.gui.anim.GuiAnim;
import io.wifi.starrailexpress.index.SREBlocks;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.client.gui.screen.maprotation.MapIntroDetail;
import io.wifi.starrailexpress.network.MapDisplayInfo;
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

    private static final List<TabInfo> TABS = List.of(
            new TabInfo(Tab.MAP_PROPERTIES, "map_intro.tab.map_properties", 0xFF5EB7D8),
            new TabInfo(Tab.SCENE_BLOCKS, "map_intro.tab.scene_blocks", 0xFF72C17B),
            new TabInfo(Tab.QUEST_BLOCKS, "map_intro.tab.quest_blocks", 0xFFE0AD5B),
            new TabInfo(Tab.MECHANICS, "map_intro.tab.mechanics", 0xFFB18AE6));

    // ---------- 界面状态 ----------
    private Screen parent;
    private final long openAtMs = System.currentTimeMillis();
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
        if (payload == null || payload.isIgnored()) {
            return; // 不兼容/损坏的包：什么都不做（通常是对面服务端版本不一致）
        }
        maps.clear();
        for (MapDisplayInfo info : payload.maps()) {
            if (info != null && info.id() != null && !info.id().isBlank()) {
                maps.add(new MapEntry(info));
            }
        }
        maps.sort(Comparator.comparing(m -> m.name.getString()));
        rebuildEntries();
        if (selected == null && !entries.isEmpty()) {
            selected = entries.get(0);
        } else if (selected != null && entries.stream().noneMatch(e -> e.sameTarget(selected))) {
            selected = entries.isEmpty() ? null : entries.get(0);
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

        // 缓存为空时自动拉一次数据（以前从游戏菜单进入这个界面会什么都看不到）
        if (MapIntroClientCache.isEmpty() && !MapIntroClientCache.isRefreshPending()) {
            requestMapData(MapIntroClientCache.isIncludeAll());
        }
    }

    /** 请求地图展示数据；{@code includeAll} 仅管理员（权限 ≥2）会被服务端接受。 */
    private void requestMapData(boolean includeAll) {
        MapIntroClientCache.beginRefresh(includeAll);
        ClientPlayNetworking.send(new MapIntroRequestPayload(includeAll));
    }

    /** 管理员才显示的「显示全部地图」勾选框（含地图文件里但未登记进投票配置的地图）。 */
    private boolean showAllToggleVisible() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2);
    }

    private void renderShowAllToggle(GuiGraphics g, int mouseX, int mouseY) {
        if (!showAllToggleVisible()) {
            return;
        }
        int boxSize = 9;
        int labelW = font.width(Component.translatable("map_intro.show_all_maps"));
        int boxX = panelX + usableWidth - boxSize - 4;
        int boxY = Math.max(4, panelY - 18);
        int labelX = boxX - 4 - labelW;
        boolean checked = MapIntroClientCache.isIncludeAll();
        boolean hovered = inside(mouseX, mouseY, labelX - 2, boxY - 3, labelW + boxSize + 8, boxSize + 6);

        g.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xC0100A06);
        g.renderOutline(boxX, boxY, boxSize, boxSize, hovered ? 0xFFF5E8C8 : 0xFF8B6914);
        if (checked) {
            g.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, 0xFF3AF07A);
        }
        g.drawString(font, Component.translatable("map_intro.show_all_maps"), labelX, boxY + 1,
                hovered ? 0xFFF5E8C8 : MUTED, false);
    }

    private boolean handleShowAllToggleClick(double mx, double my) {
        if (!showAllToggleVisible()) {
            return false;
        }
        int boxSize = 9;
        int labelW = font.width(Component.translatable("map_intro.show_all_maps"));
        int boxX = panelX + usableWidth - boxSize - 4;
        int boxY = Math.max(4, panelY - 18);
        if (!inside(mx, my, boxX - labelW - 6, boxY - 3, labelW + boxSize + 12, boxSize + 6)) {
            return false;
        }
        boolean wanted = !MapIntroClientCache.isIncludeAll();
        maps.clear();
        entries.clear();
        selected = null;
        requestMapData(wanted);
        playClickSound();
        return true;
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

    /**
     * 地图详情：直接复用 {@link MapIntroDetail}（内容与地图轮抽界面完全一致），
     * 属性判定统一走服务端解析好的 DTO，不再在这里读原始 JSON。
     */
    private void buildMapDetail(MapEntry map, int wrapW) {
        detailLines.addAll(MapIntroDetail.build(font, wrapW, map.info));
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
        drawPanelBg(graphics, leftX, panelY, leftW, panelH);
        drawPanelBg(graphics, rightX, panelY, rightW, panelH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float intro = openProgress();
        float slideY = openSlideY();
        int localMouseY = Math.round(mouseY - slideY);

        graphics.fillGradient(0, 0, width, height, 0xF018120A, 0xF0061018);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, slideY, 0.0f);
        super.render(graphics, mouseX, localMouseY, delta);
        graphics.fillGradient(0, 0, width, panelY - 4, 0xBB000000, 0x00000000);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xF5E8C8);

        if (intro > 0.08f) {
            renderCategoryBar(graphics, mouseX, localMouseY);
            renderLeftList(graphics, mouseX, localMouseY);
            renderRightPanel(graphics, mouseX, localMouseY);
        }

        graphics.drawCenteredString(font, Component.translatable("map_intro.hint").withStyle(ChatFormatting.GRAY),
                width / 2, height - 24, MUTED);
        graphics.pose().popPose();

        renderShowAllToggle(graphics, mouseX, localMouseY);

        int veil = Math.round((1.0f - intro) * 220.0f);
        if (veil > 3) {
            graphics.fill(0, 0, width, height, veil << 24);
        }
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
    private float openProgress() {
        return GuiAnim.openBezier((System.currentTimeMillis() - openAtMs) / 420.0f);
    }

    private float openSlideY() {
        return (1.0f - openProgress()) * Math.max(28.0f, panelH * 0.22f);
    }

    private double mappedMouseY(double mouseY) {
        return mouseY - openSlideY();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && handleShowAllToggleClick(mx, my)) {
            return true;
        }
        my = mappedMouseY(my);
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
        my = mappedMouseY(my);
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
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mappedMouseY(mouseY));
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
        final MapDisplayInfo info;
        final Component name;

        MapEntry(MapDisplayInfo info) {
            this.id = info.id();
            this.info = info;
            this.name = MapIntroDetail.mapDisplayName(info.id(), info);
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
                ModSceneBlocks.FOG_ZONE.asItem(), ModSceneBlocks.LOOPING_MIRROR.asItem(),
                ModSceneBlocks.MANHOLE.asItem(), ModSceneBlocks.CELLAR.asItem(),
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
}
