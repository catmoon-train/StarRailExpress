package net.exmo.sre.sixtyseconds.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes;
import net.exmo.sre.sixtyseconds.network.OpenStationS2CPacket;
import net.exmo.sre.sixtyseconds.network.StationCraftC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 合成站界面（网格版）：顶部分类标签栏 + 搜索框，左侧物品网格（<b>可合成置顶</b>、
 * 缺料变灰、科技未解锁加锁标），hover 显示 介绍/材料 tooltip；右侧详情面板展示
 * 物品介绍、材料 have/need 与 合成×1/×5 按钮。服务端最终校验，客户端置灰只是提示。
 *
 * <p>风格参考 {@code docs/ui_style.md}：深棕渐变面板 + 棕褐描边 + 金色装饰线。</p>
 */
public class StationCraftScreen extends Screen {

    // ── 配色常量（来自 ui_style.md §2.1）─────────────────────────
    private static final int BG_TOP = 0xD81A1008;
    private static final int BG_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECORATION_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int BODY = 0xFFC8B898;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;
    private static final int AQUA = 0xFF5EB7D8;
    private static final int HOVER_BG = 0x22FFFFFF;
    private static final int ROW_SEPARATOR = 0x20FFFFFF;

    // ── 布局常量 ─────────────────────────────────────────────────
    private static final int PAD = 10;
    private static final int CELL = 26;
    private static final int HEADER_H = 24;
    private static final int TAB_H = 16;

    /** 网格条目的三档状态（排序键：可合成置顶 → 缺料 → 科技未解锁）。 */
    private enum EntryState { CRAFTABLE, MISSING, LOCKED }

    private record Entry(SixtySecondsRecipes.Recipe recipe, EntryState state) {
    }

    private final SixtySecondsRecipes.Station station;
    private final BlockPos pos;
    private final Set<String> unlockedTech;
    private final boolean powered;
    private final List<SixtySecondsRecipes.Recipe> recipes;
    private final List<SixtySecondsRecipes.Category> tabs = new ArrayList<>();

    /** null = 全部 */
    private SixtySecondsRecipes.Category activeTab;
    private String search = "";
    private final List<Entry> entries = new ArrayList<>();
    private String selectedId;

    private int scrollRow;
    private int panelX, panelY, panelW, panelH;
    private int gridX, gridTop, gridBottom, gridCols;
    private int detailX, detailW;

    private EditBox searchBox;
    private Button craftBtn, craft5Btn;

    /**
     * 「家里容器」库存快照（物品 → 总数量），<b>由服务端下发</b>
     * （{@code SixtySecondsStationStockS2CPacket}：打开合成台时与每次合成后各发一次）。
     * <p><b>不能在客户端自己扫容器</b>——原版不把箱子/木桶的内容同步给未打开它的客户端，
     * 客户端扫出来恒为空，GUI 会永远显示材料不足（旧实现即此坑）。
     * 静态字段：收包时界面可能尚未构造完（开界面包与库存包同帧先后到达）。
     */
    private static Map<net.minecraft.world.item.Item, Integer> homeStock = new HashMap<>();

    /** 收到服务端库存快照（客户端网络线程 → 主线程调用）。 */
    public static void updateHomeStock(Map<net.minecraft.world.item.Item, Integer> stock) {
        homeStock = stock;
    }

    public StationCraftScreen(OpenStationS2CPacket data) {
        super(Component.translatable(
                SixtySecondsRecipes.Station.values()[data.station()].translationKey()));
        this.station = SixtySecondsRecipes.Station.values()[data.station()];
        this.pos = data.pos();
        this.unlockedTech = new HashSet<>(Arrays.asList(data.unlockedTech()));
        this.powered = data.powered();
        this.recipes = SixtySecondsRecipes.forStation(station);
        // 本站出现过的分类才生成标签（按枚举声明序）
        Set<SixtySecondsRecipes.Category> present = new LinkedHashSet<>();
        for (SixtySecondsRecipes.Category c : SixtySecondsRecipes.Category.values()) {
            for (SixtySecondsRecipes.Recipe r : recipes) {
                if (SixtySecondsRecipes.categoryOf(r) == c) {
                    present.add(c);
                    break;
                }
            }
        }
        tabs.addAll(present);
    }

    // ── 布局计算 ──────────────────────────────────────────────────

    private void computeLayout() {
        panelW = Mth.clamp((int) (this.width * 0.86f), 480, 760);
        panelH = Mth.clamp((int) (this.height * 0.82f), 300, 480);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        detailW = Mth.clamp((int) (panelW * 0.36f), 180, 250);
        detailX = panelX + panelW - PAD - detailW;

        gridX = panelX + PAD;
        gridTop = panelY + HEADER_H + TAB_H + 8;
        gridBottom = panelY + panelH - PAD;
        gridCols = Math.max(1, (detailX - 8 - gridX - 6) / CELL); // 右侧留滚动条位
    }

    private int visibleRowCount() {
        return Math.max(1, (gridBottom - gridTop) / CELL);
    }

    private int totalRows() {
        return (entries.size() + gridCols - 1) / gridCols;
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - visibleRowCount());
    }

    // ── 初始化 ────────────────────────────────────────────────────

    @Override
    protected void init() {
        computeLayout();

        // 家里容器的库存由服务端在开界面时一并下发（见 homeStock），无需客户端扫描

        // 搜索框：标签栏右侧
        int searchW = Mth.clamp(panelW / 5, 90, 130);
        searchBox = new EditBox(this.font, panelX + panelW - PAD - searchW, panelY + HEADER_H + 1,
                searchW, TAB_H - 2,
                Component.translatable("message.noellesroles.sixty_seconds.craft_search"));
        searchBox.setHint(Component.translatable("message.noellesroles.sixty_seconds.craft_search")
                .withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setBordered(false);
        searchBox.setValue(search);
        searchBox.setResponder(text -> {
            search = text;
            scrollRow = 0;
            rebuildEntries();
        });
        addRenderableWidget(searchBox);

        // 关闭按钮：右上角
        addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(panelX + panelW - 18, panelY + 3, 15, 14).build());

        // 合成按钮：详情面板底部
        int btnY = panelY + panelH - PAD - 20;
        int half = (detailW - 4) / 2;
        craftBtn = Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.craft_btn"),
                b -> sendCraft(1)).bounds(detailX, btnY, half, 20).build();
        craft5Btn = Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.craft_x5"),
                b -> sendCraft(5)).bounds(detailX + half + 4, btnY, half, 20).build();
        addRenderableWidget(craftBtn);
        addRenderableWidget(craft5Btn);

        rebuildEntries();
    }

    private void sendCraft(int count) {
        SixtySecondsRecipes.Recipe recipe = selected();
        if (recipe != null) {
            ClientPlayNetworking.send(new StationCraftC2SPacket(recipe.id(), pos, count));
        }
    }

    private SixtySecondsRecipes.Recipe selected() {
        return selectedId == null ? null : SixtySecondsRecipes.byId(selectedId);
    }

    // ── 条目构建：过滤（标签+搜索）→ 状态 → 可合成置顶 ─────────────

    private void rebuildEntries() {
        entries.clear();
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        for (SixtySecondsRecipes.Recipe recipe : recipes) {
            if (activeTab != null && SixtySecondsRecipes.categoryOf(recipe) != activeTab) {
                continue;
            }
            if (!query.isEmpty()) {
                String name = SixtySecondsRecipes.outputStack(recipe).getHoverName()
                        .getString().toLowerCase(Locale.ROOT);
                if (!name.contains(query)) {
                    continue;
                }
            }
            entries.add(new Entry(recipe, stateOf(recipe)));
        }
        // 稳定排序：CRAFTABLE < MISSING < LOCKED，同档保持配方表顺序
        entries.sort(java.util.Comparator.comparingInt(e -> e.state().ordinal()));
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
    }

    private EntryState stateOf(SixtySecondsRecipes.Recipe recipe) {
        if (!unlockedTech.contains(recipe.techId())) {
            return EntryState.LOCKED;
        }
        if (recipe.needsPower() && !powered) {
            return EntryState.MISSING;
        }
        for (SixtySecondsRecipes.Ingredient input : recipe.inputs()) {
            if (countIngredient(input) < input.count()) {
                return EntryState.MISSING;
            }
        }
        return EntryState.CRAFTABLE;
    }

    /** 背包内可充当该配料的物品总数（「任意 X」组配料合计全部候选）。 */
    private int countIngredient(SixtySecondsRecipes.Ingredient input) {
        int total = 0;
        for (net.minecraft.world.item.Item candidate : input.items()) {
            total += countInInventory(candidate);
        }
        return total;
    }

    /** 背包变化（合成扣料/捡东西）会改变置灰与排序，每 tick 重算一次（配方量小，开销可忽略）。
     *  同时每隔 20 tick 刷新附近容器缓存。 */
    @Override
    public void tick() {
        super.tick();
        // 背包变化每 tick 重算；家里容器库存由服务端推送（开界面/每次合成后）刷新
        rebuildEntries();
    }

    // ── 鼠标事件 ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 分类标签
        int tabX = panelX + PAD;
        int tabY = panelY + HEADER_H;
        for (int i = -1; i < tabs.size(); i++) {
            SixtySecondsRecipes.Category tab = i < 0 ? null : tabs.get(i);
            int w = tabWidth(tab);
            if (mouseX >= tabX && mouseX < tabX + w && mouseY >= tabY && mouseY < tabY + TAB_H) {
                if (activeTab != tab) {
                    activeTab = tab;
                    scrollRow = 0;
                    rebuildEntries();
                    playClick();
                }
                return true;
            }
            tabX += w + 4;
        }
        // 网格
        int index = gridIndexAt(mouseX, mouseY);
        if (index >= 0 && index < entries.size()) {
            Entry entry = entries.get(index);
            String id = entry.recipe().id();
            if (!id.equals(selectedId)) {
                selectedId = id;
                playClick();
            } else if (hasShiftDown() && entry.state() == EntryState.CRAFTABLE) {
                sendCraft(1); // 已选中时 Shift+点击 快速合成
            }
            return true;
        }
        return false;
    }

    /** 屏幕坐标 → 网格条目下标；不在网格内返回 -1。 */
    private int gridIndexAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseX >= gridX + gridCols * CELL
                || mouseY < gridTop || mouseY >= gridBottom) {
            return -1;
        }
        int col = (int) ((mouseX - gridX) / CELL);
        int row = scrollRow + (int) ((mouseY - gridTop) / CELL);
        return row * gridCols + col;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int next = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
        if (next != scrollRow) {
            scrollRow = next;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ── 绘制 ──────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        computeLayout();
        // 深棕渐变面板 + 描边 + 装饰线（§3 范式）
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, BG_TOP, BG_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, DECORATION_LINE);
        // 详情分栏底色
        g.fill(detailX - 4, gridTop - TAB_H - 8, detailX - 3, panelY + panelH - PAD, ROW_SEPARATOR);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        SixtySecondsRecipes.Recipe sel = selected();
        boolean canCraftSel = false;
        if (sel != null) {
            canCraftSel = stateOf(sel) == EntryState.CRAFTABLE;
        }
        craftBtn.visible = craft5Btn.visible = sel != null;
        craftBtn.active = craft5Btn.active = canCraftSel;

        super.render(g, mouseX, mouseY, partialTick);
        drawHeader(g);
        drawTabs(g, mouseX, mouseY);
        drawGrid(g, mouseX, mouseY);
        drawDetail(g, sel);
        drawGridTooltip(g, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics g) {
        g.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                panelX + PAD, panelY + 8, GOLD);
        Component powerLine = Component.translatable(powered
                ? "message.noellesroles.sixty_seconds.station_powered"
                : "message.noellesroles.sixty_seconds.station_unpowered");
        int w = this.font.width(powerLine);
        g.drawString(this.font, powerLine, panelX + panelW - PAD - w - 14, panelY + 8,
                powered ? GREEN : MUTED);
        g.fill(panelX + PAD, panelY + HEADER_H - 2, panelX + panelW - PAD,
                panelY + HEADER_H - 1, ROW_SEPARATOR);
    }

    private int tabWidth(SixtySecondsRecipes.Category tab) {
        return this.font.width(tabLabel(tab)) + 10;
    }

    private Component tabLabel(SixtySecondsRecipes.Category tab) {
        return tab == null
                ? Component.translatable("category.noellesroles.sixty_seconds.all")
                : Component.translatable(tab.translationKey());
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tabX = panelX + PAD;
        int tabY = panelY + HEADER_H;
        for (int i = -1; i < tabs.size(); i++) {
            SixtySecondsRecipes.Category tab = i < 0 ? null : tabs.get(i);
            int w = tabWidth(tab);
            boolean active = activeTab == tab;
            boolean hover = mouseX >= tabX && mouseX < tabX + w
                    && mouseY >= tabY && mouseY < tabY + TAB_H;
            int color = tab == null ? GOLD : tab.color;
            if (active) {
                g.fill(tabX, tabY, tabX + w, tabY + TAB_H, blendColors(0xFF1A1008, color, 0.55f));
            } else if (hover) {
                g.fill(tabX, tabY, tabX + w, tabY + TAB_H, blendColors(0xFF1A1008, color, 0.25f));
            }
            g.drawString(this.font, tabLabel(tab), tabX + 5, tabY + 4,
                    active ? TEXT : (hover ? BODY : MUTED));
            if (active) {
                g.fill(tabX, tabY + TAB_H - 1, tabX + w, tabY + TAB_H, color);
            }
            tabX += w + 4;
        }
        // 搜索框底线
        g.fill(searchBox.getX(), searchBox.getY() + searchBox.getHeight(),
                searchBox.getX() + searchBox.getWidth(),
                searchBox.getY() + searchBox.getHeight() + 1, 0x44FFE8C0);
    }

    private void drawGrid(GuiGraphics g, int mouseX, int mouseY) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        RenderSystem.enableScissor(
                (int) (gridX * scale),
                (int) (Minecraft.getInstance().getWindow().getHeight() - gridBottom * scale),
                (int) (gridCols * CELL * scale),
                (int) ((gridBottom - gridTop) * scale));

        int hovering = gridIndexAt(mouseX, mouseY);
        int visibleRows = visibleRowCount();
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < gridCols; col++) {
                int index = (scrollRow + row) * gridCols + col;
                if (index >= entries.size()) {
                    break;
                }
                Entry entry = entries.get(index);
                int x = gridX + col * CELL;
                int y = gridTop + row * CELL;
                drawCell(g, entry, x, y, index == hovering);
            }
        }
        RenderSystem.disableScissor();
        drawScrollBar(g);

        if (entries.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("message.noellesroles.sixty_seconds.craft_empty"),
                    gridX + gridCols * CELL / 2, gridTop + 24, MUTED);
        }
    }

    private void drawCell(GuiGraphics g, Entry entry, int x, int y, boolean hover) {
        boolean selectedCell = entry.recipe().id().equals(selectedId);
        int bg;
        int edge;
        switch (entry.state()) {
            case CRAFTABLE -> {
                bg = 0x2ED4AF37;
                edge = 0xAAD4AF37;
            }
            case MISSING -> {
                bg = 0x16FFFFFF;
                edge = 0x335A4530;
            }
            default -> {
                bg = 0x28000000;
                edge = 0x33443322;
            }
        }
        g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, bg);
        if (hover) {
            g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, HOVER_BG);
        }
        g.renderOutline(x + 1, y + 1, CELL - 2, CELL - 2, selectedCell ? 0xFFFFF4DC : edge);
        if (selectedCell) {
            g.renderOutline(x, y, CELL, CELL, GOLD);
        }

        ItemStack output = SixtySecondsRecipes.outputStack(entry.recipe());
        g.renderFakeItem(output, x + 5, y + 5);
        if (entry.recipe().outputCount() > 1) {
            g.drawString(this.font, "" + entry.recipe().outputCount(),
                    x + CELL - 4 - this.font.width("" + entry.recipe().outputCount()),
                    y + CELL - 10, TEXT);
        }
        // 需供电：右上角蓝点
        if (entry.recipe().needsPower()) {
            g.fill(x + CELL - 5, y + 2, x + CELL - 2, y + 5, powered ? AQUA : RED);
        }
        if (entry.state() == EntryState.LOCKED) {
            // 暗化 + 迷你挂锁（右下角）
            g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x90140E08);
            g.fill(x + CELL - 9, y + CELL - 8, x + CELL - 3, y + CELL - 3, 0xFF9E8B6E); // 锁体
            g.renderOutline(x + CELL - 8, y + CELL - 11, 4, 4, 0xFF9E8B6E);             // 锁梁
        }
    }

    private void drawScrollBar(GuiGraphics g) {
        int max = maxScrollRow();
        if (max <= 0) {
            return;
        }
        int trackX = gridX + gridCols * CELL + 2;
        int trackH = gridBottom - gridTop;
        g.fill(trackX, gridTop, trackX + 3, gridBottom, 0x20FFFFFF);
        int thumbH = Math.max(18, trackH * visibleRowCount() / totalRows());
        int thumbY = gridTop + (trackH - thumbH) * scrollRow / max;
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, GOLD);
    }

    // ── 右侧详情面板 ──────────────────────────────────────────────

    private void drawDetail(GuiGraphics g, SixtySecondsRecipes.Recipe recipe) {
        int x = detailX;
        int top = gridTop - TAB_H - 4;
        if (recipe == null) {
            int hintY = gridTop + 20;
            for (var line : this.font.split(Component.translatable(
                    "message.noellesroles.sixty_seconds.craft_select_hint"), detailW)) {
                g.drawString(this.font, line, x, hintY, MUTED);
                hintY += this.font.lineHeight + 1;
            }
            return;
        }
        ItemStack output = SixtySecondsRecipes.outputStack(recipe);

        // 大图标（2x）+ 名称
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, top, 0);
        pose.scale(2f, 2f, 1f);
        g.renderFakeItem(output, 0, 0);
        pose.popPose();

        MutableComponent name = output.getHoverName().copy().withStyle(ChatFormatting.BOLD);
        int textX = x + 38;
        int y = top + 4;
        for (var line : this.font.split(name, detailW - 38)) {
            g.drawString(this.font, line, textX, y, TEXT);
            y += this.font.lineHeight + 1;
        }
        if (recipe.outputCount() > 1) {
            g.drawString(this.font, "×" + recipe.outputCount(), textX, y, GOLD);
        }
        y = Math.max(y, top + 34) + 2;

        // 介绍（与物品 tooltip 相同的翻译键）
        String descKey = descriptionKey(output);
        if (descKey != null) {
            for (var line : this.font.split(
                    Component.literal(I18n.get(descKey)), detailW)) {
                if (y > panelY + panelH - 116) { // 给材料区留空间
                    break;
                }
                g.drawString(this.font, line, x, y, BODY);
                y += this.font.lineHeight + 1;
            }
        }
        y += 3;
        g.fill(x, y, x + detailW, y + 1, ROW_SEPARATOR);
        y += 5;

        // 材料需求
        g.drawString(this.font, Component.translatable(
                        "message.noellesroles.sixty_seconds.craft_materials")
                .withStyle(ChatFormatting.BOLD), x, y, GOLD);
        y += this.font.lineHeight + 3;
        for (SixtySecondsRecipes.Ingredient input : recipe.inputs()) {
            int have = countIngredient(input);
            boolean enough = have >= input.count();
            g.renderFakeItem(new ItemStack(input.item()), x, y - 4);
            g.drawString(this.font, input.displayName(), x + 20, y, enough ? TEXT : MUTED);
            String count = have + "/" + input.count();
            g.drawString(this.font, count, x + detailW - this.font.width(count), y,
                    enough ? GREEN : RED);
            y += 15;
        }

        // 条件行：供电 / 科技
        if (recipe.needsPower()) {
            g.drawString(this.font,
                    Component.translatable("message.noellesroles.sixty_seconds.needs_power"),
                    x, y, powered ? AQUA : RED);
            y += this.font.lineHeight + 2;
        }
        if (!unlockedTech.contains(recipe.techId())) {
            for (var line : this.font.split(Component.translatable(
                            "message.noellesroles.sixty_seconds.tech_requires",
                            Component.translatable("tech.noellesroles.sixty_seconds." + recipe.techId())),
                    detailW)) {
                g.drawString(this.font, line, x, y, RED);
                y += this.font.lineHeight + 1;
            }
        }
    }

    // ── 网格 tooltip：名称 + 介绍 + 材料 ──────────────────────────

    private void drawGridTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int index = gridIndexAt(mouseX, mouseY);
        if (index < 0 || index >= entries.size()) {
            return;
        }
        Entry entry = entries.get(index);
        SixtySecondsRecipes.Recipe recipe = entry.recipe();
        ItemStack output = SixtySecondsRecipes.outputStack(recipe);

        List<Component> lines = new ArrayList<>();
        MutableComponent name = output.getHoverName().copy();
        if (recipe.outputCount() > 1) {
            name.append(Component.literal(" ×" + recipe.outputCount()).withStyle(ChatFormatting.GOLD));
        }
        lines.add(name);
        SixtySecondsRecipes.Category category = SixtySecondsRecipes.categoryOf(recipe);
        lines.add(Component.translatable(category.translationKey())
                .withStyle(style -> style.withColor(category.color & 0xFFFFFF)));
        String descKey = descriptionKey(output);
        if (descKey != null) {
            for (var seg : this.font.getSplitter().splitLines(
                    I18n.get(descKey), 200, net.minecraft.network.chat.Style.EMPTY)) {
                lines.add(Component.literal(seg.getString()).withStyle(ChatFormatting.GRAY));
            }
        }
        lines.add(Component.empty());
        for (SixtySecondsRecipes.Ingredient input : recipe.inputs()) {
            int have = countIngredient(input);
            boolean enough = have >= input.count();
            lines.add(Component.literal("• ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(input.displayName().copy()
                            .withStyle(enough ? ChatFormatting.WHITE : ChatFormatting.RED))
                    .append(Component.literal("  " + have + "/" + input.count())
                            .withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
        if (recipe.needsPower()) {
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.needs_power")
                    .withStyle(powered ? ChatFormatting.AQUA : ChatFormatting.RED));
        }
        if (entry.state() == EntryState.LOCKED) {
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.tech_requires",
                            Component.translatable("tech.noellesroles.sixty_seconds." + recipe.techId()))
                    .withStyle(ChatFormatting.RED));
        }
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /** 物品介绍翻译键（与 {@code SixtySecondsTooltips} 一致）；未定义返回 null。 */
    private static String descriptionKey(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"noellesroles".equals(id.getNamespace()) || !id.getPath().startsWith("sixty_seconds_")) {
            return null;
        }
        String key = "tooltip.noellesroles." + id.getPath();
        return I18n.exists(key) ? key : null;
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    /**
     * 某物品的可用总数 = <b>自己背包</b>（客户端本地已知）+ <b>家里容器</b>（服务端下发的 {@link #homeStock}）。
     * 与服务端 {@code SixtySecondsStations.countMatching} 的取料范围一致，故 GUI 显示与实际可合成结果对得上。
     */
    private int countInInventory(net.minecraft.world.item.Item item) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return 0;
        }
        int have = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.is(item)) {
                have += stack.getCount();
            }
        }
        have += homeStock.getOrDefault(item, 0);
        return have;
    }

    private static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
