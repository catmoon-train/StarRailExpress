package org.agmas.noellesroles.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.agmas.noellesroles.commands.GameUtilsCommand;

public class GameManagementScreen extends Screen {

    Screen parent;

    // ══════════════════════════════════════════════════════════════════
    // 条目类型
    // ══════════════════════════════════════════════════════════════════

    public enum EntryType {
        LABEL, BUTTON, SEPARATOR
    }

    public static class Entry {
        public final EntryType type;
        public final Component text;
        /** 点击执行的指令（不含 /）；LABEL / SEPARATOR 为 null */
        public final String command;
        /** 主题色 */
        public final int color;

        private Entry(EntryType type, Component text, String command, int color) {
            this.type = type;
            this.text = text;
            this.command = command;
            this.color = color;
        }

        // ── 工厂方法 ───────────────────────────────────────────────

        /** 小节标题（黄色粗体，使用翻译键） */
        public static Entry label(String key) {
            return new Entry(EntryType.LABEL,
                    Component.translatable(key).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
                    null, 0xFFCCAA22);
        }

        /** 小节标题（自定义颜色，使用翻译键） */
        public static Entry label(String key, int color) {
            return new Entry(EntryType.LABEL,
                    Component.translatable(key).withStyle(ChatFormatting.BOLD),
                    null, color);
        }

        /** 小节标题（完全自定义 Component） */
        public static Entry label(Component text, int color) {
            return new Entry(EntryType.LABEL, text, null, color);
        }

        /** 按钮（翻译键 + 指令，默认蓝色） */
        public static Entry button(String key, String command) {
            return new Entry(EntryType.BUTTON,
                    Component.translatable(key), command, 0xFF5577CC);
        }

        /** 按钮（翻译键 + 指令 + 主题色） */
        public static Entry button(String key, String command, int color) {
            return new Entry(EntryType.BUTTON,
                    Component.translatable(key), command, color);
        }

        /** 按钮（自定义 Component + 指令 + 主题色） */
        public static Entry button(Component text, String command, int color) {
            return new Entry(EntryType.BUTTON, text, command, color);
        }

        /** 分隔线 */
        public static Entry separator() {
            return new Entry(EntryType.SEPARATOR, Component.empty(), null, 0xFF334466);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 分类
    // ══════════════════════════════════════════════════════════════════

    public static class Category {
        public final String labelKey;
        public final int color;
        public final List<Entry> entries = new ArrayList<>();

        public Category(String labelKey, int color) {
            this.labelKey = labelKey;
            this.color = color;
        }

        /** 追加任意条目，支持链式调用 */
        public Category add(Entry e) {
            entries.add(e);
            return this;
        }

        /**
         * 快捷添加一个小节。
         * 自动写入：小节标题（Entry.label）+ 分隔线（Entry.separator）。
         * 后续条目用 .add(...) 链式追加。
         */
        public Category section(String titleKey) {
            return section(titleKey, color);
        }

        public Category section(String titleKey, int titleColor) {
            entries.add(Entry.label(titleKey, titleColor));
            entries.add(Entry.separator());
            return this;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 【数据定义】在这里追加分类 / 小节 / 按钮，无需改动其他代码。
    // 所有翻译键以 screen.game_manage. 开头。
    // ══════════════════════════════════════════════════════════════════

    public static final List<Category> CATEGORIES = new ArrayList<>();

    public static Random random = new Random();
    static {

        // ── 分类：游戏流程 ──────────────────────────────────────────
        Category game = new Category("screen.game_manage.category.game", 0xFF5577CC);

        game.section("screen.game_manage.section.start", 0xFF5577CC)
                .add(Entry.button("screen.game_manage.btn.start_murder", "tmm:start starrailexpress:murder",
                        0xFF44BB66))
                .add(Entry.button("screen.game_manage.btn.start_wheel", "tmm:start noellesroles:chair_wheel_race",
                        0xFFCC2233))
                .add(Entry.button("screen.game_manage.btn.start_loose_ends", "tmm:start wathe:loose_ends", 0xFFCCAA22));

        var section = game.section("screen.game_manage.section.win", 0xFF7755BB);
        for (var status : GameUtilsCommand.WinStatusSuggestions.allWinStatus) {
            var statusName = status.toString().toLowerCase();
            section.add(Entry.button(
                    Component.translatable("announcement.star.win." + statusName)
                            .append(Component.literal(" " + statusName).withStyle(ChatFormatting.GRAY)),
                    "tmm:game win " + statusName,
                    new java.awt.Color(random.nextInt(0, 256), random.nextInt(0, 256), random.nextInt(0, 256))
                            .getRGB()));
        }

        game.section("screen.game_manage.section.stop", 0xFF7755BB)
                .add(Entry.button("screen.game_manage.btn.stop", "tmm:stop", 0xFFEEBB33))
                .add(Entry.button("screen.game_manage.btn.stop_force", "tmm:stop force", 0xFF334488));

        game.section("screen.game_manage.section.vote", 0xFF7755BB)
                .add(Entry.button("screen.game_manage.btn.vote_10s", "tmm:votemap 200", 0xFF44BB66))
                .add(Entry.button("screen.game_manage.btn.vote_20s", "tmm:votemap 400", 0xFF44BB66))
                .add(Entry.button("screen.game_manage.btn.vote_30s", "tmm:votemap 600", 0xFF44BB66));

        CATEGORIES.add(game);

        // ── 分类：游戏效果管理 ──────────────────────────────────────────
        Category effects = new Category("screen.game_manage.category.effects", 0xFF44BB88);

        effects.section("screen.game_manage.section.blackout", 0xFF44BB88)
                .add(Entry.button("screen.game_manage.btn.trigger_blackout",
                        "tmm:game blackout", 0xFF22BBCC))
                .add(Entry.button("screen.game_manage.btn.stop_blackout",
                        "tmm:game blackout stop", 0xFF88CC44));

        effects.section("screen.game_manage.section.psycho", 0xFFBB5533)
                .add(Entry.button("screen.game_manage.btn.trigger_psycho",
                        "tmm:game psycho", 0xFFBB5533))
                .add(Entry.button("screen.game_manage.btn.stop_psycho",
                        "tmm:game psycho stop", 0xFF5577CC));
        effects.section("screen.game_manage.section.timestop", 0xFFBB5533)
                .add(Entry.button("screen.game_manage.btn.trigger_timestop_5s",
                        "tmm:game timestop 100", 0xFFBB5533))
                .add(Entry.button("screen.game_manage.btn.trigger_timestop_10s",
                        "tmm:game timestop 200", 0xFFBB5533))
                .add(Entry.button("screen.game_manage.btn.trigger_timestop_20s",
                        "tmm:game timestop 400", 0xFFBB5533))
                .add(Entry.button("screen.game_manage.btn.trigger_timestop_30s",
                        "tmm:game timestop 600", 0xFFBB5533))
                .add(Entry.button("screen.game_manage.btn.stop_timestop",
                        "tmm:game timestop stop", 0xFF5577CC));
        CATEGORIES.add(effects);

        // ── 分类：地图 ──────────────────────────────────────────
        Category world = new Category("screen.game_manage.category.world", 0xFFEEBB33);

        world.section("screen.game_manage.section.time", 0xFFEEBB33)
                .add(Entry.button("screen.game_manage.btn.time_day", "time set day", 0xFFEEBB33))
                .add(Entry.button("screen.game_manage.btn.time_noon", "time set noon", 0xFFFFCC44))
                .add(Entry.button("screen.game_manage.btn.time_night", "time set night", 0xFF334488))
                .add(Entry.button("screen.game_manage.btn.time_midnight", "time set midnight", 0xFF223366));

        world.section("screen.game_manage.section.weather", 0xFF55AADD)
                .add(Entry.button("screen.game_manage.btn.weather_clear", "weather clear", 0xFF55AADD))
                .add(Entry.button("screen.game_manage.btn.weather_rain", "weather rain", 0xFF334477))
                .add(Entry.button("screen.game_manage.btn.weather_thunder", "weather thunder", 0xFFAA8833));
        CATEGORIES.add(world);

        // ── 分类：地图配置 ──────────────────────────────────────────
        Category maps = new Category("screen.game_manage.category.maps", 0xFFAA44CC);

        maps.section("screen.game_manage.section.switchmap", 0xFFAA44CC)
                .add(Entry.button("screen.game_manage.btn.switchmap_info",
                        "tmm:switchmap", 0xFF8844CC))
                .add(Entry.button("screen.game_manage.btn.switchmap_list",
                        "tmm:switchmap list", 0xFFCC2233))
                .add(Entry.button("screen.game_manage.btn.switchmap_random",
                        "tmm:switchmap random", 0xFF22BBCC));

        maps.section("screen.game_manage.section.reset_map", 0xFF6644AA)
                .add(Entry.button("screen.game_manage.btn.simple_reset_map",
                        "tmm:game reset asyn simple", 0xFF6644AA))
                .add(Entry.button("screen.game_manage.btn.full_reset_map",
                        "tmm:game reset asyn copy", 0xFF5566BB));
        maps.section("screen.game_manage.section.map_scanner", 0xFFAA4422)
                .add(Entry.button("screen.game_manage.section.scan_reset",
                        "tmm:game scan reset_points", 0xFF6644AA))
                .add(Entry.button("screen.game_manage.section.scan_task",
                        "tmm:game scan task_points", 0xFF5566BB));
        CATEGORIES.add(maps);

        // ── 分类：调试工具 ──────────────────────────────────────────
        Category debug = new Category("screen.game_manage.category.debug", 0xFF778899);

        debug.section("screen.game_manage.section.infos", 0xFF778899)

                .add(Entry.button("screen.game_manage.btn.list_roles",
                        "listRoles", 0xFF667788))
                .add(Entry.button("screen.game_manage.btn.list_game_roles",
                        "listGameRoles", 0xFF778899))
                .add(Entry.button("screen.game_manage.btn.list_rooms",
                        "room", 0xFF556677))
                .add(Entry.button("screen.game_manage.btn.show_game_time",
                        "tmm:game gametime", 0xFF556677))
                .add(Entry.button("screen.game_manage.btn.list_tasks",
                        "tmm:game tasks list", 0xFF526677))
                .add(Entry.button("screen.game_manage.btn.list_roles",
                        "listRoles", 0xFF667788));

        debug.section("screen.game_manage.section.misc", 0xFF5577BB)
                .add(Entry.button("screen.game_manage.btn.reload_config",
                        "tmm:config reload", 0xFF5577CC));

        CATEGORIES.add(debug);
    }

    // ══════════════════════════════════════════════════════════════════
    // 布局常量
    // ══════════════════════════════════════════════════════════════════

    private static final int MAX_USABLE_WIDTH = 520;
    private static final float USABLE_RATIO = 0.65f;

    private static final int PANEL_PAD = 8;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int BANNER_H = 28;

    private static final int BUTTON_H = 26;
    private static final int LABEL_H = 18;
    private static final int SEPARATOR_H = 8;
    private static final int ENTRY_SPACING = 3;

    /** 分类标签栏高度 */
    private static final int TAB_BAR_H = 20;
    /** 相邻标签间距 */
    private static final int TAB_GAP = 2;

    // ══════════════════════════════════════════════════════════════════
    // 运行时状态
    // ══════════════════════════════════════════════════════════════════

    // 布局缓存
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int tabBarY;

    // 分类
    private int selectedCatIdx = 0;
    private final int[] tabX = new int[32];
    private final int[] tabW = new int[32];

    // 当前分类的条目快照（refreshEntries 更新）
    private final List<Entry> currentEntries = new ArrayList<>();

    // 悬停动画（每条目一个进度值 [0,1]）
    private final float[] hoverAnims = new float[256];

    // 滚动
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragStartY = 0;
    private int dragStartOffset = 0;

    // ══════════════════════════════════════════════════════════════════
    // 构造
    // ══════════════════════════════════════════════════════════════════

    public GameManagementScreen() {
        super(Component.translatable("screen.game_manage.title"));
    }

    public GameManagementScreen(Screen parent) {
        this();
        this.parent = parent;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // ══════════════════════════════════════════════════════════════════
    // 初始化
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        computeLayout();
        refreshEntries();
    }

    private void computeLayout() {
        int usableWidth = Math.min((int) (width * USABLE_RATIO), MAX_USABLE_WIDTH);
        panelX = (width - usableWidth) / 2;
        panelY = 52;
        panelW = usableWidth;
        panelH = height - panelY - 48;

        // 分类标签栏紧贴面板上方
        tabBarY = panelY - TAB_BAR_H - 4;

        contentX = panelX + PANEL_PAD;
        contentY = panelY + BANNER_H + PANEL_PAD;
        contentW = panelW - PANEL_PAD * 2 - SCROLL_W - 4;
        contentH = panelH - BANNER_H - PANEL_PAD * 2;
    }

    /** 切换分类后刷新快照并重置动画与滚动 */
    private void refreshEntries() {
        currentEntries.clear();
        if (selectedCatIdx >= 0 && selectedCatIdx < CATEGORIES.size())
            currentEntries.addAll(CATEGORIES.get(selectedCatIdx).entries);
        Arrays.fill(hoverAnims, 0f);
        maxScroll = Math.max(0, totalEntriesHeight() - contentH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    // ══════════════════════════════════════════════════════════════════
    // 高度工具
    // ══════════════════════════════════════════════════════════════════

    private int getEntryH(Entry e) {
        return switch (e.type) {
            case BUTTON -> BUTTON_H;
            case LABEL -> LABEL_H;
            case SEPARATOR -> SEPARATOR_H;
        };
    }

    private int totalEntriesHeight() {
        int h = 0;
        for (int i = 0; i < currentEntries.size(); i++) {
            h += getEntryH(currentEntries.get(i));
            if (i < currentEntries.size() - 1)
                h += ENTRY_SPACING;
        }
        return h;
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        drawPanelBg(g, panelX, panelY, panelW, panelH);
        renderBanner(g);
        renderTabBar(g, mouseX, mouseY);
        renderEntryList(g, mouseX, mouseY);
        renderVScrollbar(g,
                panelX + panelW - PANEL_PAD - SCROLL_W,
                contentY, contentH,
                scrollOffset, maxScroll,
                Math.max(1, totalEntriesHeight()),
                mouseX, mouseY, isDraggingScroll);

        // 顶部遮罩 + 标题
        g.fillGradient(0, 0, width, tabBarY - 2, 0xBB000000, 0x00000000);
        g.drawCenteredString(font, this.title, width / 2, 8, 0xEEEEFF);

        // 底部提示
        g.drawCenteredString(font,
                Component.translatable("screen.game_manage.hint")
                        .withStyle(ChatFormatting.GRAY),
                width / 2, height - 22, 0x88AABB);
    }

    // ── 顶部标题条 ───────────────────────────────────────────────────

    private void renderBanner(GuiGraphics g) {
        int col = currentCategory().color | 0xFF000000;
        // 左侧色条
        g.fill(panelX + 1, panelY + 1, panelX + 4, panelY + BANNER_H, col);
        // 渐变背景
        g.fillGradient(panelX + 4, panelY + 1,
                panelX + panelW / 2, panelY + BANNER_H,
                blendColors(0xCC0C1020, col, 0.30f),
                blendColors(0x440C1020, col, 0.10f));
        g.fillGradient(panelX + panelW / 2, panelY + 1,
                panelX + panelW - 1, panelY + BANNER_H,
                blendColors(0x440C1020, col, 0.10f), 0x00000000);
        // 底部分割线
        g.fill(panelX + 1, panelY + BANNER_H - 1,
                panelX + panelW - 1, panelY + BANNER_H,
                (currentCategory().color & 0x00FFFFFF) | 0x88000000);
        // 标题文字（当前分类名）
        g.drawCenteredString(font,
                Component.translatable(currentCategory().labelKey)
                        .withStyle(ChatFormatting.BOLD),
                panelX + panelW / 2,
                panelY + (BANNER_H - font.lineHeight) / 2,
                0xEEEEFF);
    }

    // ── 分类标签栏 ───────────────────────────────────────────────────

    private void renderTabBar(GuiGraphics g, int mouseX, int mouseY) {
        int n = CATEGORIES.size();
        int[] naturalW = new int[n];
        int totalNatural = TAB_GAP * (n - 1);
        for (int i = 0; i < n; i++) {
            String lbl = Component.translatable(CATEGORIES.get(i).labelKey).getString();
            naturalW[i] = font.width(lbl) + 14;
            totalNatural += naturalW[i];
        }
        float scale = totalNatural > panelW ? (float) panelW / totalNatural : 1f;

        int curX = panelX;
        for (int i = 0; i < n; i++) {
            int tw = (int) (naturalW[i] * scale);
            tabX[i] = curX;
            tabW[i] = tw;

            boolean active = (i == selectedCatIdx);
            boolean hovered = !active && isInRect(mouseX, mouseY, curX, tabBarY, tw, TAB_BAR_H);
            int base = CATEGORIES.get(i).color;

            if (active) {
                g.fillGradient(curX, tabBarY, curX + tw, tabBarY + TAB_BAR_H,
                        blendColors(0xFF0D1020, base | 0xFF000000, 0.50f),
                        blendColors(0xFF0A0C18, base | 0xFF000000, 0.28f));
                // 底部指示条（紧贴面板顶边）
                g.fill(curX, tabBarY + TAB_BAR_H - 2, curX + tw, tabBarY + TAB_BAR_H,
                        base | 0xFF000000);
                g.fill(curX, tabBarY, curX + 1, tabBarY + TAB_BAR_H,
                        (base & 0x00FFFFFF) | 0x88000000);
                g.fill(curX + tw - 1, tabBarY, curX + tw, tabBarY + TAB_BAR_H,
                        (base & 0x00FFFFFF) | 0x88000000);
                g.fill(curX + 1, tabBarY, curX + tw - 1, tabBarY + 1,
                        (base & 0x00FFFFFF) | 0x44000000);
            } else if (hovered) {
                g.fillGradient(curX, tabBarY, curX + tw, tabBarY + TAB_BAR_H,
                        blendColors(0xFF0D1020, base | 0xFF000000, 0.22f),
                        blendColors(0xFF0A0C18, base | 0xFF000000, 0.10f));
                g.fill(curX, tabBarY + TAB_BAR_H - 1, curX + tw, tabBarY + TAB_BAR_H,
                        (base & 0x00FFFFFF) | 0x66000000);
                g.renderOutline(curX, tabBarY, tw, TAB_BAR_H,
                        (base & 0x00FFFFFF) | 0x44000000);
            } else {
                g.fill(curX, tabBarY, curX + tw, tabBarY + TAB_BAR_H, 0x33111828);
                g.renderOutline(curX, tabBarY, tw, TAB_BAR_H, 0x33334466);
            }

            String lbl = Component.translatable(CATEGORIES.get(i).labelKey).getString();
            String truncated = font.plainSubstrByWidth(lbl, tw - 6);
            int textColor = active ? (base | 0xFF000000)
                    : hovered ? 0xFFCCDDFF
                            : 0xFF7788AA;
            g.drawCenteredString(font, truncated,
                    curX + tw / 2,
                    tabBarY + (TAB_BAR_H - font.lineHeight) / 2,
                    textColor);

            curX += tw + TAB_GAP;
        }
    }

    // ── 条目列表 ─────────────────────────────────────────────────────

    private void renderEntryList(GuiGraphics g, int mouseX, int mouseY) {
        // 上下渐变遮罩（须在 scissor 外绘制才能盖住内容）
        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int curY = contentY - scrollOffset;
        for (int i = 0; i < currentEntries.size(); i++) {
            Entry entry = currentEntries.get(i);
            int eh = getEntryH(entry);
            if (curY + eh >= contentY && curY <= contentY + contentH)
                renderEntry(g, entry, i, contentX, curY, contentW, eh, mouseX, mouseY);
            curY += eh + ENTRY_SPACING;
        }

        g.disableScissor();

        // 内容区上下淡出边缘
        g.fillGradient(contentX, contentY,
                contentX + contentW, contentY + 10,
                0xAA000000, 0x00000000);
        g.fillGradient(contentX, contentY + contentH - 10,
                contentX + contentW, contentY + contentH,
                0x00000000, 0xAA000000);
    }

    // ── 单条条目 ─────────────────────────────────────────────────────

    private void renderEntry(GuiGraphics g, Entry entry, int idx,
            int x, int y, int w, int h,
            int mouseX, int mouseY) {
        switch (entry.type) {
            case LABEL -> {
                int col = entry.color | 0xFF000000;
                // 整体淡晕背景
                g.fillGradient(x, y, x + w, y + h,
                        (entry.color & 0x00FFFFFF) | 0x0F000000, 0x00000000);
                // 左侧色条
                g.fill(x, y, x + 3, y + h, col);
                g.fillGradient(x + 3, y, x + 36, y + h,
                        (entry.color & 0x00FFFFFF) | 0x44000000, 0x00000000);
                // 文字
                g.drawString(font, entry.text,
                        x + 9, y + (h - font.lineHeight) / 2, 0xFFFFFF, true);
            }

            case SEPARATOR -> {
                int midY = y + h / 2;
                g.fill(x + 4, midY, x + w - 4, midY + 1, 0xFF1E3060);
                g.fill(x + 4, midY + 1, x + w - 4, midY + 2, 0x22FFFFFF);
            }

            case BUTTON -> {
                boolean hovered = isInRect(mouseX, mouseY, x, y, w, h);

                // 平滑动画进度
                float anim = hoverAnims[idx % hoverAnims.length];
                anim = Mth.lerp(0.22f, anim, hovered ? 1f : 0f);
                hoverAnims[idx % hoverAnims.length] = anim;

                int raw = entry.color;
                int full = raw | 0xFF000000;

                // 外边框
                g.fill(x, y, x + w, y + h,
                        hovered ? blendColors(0xFF334488, full, 0.70f)
                                : blendColors(0xFF1E3060, full, 0.25f));

                // 内背景渐变
                int bgL = hovered ? blendColors(0xFF141828, blendColors(0xFF223380, full, 0.40f), anim)
                        : 0xFF141828;
                int bgR = hovered ? blendColors(0xFF0E1020, blendColors(0xFF162060, full, 0.30f), anim)
                        : 0xFF0E1020;
                g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, bgL, bgR);

                // 顶部亮边
                g.fill(x + 1, y + 1, x + w - 1, y + 2,
                        hovered ? 0x33FFFFFF : 0x10FFFFFF);

                // 左侧色条 + 渐变光晕
                g.fill(x + 1, y + 1, x + 4, y + h - 1, full);
                g.fillGradient(x + 4, y + 1, x + 16, y + h - 1,
                        (raw & 0x00FFFFFF) | 0x44000000, 0x00000000);

                // 文字（居中）
                int textCol = hovered ? 0xFFFFFFFF
                        : blendColors(0xFFAABBCC, 0xFFDDEEFF, anim * 0.5f);
                g.drawCenteredString(font, entry.text,
                        x + w / 2, y + (h - font.lineHeight) / 2, textCol);
                if (hovered) {
                    var tooltipText = Component
                            .translatable("screen.game_manage.click_to_send",
                                    Component.literal("/" + entry.command).withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.YELLOW);
                    g.renderTooltip(font,
                            tooltipText,
                            x + (w - font.width(tooltipText)) / 2, y);
                }
                // 悬停指示箭头（右侧淡入）
                if (anim > 0.05f) {
                    int alpha = (int) (anim * 0xAA);
                    g.drawString(font, Component.literal(">"),
                            x + w - 14,
                            y + (h - font.lineHeight) / 2,
                            (alpha << 24) | 0xFFFFFF, false);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 通用渲染工具（与 RoleIntroduceScreen 风格一致）
    // ══════════════════════════════════════════════════════════════════

    private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xD80C1020, 0xD8101828);
        g.renderOutline(x, y, w, h, 0xFF1E3060);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
    }

    private void renderVScrollbar(GuiGraphics g, int x, int y, int h,
            int scrollOff, int maxScr, int totalContentH,
            int mouseX, int mouseY, boolean dragging) {
        g.fill(x, y, x + SCROLL_W, y + h, 0xFF111828);
        g.fill(x + 1, y + 1, x + SCROLL_W - 1, y + h - 1, 0x55334466);
        if (maxScr <= 0)
            return;

        float ratio = Math.min(1f, (float) h / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) scrollOff / maxScr));
        boolean hl = dragging || isInRect(mouseX, mouseY, x, thumbY, SCROLL_W, thumbH);

        g.fill(x, thumbY, x + SCROLL_W, thumbY + thumbH,
                hl ? 0xFF8899CC : 0xFF556699);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + thumbH - 1,
                hl ? 0xFFAABBEE : 0xFF7788BB);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    // ══════════════════════════════════════════════════════════════════
    // 鼠标事件
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {

            // ── 分类标签 ──────────────────────────────────────────
            for (int i = 0; i < CATEGORIES.size(); i++) {
                if (tabW[i] > 0 && isInRect((int) mx, (int) my,
                        tabX[i], tabBarY, tabW[i], TAB_BAR_H)) {
                    if (selectedCatIdx != i) {
                        selectedCatIdx = i;
                        scrollOffset = 0;
                        refreshEntries();
                        this.minecraft.getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                    }
                    return true;
                }
            }

            // ── 滚动条 ────────────────────────────────────────────
            int sbX = panelX + panelW - PANEL_PAD - SCROLL_W;
            if (isInRect((int) mx, (int) my, sbX, contentY, SCROLL_W, contentH)
                    && maxScroll > 0) {
                isDraggingScroll = true;
                dragStartY = my;
                dragStartOffset = scrollOffset;
                return true;
            }

            // ── 按钮 ──────────────────────────────────────────────
            if (isInRect((int) mx, (int) my, contentX, contentY, contentW, contentH)) {
                int curY = contentY - scrollOffset;
                for (int i = 0; i < currentEntries.size(); i++) {
                    Entry entry = currentEntries.get(i);
                    int eh = getEntryH(entry);
                    if (entry.type == EntryType.BUTTON
                            && isInRect((int) mx, (int) my, contentX, curY, contentW, eh)) {
                        executeCommand(entry.command);
                        this.minecraft.getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                        return true;
                    }
                    curY += eh + ENTRY_SPACING;
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingScroll && maxScroll > 0) {
            int total = Math.max(1, totalEntriesHeight());
            int thumbH = Math.max(SCROLL_MIN_THUMB,
                    (int) (contentH * Math.min(1f, (float) contentH / total)));
            double trackH = contentH - thumbH;
            if (trackH > 0)
                scrollOffset = Mth.clamp(
                        (int) (dragStartOffset + (my - dragStartY) / trackH * maxScroll),
                        0, maxScroll);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (isInRect((int) mx, (int) my, panelX, panelY, panelW, panelH)) {
            scrollOffset = Mth.clamp(
                    (int) (scrollOffset - scrollY * (BUTTON_H + ENTRY_SPACING)),
                    0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ══════════════════════════════════════════════════════════════════
    // 键盘事件（← → 切换分类，与 RoleIntroduceScreen 一致）
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 || keyCode == 262) { // ← →
            int newIdx = Mth.clamp(
                    selectedCatIdx + (keyCode == 263 ? -1 : 1),
                    0, CATEGORIES.size() - 1);
            if (newIdx != selectedCatIdx) {
                selectedCatIdx = newIdx;
                scrollOffset = 0;
                refreshEntries();
                this.minecraft.getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════════════════════════════════════════════════════════
    // 指令执行
    // ══════════════════════════════════════════════════════════════════

    private void executeCommand(String command) {
        if (command == null || command.isBlank())
            return;
        var text = Component
                .translatable("tip.game_manage.send_command",
                        Component.literal("/" + command).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GREEN)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("tip.game_manage.click_to_suggest"))));
        if (minecraft.player != null) {
            minecraft.player
                    .displayClientMessage(text, false);
            minecraft.player.connection.sendCommand(command);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 工具方法（与 RoleIntroduceScreen 保持一致）
    // ══════════════════════════════════════════════════════════════════

    private Category currentCategory() {
        return (selectedCatIdx >= 0 && selectedCatIdx < CATEGORIES.size())
                ? CATEGORIES.get(selectedCatIdx)
                : CATEGORIES.get(0);
    }

    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0f)
            return c1;
        if (t >= 1f)
            return c2;
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}