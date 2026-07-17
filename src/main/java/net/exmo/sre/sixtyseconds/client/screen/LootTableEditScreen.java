package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.exmo.sre.sixtyseconds.network.LootTableSaveC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * loot 表编辑 GUI（复古车票风，遵循 docs/ui_style.md）：
 * 顶部类别标签页切换；左侧为当前类别的条目列表（物品图标 + 名称 + 数量/权重，滚动 + hover +
 * 选中态），选中后底部编辑器可改 物品ID/数量/权重；右侧为<b>背包物品选择器</b>——点击背包里的
 * 物品即把它加进当前类别（数量取手上堆叠数、权重 1.0）。「保存」经 {@link LootTableSaveC2SPacket}
 * 上传服务端落盘。空类别在保存时自动移除。
 */
public class LootTableEditScreen extends Screen {
    // ── ui_style.md 色板 ─────────────────────────────────────────────
    private static final int BG_TOP = 0xD81A1008;
    private static final int BG_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int ACCENT_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int RED = 0xFFE06B65;
    private static final int GREEN = 0xFF72C17B;
    private static final int HOVER_BG = 0x22FFFFFF;
    private static final int ROW_SEP = 0x20FFFFFF;
    private static final int IDLE_BORDER = 0xFF5A4530;

    private static final int PAD = 8;
    private static final int TAB_H = 18;
    private static final int TAB_DEL_W = 9;      // 类别标签右侧删除「×」预留槽宽
    private static final int TAB_ARROW_W = 10;   // 标签栏左右箭头宽度
    private static final int ROW_H = 30;
    private static final int CELL = 20;          // 背包格尺寸
    private static final int MAX_ENTRIES_PER_CATEGORY = 64;

    /** 类别 → 条目（可变副本，保存时重建协议对象）。 */
    private final LinkedHashMap<String, List<RowData>> categories = new LinkedHashMap<>();
    private String currentCategory;
    private RowData selected;

    private int tabScroll;    // 标签页水平滚动（像素）
    private int listScroll;   // 条目列表滚动（像素）
    private int gridScroll;   // 背包网格滚动（行）
    private int openTicks;    // 入场动画计时

    private EditBox idBox;
    private EditBox countBox;
    private EditBox weightBox;
    private EditBox newCatBox;
    private boolean updatingEditors; // setValue 触发 responder 的回写保护

    // 每帧计算的布局缓存（render/mouse 共用）
    private int panelX, panelY, panelW, panelH;
    private int leftX, leftW, rightX, rightW, contentTop, listBottom, detailTop;

    public LootTableEditScreen(SixtySecondsLootTable table) {
        super(Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.title"));
        for (Map.Entry<String, List<SixtySecondsLootTable.Entry>> e : table.categories.entrySet()) {
            List<RowData> rows = new ArrayList<>();
            if (e.getValue() != null) {
                for (SixtySecondsLootTable.Entry entry : e.getValue()) {
                    rows.add(new RowData(entry.itemId, entry.count, entry.weight));
                }
            }
            categories.put(e.getKey(), rows);
        }
        if (categories.isEmpty()) {
            categories.put("tool", new ArrayList<>());
        }
        currentCategory = categories.keySet().iterator().next();
    }

    // ── 布局 ─────────────────────────────────────────────────────────

    private void computeLayout() {
        panelW = (int) Math.min(700, this.width * 0.9F);
        panelH = (int) Mth.clamp(this.height * 0.78F, 230, 360);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        contentTop = panelY + 22 + TAB_H + 6;
        leftX = panelX + PAD;
        leftW = (int) ((panelW - PAD * 3) * 0.58F);
        rightX = leftX + leftW + PAD;
        rightW = panelX + panelW - PAD - rightX;
        detailTop = panelY + panelH - 28 - 46;
        listBottom = detailTop - 4;
    }

    @Override
    protected void init() {
        computeLayout();
        int boxY = detailTop + 12;
        idBox = editor(leftX, boxY, leftW - 100, 128);
        countBox = editor(leftX + leftW - 96, boxY, 42, 8);
        weightBox = editor(leftX + leftW - 50, boxY, 50, 12);
        idBox.setResponder(v -> {
            if (!updatingEditors && selected != null) {
                selected.itemId = v.trim();
            }
        });
        countBox.setResponder(v -> {
            if (!updatingEditors && selected != null) {
                selected.count = Math.max(1, parseInt(v, selected.count));
            }
        });
        weightBox.setResponder(v -> {
            if (!updatingEditors && selected != null) {
                selected.weight = Math.max(0, parseFloat(v, selected.weight));
            }
        });
        newCatBox = editor(panelX + panelW - PAD - 96, panelY + 22, 96, 24);
        newCatBox.setHint(Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.new_category")
                .withStyle(ChatFormatting.DARK_GRAY));
        refreshEditors();
    }

    private EditBox editor(int x, int y, int w, int maxLen) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.empty());
        box.setMaxLength(maxLen);
        box.setBordered(true);
        addRenderableWidget(box);
        return box;
    }

    /** 选中项变化时把值刷进底部编辑器（带回写保护）。 */
    private void refreshEditors() {
        updatingEditors = true;
        boolean has = selected != null;
        idBox.setVisible(has);
        countBox.setVisible(has);
        weightBox.setVisible(has);
        if (has) {
            idBox.setValue(selected.itemId);
            countBox.setValue(Integer.toString(selected.count));
            weightBox.setValue(trimFloat(selected.weight));
        }
        updatingEditors = false;
    }

    // ── 渲染 ─────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        openTicks++;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        computeLayout();
        float open = easeOutCubic(Mth.clamp((openTicks + partialTick) / 5f, 0f, 1f));
        g.fill(0, 0, this.width, this.height, ((int) (0x66 * open) << 24));
        // 面板三步范式：渐变 + 描边 + 顶部装饰线
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, BG_TOP, BG_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, ACCENT_LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float open = easeOutCubic(Mth.clamp((openTicks + partialTick) / 5f, 0f, 1f));

        // 标题
        g.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                panelX + PAD, panelY + 8, GOLD, false);

        drawTabs(g, mouseX, mouseY);
        drawEntryList(g, mouseX, mouseY, open);
        drawInventoryGrid(g, mouseX, mouseY);
        drawDetailEditor(g);
        drawBottomButtons(g, mouseX, mouseY);
    }

    /** 顶部类别标签页（可水平滚动）+ 新类别输入框。 */
    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        int startX = panelX + PAD;
        int y = panelY + 22;
        int tabAreaRight = newCatBox.getX() - 4;

        // 计算所有标签总宽度
        int totalW = 0;
        for (String cat : categories.keySet()) {
            totalW += tabWidth(cat) + 4;
        }
        if (totalW > 0) totalW -= 4;

        int rawAreaW = tabAreaRight - startX;
        boolean scrollable = totalW > rawAreaW;

        int scrollLeft = startX;
        int scrollRight = tabAreaRight;
        int actualAreaW = rawAreaW;

        // 可滚动时左右各留箭头位
        if (scrollable) {
            scrollLeft += TAB_ARROW_W;
            scrollRight -= TAB_ARROW_W;
            actualAreaW = scrollRight - scrollLeft;
        }

        int maxTabScroll = Math.max(0, totalW - actualAreaW);
        tabScroll = Mth.clamp(tabScroll, 0, maxTabScroll);

        g.enableScissor(scrollLeft, y, scrollRight, y + TAB_H);

        int drawX = scrollLeft - tabScroll;
        for (String cat : categories.keySet()) {
            int w = tabWidth(cat);
            // 跳过完全在可视区域外的标签
            if (drawX + w <= scrollLeft) {
                drawX += w + 4;
                continue;
            }
            if (drawX >= scrollRight) {
                break;
            }
            boolean active = cat.equals(currentCategory);
            boolean hover = isInRect(mouseX, mouseY, drawX, y, w, TAB_H);
            int bg = active ? blendColors(0xFF1A1008, 0xFFC9A84C, 0.45F)
                    : hover ? blendColors(0xFF1A1008, 0xFFC9A84C, 0.20F) : 0x331A1008;
            g.fill(drawX, y, drawX + w, y + TAB_H, bg);
            g.renderOutline(drawX, y, w, TAB_H, active ? GOLD : IDLE_BORDER);
            int labelW = w - (canDeleteCategory() ? TAB_DEL_W : 0);
            g.drawCenteredString(this.font, cat, drawX + labelW / 2, y + 5, active ? TEXT : MUTED);
            if (canDeleteCategory()) {
                boolean xHover = isInRect(mouseX, mouseY, drawX + w - TAB_DEL_W, y, TAB_DEL_W, TAB_H);
                g.drawString(this.font, "×", drawX + w - TAB_DEL_W + 2, y + 5, xHover ? RED : MUTED, false);
            }
            drawX += w + 4;
        }

        g.disableScissor();

        // 可滚动时绘制左右箭头指示
        if (scrollable) {
            // 左箭头
            boolean leftHover = isInRect(mouseX, mouseY, startX, y, TAB_ARROW_W, TAB_H);
            int leftColor = tabScroll > 0 ? (leftHover ? GOLD : TEXT) : MUTED;
            g.drawString(this.font, "<", startX + 2, y + 5, leftColor, false);
            // 右箭头
            int arrowRX = tabAreaRight - TAB_ARROW_W;
            boolean rightHover = isInRect(mouseX, mouseY, arrowRX, y, TAB_ARROW_W, TAB_H);
            int rightColor = tabScroll < maxTabScroll ? (rightHover ? GOLD : TEXT) : MUTED;
            g.drawString(this.font, ">", arrowRX + 3, y + 5, rightColor, false);
        }

        // "+"按钮：把 newCatBox 内容作为新类别（始终固定）
        int plusX = newCatBox.getX() + newCatBox.getWidth() + 2;
        boolean plusHover = isInRect(mouseX, mouseY, plusX, y, 18, TAB_H);
        g.fill(plusX, y, plusX + 18, y + TAB_H, plusHover ? HOVER_BG : 0x331A1008);
        g.renderOutline(plusX, y, 18, TAB_H, plusHover ? GOLD : IDLE_BORDER);
        g.drawCenteredString(this.font, "+", plusX + 9, y + 5, GREEN);
    }

    private int tabWidth(String cat) {
        return this.font.width(cat) + 14 + (canDeleteCategory() ? TAB_DEL_W : 0);
    }

    /** 至少保留一个类别：仅当类别数 > 1 时允许删除、并预留标签删除槽。 */
    private boolean canDeleteCategory() {
        return categories.size() > 1;
    }

    /** 左侧条目列表（scissor 滚动 + 逐行入场滑入）。 */
    private void drawEntryList(GuiGraphics g, int mouseX, int mouseY, float open) {
        List<RowData> rows = rows();
        g.renderOutline(leftX - 1, contentTop - 1, leftW + 2, listBottom - contentTop + 2, IDLE_BORDER);
        if (rows.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.entries_empty"),
                    leftX + leftW / 2, (contentTop + listBottom) / 2 - 4, MUTED);
            return;
        }
        int maxScroll = Math.max(0, rows.size() * ROW_H - (listBottom - contentTop));
        listScroll = Mth.clamp(listScroll, 0, maxScroll);
        g.enableScissor(leftX, contentTop, leftX + leftW, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            int y = contentTop + i * ROW_H - listScroll;
            if (y + ROW_H < contentTop || y > listBottom) {
                continue;
            }
            RowData row = rows.get(i);
            // 入场：逐行延迟滑入（0.03s/行，≤0.4s 总时长）
            float rowT = easeOutCubic(Mth.clamp((openTicks - i * 0.6f) / 5f, 0f, 1f));
            int x = leftX - (int) ((1f - rowT) * 22f);
            boolean hover = isInRect(mouseX, mouseY, leftX, Math.max(y, contentTop),
                    leftW, Math.min(y + ROW_H, listBottom) - Math.max(y, contentTop));
            if (row == selected) {
                g.fillGradient(leftX, y, leftX + leftW, y + ROW_H,
                        blendColors(0xFF1A1008, 0xFFC9A84C, 0.32F),
                        blendColors(0xFF120A04, 0xFFC9A84C, 0.18F));
            } else if (hover) {
                g.fill(leftX, y, leftX + leftW, y + ROW_H, HOVER_BG);
            }
            ItemStack icon = row.icon();
            boolean valid = !icon.isEmpty();
            if (valid) {
                g.renderItem(icon, x + 4, y + 6);
            } else {
                g.drawString(this.font, "?", x + 9, y + 10, RED, false);
            }
            Component name = valid ? icon.getHoverName()
                    : Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.unknown_item");
            g.drawString(this.font, name, x + 26, y + 5, valid ? TEXT : RED, false);
            g.drawString(this.font, row.itemId, x + 26, y + 17, MUTED, false);
            String meta = "×" + row.count + "  w" + trimFloat(row.weight);
            g.drawString(this.font, meta, leftX + leftW - this.font.width(meta) - 18, y + 10,
                    0xFFC8B898, false);
            // hover 时行尾的删除 ×
            if (hover) {
                boolean xHover = isInRect(mouseX, mouseY, leftX + leftW - 14, y + 8, 12, 12);
                g.drawString(this.font, "×", leftX + leftW - 11, y + 10, xHover ? RED : MUTED, false);
            }
            g.fill(leftX + 2, y + ROW_H - 1, leftX + leftW - 2, y + ROW_H, ROW_SEP);
        }
        g.disableScissor();
        drawScrollbar(g, leftX + leftW - 3, contentTop, listBottom,
                rows.size() * ROW_H, listScroll);
    }

    /** 右侧背包物品选择器：点击加入当前类别。 */
    private void drawInventoryGrid(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.inventory"),
                rightX, contentTop - 1, GOLD, false);
        int gridTop = contentTop + 11;
        int gridBottom = panelY + panelH - 28;
        g.renderOutline(rightX - 1, gridTop - 1, rightW + 2, gridBottom - gridTop + 2, IDLE_BORDER);

        List<ItemStack> items = inventoryItems();
        if (items.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.inventory_empty"),
                    rightX + rightW / 2, (gridTop + gridBottom) / 2 - 4, MUTED);
            return;
        }
        int cols = Math.max(1, (rightW - 4) / CELL);
        int totalRows = (items.size() + cols - 1) / cols;
        int visibleRows = Math.max(1, (gridBottom - gridTop - 4) / CELL);
        gridScroll = Mth.clamp(gridScroll, 0, Math.max(0, totalRows - visibleRows));

        ItemStack hovered = ItemStack.EMPTY;
        g.enableScissor(rightX, gridTop, rightX + rightW, gridBottom);
        for (int i = 0; i < items.size(); i++) {
            int r = i / cols - gridScroll;
            if (r < 0 || r >= visibleRows) {
                continue;
            }
            int cx = rightX + 2 + (i % cols) * CELL;
            int cy = gridTop + 2 + r * CELL;
            boolean hover = isInRect(mouseX, mouseY, cx, cy, CELL, CELL);
            if (hover) {
                g.fill(cx, cy, cx + CELL, cy + CELL, HOVER_BG);
                g.renderOutline(cx, cy, CELL, CELL, GOLD);
                hovered = items.get(i);
            }
            g.renderItem(items.get(i), cx + 2, cy + 2);
            g.renderItemDecorations(this.font, items.get(i), cx + 2, cy + 2);
        }
        g.disableScissor();
        drawScrollbar(g, rightX + rightW - 3, gridTop, gridBottom, totalRows * CELL, gridScroll * CELL);
        // 底部提示 + hover 物品 tooltip
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.hint"),
                rightX, gridBottom + 4, MUTED, false);
        if (!hovered.isEmpty()) {
            g.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    /** 底部选中条目编辑器（物品ID / 数量 / 权重）。 */
    private void drawDetailEditor(GuiGraphics g) {
        if (selected == null) {
            g.drawString(this.font,
                    Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.select_hint"),
                    leftX, detailTop + 14, MUTED, false);
            return;
        }
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.item_id"),
                idBox.getX(), detailTop + 1, MUTED, false);
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.count"),
                countBox.getX(), detailTop + 1, MUTED, false);
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.weight"),
                weightBox.getX(), detailTop + 1, MUTED, false);
    }

    /** 底部「保存 / 完成」按钮（自绘 hover 态）。 */
    private void drawBottomButtons(GuiGraphics g, int mouseX, int mouseY) {
        int y = panelY + panelH - 24;
        int saveX = saveButtonX();
        boolean saveHover = isInRect(mouseX, mouseY, saveX, y, 64, 18);
        g.fill(saveX, y, saveX + 64, y + 18,
                saveHover ? blendColors(0xFF1A1008, GOLD, 0.35F) : 0x551A1008);
        g.renderOutline(saveX, y, 64, 18, saveHover ? GOLD : BORDER);
        g.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.save"),
                saveX + 32, y + 5, GOLD);

        int doneX = saveX + 72;
        boolean doneHover = isInRect(mouseX, mouseY, doneX, y, 64, 18);
        g.fill(doneX, y, doneX + 64, y + 18, doneHover ? HOVER_BG : 0x551A1008);
        g.renderOutline(doneX, y, 64, 18, doneHover ? GOLD : IDLE_BORDER);
        g.drawCenteredString(this.font, Component.translatable("gui.done"), doneX + 32, y + 5, TEXT);
    }

    private int saveButtonX() {
        return panelX + panelW - PAD - 64 - 72;
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int bottom, int contentH, int scrollPx) {
        int viewH = bottom - top;
        if (contentH <= viewH) {
            return;
        }
        int thumbH = Math.max(18, viewH * viewH / contentH);
        int thumbY = top + (viewH - thumbH) * scrollPx / Math.max(1, contentH - viewH);
        g.fill(x, top, x + 3, bottom, 0x33000000);
        g.fill(x, thumbY, x + 3, thumbY + thumbH, GOLD);
    }

    // ── 交互 ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        // 类别标签（支持水平滚动）
        int ty = panelY + 22;
        int tStartX = panelX + PAD;
        int tAreaRight = newCatBox.getX() - 4;

        // 计算标签总宽，判断是否可滚动
        int tabsTotalW = 0;
        for (String cat : categories.keySet()) {
            tabsTotalW += tabWidth(cat) + 4;
        }
        if (tabsTotalW > 0) tabsTotalW -= 4;
        boolean tabScrollable = tabsTotalW > (tAreaRight - tStartX);

        if (tabScrollable) {
            int sLeft = tStartX + TAB_ARROW_W;
            int sRight = tAreaRight - TAB_ARROW_W;
            int maxTS = Math.max(0, tabsTotalW - (sRight - sLeft));
            // 左箭头点击
            if (isInRect(mouseX, mouseY, tStartX, ty, TAB_ARROW_W, TAB_H) && tabScroll > 0) {
                tabScroll = Math.max(0, tabScroll - 60);
                clickSound();
                return true;
            }
            // 右箭头点击
            if (isInRect(mouseX, mouseY, tAreaRight - TAB_ARROW_W, ty, TAB_ARROW_W, TAB_H) && tabScroll < maxTS) {
                tabScroll = Math.min(maxTS, tabScroll + 60);
                clickSound();
                return true;
            }
        }

        int tx = tStartX + (tabScrollable ? TAB_ARROW_W : 0);
        for (String cat : categories.keySet()) {
            int w = tabWidth(cat);
            int screenX = tx - tabScroll;
            if (isInRect(mouseX, mouseY, screenX, ty, w, TAB_H)) {
                // 命中标签右侧「×」：删除整个类别
                if (canDeleteCategory()
                        && isInRect(mouseX, mouseY, screenX + w - TAB_DEL_W, ty, TAB_DEL_W, TAB_H)) {
                    deleteCategory(cat);
                    return true;
                }
                if (!cat.equals(currentCategory)) {
                    currentCategory = cat;
                    selected = null;
                    listScroll = 0;
                    refreshEditors();
                    clickSound();
                }
                return true;
            }
            tx += w + 4;
        }
        // “+”新类别
        int plusX = newCatBox.getX() + newCatBox.getWidth() + 2;
        if (isInRect(mouseX, mouseY, plusX, ty, 18, TAB_H)) {
            addCategoryFromBox();
            return true;
        }
        // 条目列表：选中 / 删除
        if (isInRect(mouseX, mouseY, leftX, contentTop, leftW, listBottom - contentTop)) {
            List<RowData> rows = rows();
            int index = ((int) mouseY - contentTop + listScroll) / ROW_H;
            if (index >= 0 && index < rows.size()) {
                int rowY = contentTop + index * ROW_H - listScroll;
                if (isInRect(mouseX, mouseY, leftX + leftW - 14, rowY + 8, 12, 12)) {
                    if (selected == rows.get(index)) {
                        selected = null;
                        refreshEditors();
                    }
                    rows.remove(index);
                    clickSound();
                    return true;
                }
                selected = rows.get(index);
                refreshEditors();
                clickSound();
                return true;
            }
            return true;
        }
        // 背包网格：点击加入当前类别
        int gridTop = contentTop + 11;
        int gridBottom = panelY + panelH - 28;
        if (isInRect(mouseX, mouseY, rightX, gridTop, rightW, gridBottom - gridTop)) {
            List<ItemStack> items = inventoryItems();
            int cols = Math.max(1, (rightW - 4) / CELL);
            int col = ((int) mouseX - rightX - 2) / CELL;
            int row = ((int) mouseY - gridTop - 2) / CELL + gridScroll;
            int index = row * cols + col;
            if (col >= 0 && col < cols && index >= 0 && index < items.size()) {
                addFromInventory(items.get(index));
            }
            return true;
        }
        // 保存 / 完成
        int by = panelY + panelH - 24;
        if (isInRect(mouseX, mouseY, saveButtonX(), by, 64, 18)) {
            ClientPlayNetworking.send(new LootTableSaveC2SPacket(buildTable()));
            clickSound();
            return true;
        }
        if (isInRect(mouseX, mouseY, saveButtonX() + 72, by, 64, 18)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 新类别输入框回车确认
        if (newCatBox.isFocused() && (keyCode == 257 || keyCode == 335)) { // Enter / 小键盘 Enter
            addCategoryFromBox();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 鼠标在标签栏区域 → 水平滚动标签
        int tabY = panelY + 22;
        if (mouseY >= tabY && mouseY < tabY + TAB_H) {
            int tStartX = panelX + PAD;
            int tAreaRight = newCatBox.getX() - 4;
            int tabsTotalW = 0;
            for (String cat : categories.keySet()) {
                tabsTotalW += tabWidth(cat) + 4;
            }
            if (tabsTotalW > 0) tabsTotalW -= 4;
            boolean scrollable = tabsTotalW > (tAreaRight - tStartX);
            if (scrollable) {
                int sLeft = tStartX + TAB_ARROW_W;
                int sRight = tAreaRight - TAB_ARROW_W;
                int maxTS = Math.max(0, tabsTotalW - (sRight - sLeft));
                tabScroll = Mth.clamp(tabScroll - (int) (scrollY * 40), 0, maxTS);
                return true;
            }
        }
        if (mouseX < rightX - PAD / 2.0) {
            listScroll -= (int) (scrollY * ROW_H);
            return true;
        }
        gridScroll -= (int) Math.signum(scrollY);
        return true;
    }

    // ── 数据操作 ─────────────────────────────────────────────────────

    private List<RowData> rows() {
        return categories.computeIfAbsent(currentCategory, k -> new ArrayList<>());
    }

    private void addCategoryFromBox() {
        String name = newCatBox.getValue().trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (name.isEmpty()) {
            return;
        }
        categories.computeIfAbsent(name, k -> new ArrayList<>());
        currentCategory = name;
        selected = null;
        listScroll = 0;
        newCatBox.setValue("");
        refreshEditors();
        clickSound();
    }

    /** 删除整个类别（连同其全部条目）；若删的是当前类别，切到剩余的第一个。保底至少留一个类别。 */
    private void deleteCategory(String cat) {
        if (!canDeleteCategory()) {
            return;
        }
        categories.remove(cat);
        if (cat.equals(currentCategory)) {
            currentCategory = categories.keySet().iterator().next();
            listScroll = 0;
        }
        selected = null;
        refreshEditors();
        clickSound();
    }

    /** 点击背包物品：加入当前类别（数量=堆叠数、权重 1.0）并选中，便于紧接着调参。 */
    private void addFromInventory(ItemStack stack) {
        List<RowData> rows = rows();
        if (rows.size() >= MAX_ENTRIES_PER_CATEGORY) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        RowData row = new RowData(id.toString(), stack.getCount(), 1.0F);
        rows.add(row);
        selected = row;
        listScroll = Math.max(0, rows.size() * ROW_H - (listBottom - contentTop));
        refreshEditors();
        clickSound();
    }

    /** 玩家背包（主 36 格 + 副手）里的物品，按 Item 去重；跳过屏障（60s 槽位占位符）。 */
    private List<ItemStack> inventoryItems() {
        List<ItemStack> out = new ArrayList<>();
        if (this.minecraft == null || this.minecraft.player == null) {
            return out;
        }
        Inventory inv = this.minecraft.player.getInventory();
        List<Item> seen = new ArrayList<>();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty() || stack.is(Items.BARRIER) || seen.contains(stack.getItem())) {
                continue;
            }
            seen.add(stack.getItem());
            out.add(stack);
        }
        return out;
    }

    private SixtySecondsLootTable buildTable() {
        SixtySecondsLootTable table = new SixtySecondsLootTable();
        LinkedHashMap<String, List<SixtySecondsLootTable.Entry>> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<RowData>> e : categories.entrySet()) {
            List<SixtySecondsLootTable.Entry> list = new ArrayList<>();
            for (RowData row : e.getValue()) {
                if (!row.itemId.isEmpty()) {
                    list.add(new SixtySecondsLootTable.Entry(
                            row.itemId, Math.max(1, row.count), Math.max(0, row.weight)));
                }
            }
            if (!list.isEmpty()) {
                map.put(e.getKey(), list);
            }
        }
        table.categories = map;
        return table;
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    private void clickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean isInRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float easeOutCubic(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }

    private static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    private static String trimFloat(float v) {
        return v == (int) v ? Integer.toString((int) v) : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 单条 loot 条目的可编辑副本；{@link #icon()} 按 itemId 实时解析（无效 → EMPTY）。 */
    private static final class RowData {
        String itemId;
        int count;
        float weight;

        RowData(String itemId, int count, float weight) {
            this.itemId = itemId;
            this.count = count;
            this.weight = weight;
        }

        ItemStack icon() {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(BuiltInRegistries.ITEM.get(rl));
        }
    }
}
