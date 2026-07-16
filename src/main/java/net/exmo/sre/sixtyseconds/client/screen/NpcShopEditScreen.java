package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.network.NpcShopSaveC2SPacket;
import net.exmo.sre.sixtyseconds.network.OpenNpcShopEditS2CPacket;
import net.exmo.sre.sixtyseconds.shop.SixtySecondsShopTable;
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
 * 商店货架编辑 GUI（复古车票风，遵循 docs/ui_style.md）——<b>与物资箱的
 * {@code LootTableEditScreen} 同一套布局</b>：顶部档案标签页切换；左侧为当前档案的商品列表
 * （物品图标 + 名称 + 单价/库存，滚动 + hover + 选中态），选中后底部编辑器可改
 * 物品ID/单价/库存/日回补；右侧为<b>背包物品选择器</b>——点击背包里的物品即把它加进当前档案。
 * 「保存」经 {@link NpcShopSaveC2SPacket} 上传服务端落盘（CCA sync 只同步不落盘）。空档案在保存时自动移除。
 */
public class NpcShopEditScreen extends Screen {
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
    private static final int TAB_DEL_W = 9;      // 档案标签右侧删除「×」预留槽宽
    private static final int ROW_H = 30;
    private static final int CELL = 20;          // 背包格尺寸
    private static final int MAX_ENTRIES_PER_PROFILE = 64;

    /** 档案 → 商品（可变副本，保存时重建协议对象）。 */
    private final LinkedHashMap<String, List<RowData>> profiles = new LinkedHashMap<>();
    private String currentProfile;
    private RowData selected;

    private int listScroll;   // 商品列表滚动（像素）
    private int gridScroll;   // 背包网格滚动（行）
    private int openTicks;    // 入场动画计时

    private EditBox idBox;
    private EditBox priceBox;
    private EditBox stockBox;
    private EditBox restockBox;
    private EditBox newProfileBox;
    private boolean updatingEditors; // setValue 触发 responder 的回写保护

    // 每帧计算的布局缓存（render/mouse 共用）
    private int panelX, panelY, panelW, panelH;
    private int leftX, leftW, rightX, rightW, contentTop, listBottom, detailTop;

    public NpcShopEditScreen(OpenNpcShopEditS2CPacket data) {
        super(Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.title"));
        for (Map.Entry<String, List<SixtySecondsShopTable.Entry>> e : data.table().profiles.entrySet()) {
            List<RowData> rows = new ArrayList<>();
            if (e.getValue() != null) {
                for (SixtySecondsShopTable.Entry entry : e.getValue()) {
                    rows.add(new RowData(entry));
                }
            }
            profiles.put(e.getKey(), rows);
        }
        if (profiles.isEmpty()) {
            profiles.put("default", new ArrayList<>());
        }
        // 默认跳到该商人正在用的档案页
        currentProfile = profiles.containsKey(data.profile())
                ? data.profile() : profiles.keySet().iterator().next();
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
        // 底部四格：物品ID（宽） + 单价 / 库存 / 日回补
        idBox = editor(leftX, boxY, leftW - 150, 128);
        priceBox = editor(leftX + leftW - 146, boxY, 46, 6);
        stockBox = editor(leftX + leftW - 98, boxY, 46, 6);
        restockBox = editor(leftX + leftW - 48, boxY, 48, 6);
        idBox.setResponder(v -> {
            if (!updatingEditors && selected != null) {
                selected.itemId = v.trim();
            }
        });
        priceBox.setResponder(v -> {
            if (!updatingEditors && selected != null) {
                selected.price = Math.max(1, parseInt(v, selected.price));
            }
        });
        stockBox.setResponder(v -> {
            if (!updatingEditors && selected != null) {
                selected.stock = Math.max(0, parseInt(v, selected.stock));
            }
        });
        restockBox.setResponder(v -> {
            if (!updatingEditors && selected != null) {
                selected.restockPerDay = Math.max(0, parseInt(v, selected.restockPerDay));
            }
        });
        newProfileBox = editor(panelX + panelW - PAD - 96, panelY + 22, 96, 24);
        newProfileBox.setHint(Component.translatable(
                        "screen.noellesroles.sixty_seconds.npc.shop_edit.new_profile")
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
        priceBox.setVisible(has);
        stockBox.setVisible(has);
        restockBox.setVisible(has);
        if (has) {
            idBox.setValue(selected.itemId);
            priceBox.setValue(Integer.toString(selected.price));
            stockBox.setValue(Integer.toString(selected.stock));
            restockBox.setValue(Integer.toString(selected.restockPerDay));
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

        g.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                panelX + PAD, panelY + 8, GOLD, false);

        drawTabs(g, mouseX, mouseY);
        drawEntryList(g, mouseX, mouseY, open);
        drawInventoryGrid(g, mouseX, mouseY);
        drawDetailEditor(g);
        drawBottomButtons(g, mouseX, mouseY);
    }

    /** 顶部档案标签页 + 新档案输入框。 */
    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = panelX + PAD;
        int y = panelY + 22;
        for (String profile : profiles.keySet()) {
            int w = tabWidth(profile);
            if (x + w > newProfileBox.getX() - 24) {
                g.drawString(this.font, "…", x + 2, y + 5, MUTED, false);
                break;
            }
            boolean active = profile.equals(currentProfile);
            boolean hover = isInRect(mouseX, mouseY, x, y, w, TAB_H);
            int bg = active ? blendColors(0xFF1A1008, 0xFFC9A84C, 0.45F)
                    : hover ? blendColors(0xFF1A1008, 0xFFC9A84C, 0.20F) : 0x331A1008;
            g.fill(x, y, x + w, y + TAB_H, bg);
            g.renderOutline(x, y, w, TAB_H, active ? GOLD : IDLE_BORDER);
            int labelW = w - (canDeleteProfile() ? TAB_DEL_W : 0);
            g.drawCenteredString(this.font, profile, x + labelW / 2, y + 5, active ? TEXT : MUTED);
            // 删除该档案的「×」（档案>1 时可用；保底至少留一个）
            if (canDeleteProfile()) {
                boolean xHover = isInRect(mouseX, mouseY, x + w - TAB_DEL_W, y, TAB_DEL_W, TAB_H);
                g.drawString(this.font, "×", x + w - TAB_DEL_W + 2, y + 5, xHover ? RED : MUTED, false);
            }
            x += w + 4;
        }
        // “+”按钮：把 newProfileBox 内容作为新档案
        int plusX = newProfileBox.getX() + newProfileBox.getWidth() + 2;
        boolean plusHover = isInRect(mouseX, mouseY, plusX, y, 18, TAB_H);
        g.fill(plusX, y, plusX + 18, y + TAB_H, plusHover ? HOVER_BG : 0x331A1008);
        g.renderOutline(plusX, y, 18, TAB_H, plusHover ? GOLD : IDLE_BORDER);
        g.drawCenteredString(this.font, "+", plusX + 9, y + 5, GREEN);
    }

    private int tabWidth(String profile) {
        return this.font.width(profile) + 14 + (canDeleteProfile() ? TAB_DEL_W : 0);
    }

    /** 至少保留一个档案：仅当档案数 > 1 时允许删除、并预留标签删除槽。 */
    private boolean canDeleteProfile() {
        return profiles.size() > 1;
    }

    /** 左侧商品列表（scissor 滚动 + 逐行入场滑入）。 */
    private void drawEntryList(GuiGraphics g, int mouseX, int mouseY, float open) {
        List<RowData> rows = rows();
        g.renderOutline(leftX - 1, contentTop - 1, leftW + 2, listBottom - contentTop + 2, IDLE_BORDER);
        if (rows.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.entries_empty"),
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
                    : Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.unknown_item");
            g.drawString(this.font, name, x + 26, y + 5, valid ? TEXT : RED, false);
            g.drawString(this.font, row.itemId, x + 26, y + 17, MUTED, false);
            String meta = "×" + row.count + "  ¤" + row.price + "  x" + row.stock;
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
        drawScrollbar(g, leftX + leftW - 3, contentTop, listBottom, rows.size() * ROW_H, listScroll);
    }

    /** 右侧背包物品选择器：点击加入当前档案。 */
    private void drawInventoryGrid(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.inventory"),
                rightX, contentTop - 1, GOLD, false);
        int gridTop = contentTop + 11;
        int gridBottom = panelY + panelH - 28;
        g.renderOutline(rightX - 1, gridTop - 1, rightW + 2, gridBottom - gridTop + 2, IDLE_BORDER);

        List<ItemStack> items = inventoryItems();
        if (items.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.inventory_empty"),
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
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.hint"),
                rightX, gridBottom + 4, MUTED, false);
        if (!hovered.isEmpty()) {
            g.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    /** 底部选中条目编辑器（物品ID / 单价 / 库存 / 日回补）。 */
    private void drawDetailEditor(GuiGraphics g) {
        if (selected == null) {
            g.drawString(this.font,
                    Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.select_hint"),
                    leftX, detailTop + 14, MUTED, false);
            return;
        }
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.item_id"),
                idBox.getX(), detailTop + 1, MUTED, false);
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.price"),
                priceBox.getX(), detailTop + 1, MUTED, false);
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.stock"),
                stockBox.getX(), detailTop + 1, MUTED, false);
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.restock"),
                restockBox.getX(), detailTop + 1, MUTED, false);
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
                Component.translatable("screen.noellesroles.sixty_seconds.npc.shop_edit.save"),
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
        // 档案标签
        int tx = panelX + PAD;
        int ty = panelY + 22;
        for (String profile : profiles.keySet()) {
            int w = tabWidth(profile);
            if (tx + w > newProfileBox.getX() - 24) {
                break;
            }
            if (isInRect(mouseX, mouseY, tx, ty, w, TAB_H)) {
                // 命中标签右侧「×」：删除整个档案
                if (canDeleteProfile()
                        && isInRect(mouseX, mouseY, tx + w - TAB_DEL_W, ty, TAB_DEL_W, TAB_H)) {
                    deleteProfile(profile);
                    return true;
                }
                if (!profile.equals(currentProfile)) {
                    currentProfile = profile;
                    selected = null;
                    listScroll = 0;
                    refreshEditors();
                    clickSound();
                }
                return true;
            }
            tx += w + 4;
        }
        // “+”新档案
        int plusX = newProfileBox.getX() + newProfileBox.getWidth() + 2;
        if (isInRect(mouseX, mouseY, plusX, ty, 18, TAB_H)) {
            addProfileFromBox();
            return true;
        }
        // 商品列表：选中 / 删除
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
        // 背包网格：点击加入当前档案
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
            ClientPlayNetworking.send(new NpcShopSaveC2SPacket(buildTable()));
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
        // 新档案输入框回车确认
        if (newProfileBox.isFocused() && (keyCode == 257 || keyCode == 335)) { // Enter / 小键盘 Enter
            addProfileFromBox();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < rightX - PAD / 2.0) {
            listScroll -= (int) (scrollY * ROW_H);
            return true;
        }
        gridScroll -= (int) Math.signum(scrollY);
        return true;
    }

    // ── 数据操作 ─────────────────────────────────────────────────────

    private List<RowData> rows() {
        return profiles.computeIfAbsent(currentProfile, k -> new ArrayList<>());
    }

    private void addProfileFromBox() {
        String name = newProfileBox.getValue().trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (name.isEmpty()) {
            return;
        }
        profiles.computeIfAbsent(name, k -> new ArrayList<>());
        currentProfile = name;
        selected = null;
        listScroll = 0;
        newProfileBox.setValue("");
        refreshEditors();
        clickSound();
    }

    /** 删除整个档案（连同其全部商品）；若删的是当前档案，切到剩余的第一个。保底至少留一个。 */
    private void deleteProfile(String profile) {
        if (!canDeleteProfile()) {
            return;
        }
        profiles.remove(profile);
        if (profile.equals(currentProfile)) {
            currentProfile = profiles.keySet().iterator().next();
            listScroll = 0;
        }
        selected = null;
        refreshEditors();
        clickSound();
    }

    /** 点击背包物品：加入当前档案（数量=堆叠数、默认价/库存）并选中，便于紧接着调参。 */
    private void addFromInventory(ItemStack stack) {
        List<RowData> rows = rows();
        if (rows.size() >= MAX_ENTRIES_PER_PROFILE) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        RowData row = new RowData(new SixtySecondsShopTable.Entry(
                id.toString(), stack.getCount(), 5, 8, 4, 0.25F));
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

    private SixtySecondsShopTable buildTable() {
        SixtySecondsShopTable table = new SixtySecondsShopTable();
        LinkedHashMap<String, List<SixtySecondsShopTable.Entry>> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<RowData>> e : profiles.entrySet()) {
            List<SixtySecondsShopTable.Entry> list = new ArrayList<>();
            for (RowData row : e.getValue()) {
                if (!row.itemId.isEmpty()) {
                    list.add(row.toEntry());
                }
            }
            if (!list.isEmpty()) {
                map.put(e.getKey(), list);
            }
        }
        table.profiles = map;
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

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 单条商品的可编辑副本；{@link #icon()} 按 itemId 实时解析（无效 → EMPTY）。 */
    private static final class RowData {
        String itemId;
        int count;
        int price;
        int stock;
        int restockPerDay;
        /** 价格浮动：本 GUI 不暴露编辑（默认 0.25 足够），但要原样带回去，别在编辑时丢掉。 */
        final float priceJitter;
        final String currency;

        RowData(SixtySecondsShopTable.Entry entry) {
            this.itemId = entry.itemId == null ? "" : entry.itemId;
            this.count = Math.max(1, entry.count);
            this.price = Math.max(1, entry.price);
            this.stock = Math.max(0, entry.stock);
            this.restockPerDay = Math.max(0, entry.restockPerDay);
            this.priceJitter = entry.priceJitter;
            this.currency = entry.currency == null ? "MINIGAME_TOKEN" : entry.currency;
        }

        SixtySecondsShopTable.Entry toEntry() {
            SixtySecondsShopTable.Entry entry = new SixtySecondsShopTable.Entry(
                    itemId, Math.max(1, count), Math.max(1, price),
                    Math.max(0, stock), Math.max(0, restockPerDay), priceJitter);
            entry.currency = currency;
            return entry;
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
