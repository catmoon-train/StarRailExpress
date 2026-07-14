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

import java.util.*;

/**
 * 空投物资自定义编辑 GUI（复古车票风）：
 * 顶部「空投」固定类别标签；左侧条目列表（物品图标 + 名称 + 数量/权重，滚动 + hover +
 * 选中态），选中后底部编辑器可改 物品ID/数量/权重；右侧背包物品选择器——点击背包物品
 * 即加到空投表。「仅保存空投」上传服务端落盘。
 */
public class AirdropLootEditScreen extends Screen {
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
    private static final int ROW_H = 30;
    private static final int CELL = 20;
    private static final int MAX_ENTRIES = 64;

    private final List<Entry> entries = new ArrayList<>();
    private Entry selected;
    private int listScroll;
    private int gridScroll;
    private int openTicks;

    private EditBox idBox, countBox, weightBox;

    private int panelX, panelY, panelW, panelH;
    private int leftX, leftW, rightX, rightW;
    private int contentTop, listBottom, detailTop;

    public AirdropLootEditScreen(SixtySecondsLootTable fullTable) {
        super(Component.translatable("screen.noellesroles.sixty_seconds.airdrop_edit.title"));
        List<SixtySecondsLootTable.Entry> airdrop = fullTable.categories.get("airdrop");
        if (airdrop != null) {
            for (var e : airdrop) {
                entries.add(new Entry(e.itemId, e.count, e.weight));
            }
        }
    }

    private void computeLayout() {
        panelW = Math.min(700, this.width);
        panelH = Mth.clamp(this.height * 7 / 10, 280, 400);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        contentTop = panelY + 30;
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
        idBox.setResponder(v -> { if (selected != null) selected.itemId = v.trim(); });
        countBox.setResponder(v -> {
            if (selected != null) selected.count = Math.max(1, parseInt(v, selected.count));
        });
        weightBox.setResponder(v -> {
            if (selected != null) selected.weight = Math.max(0, parseFloat(v, selected.weight));
        });
        refreshEditors();
    }

    private EditBox editor(int x, int y, int w, int maxLen) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.empty());
        box.setMaxLength(maxLen);
        box.setBordered(true);
        addRenderableWidget(box);
        return box;
    }

    private void refreshEditors() {
        boolean has = selected != null;
        idBox.setVisible(has);
        countBox.setVisible(has);
        weightBox.setVisible(has);
        if (has) {
            idBox.setValue(selected.itemId);
            countBox.setValue(Integer.toString(selected.count));
            weightBox.setValue(trimFloat(selected.weight));
        }
    }

    @Override
    public void tick() { super.tick(); openTicks++; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        computeLayout();
        float open = easeOutCubic(Mth.clamp((openTicks + pt) / 5f, 0f, 1f));
        g.fill(0, 0, this.width, this.height, ((int) (0x66 * open) << 24));
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, BG_TOP, BG_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, ACCENT_LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        float open = easeOutCubic(Mth.clamp((openTicks + pt) / 5f, 0f, 1f));
        g.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                panelX + PAD, panelY + 8, GOLD, false);
        drawEntries(g, mouseX, mouseY, open);
        drawInventory(g, mouseX, mouseY);
        drawEditor(g);
        drawButtons(g, mouseX, mouseY);
    }

    private void drawEntries(GuiGraphics g, int mx, int my, float open) {
        g.renderOutline(leftX - 1, contentTop - 1, leftW + 2, listBottom - contentTop + 2, IDLE_BORDER);
        if (entries.isEmpty()) {
            String hint = Component.translatable(
                    "screen.noellesroles.sixty_seconds.airdrop_edit.empty").getString();
            g.drawCenteredString(this.font, hint, leftX + leftW / 2,
                    (contentTop + listBottom) / 2 - 4, MUTED);
            return;
        }
        int maxScroll = Math.max(0, entries.size() * ROW_H - (listBottom - contentTop));
        listScroll = Mth.clamp(listScroll, 0, maxScroll);
        g.enableScissor(leftX, contentTop, leftX + leftW, listBottom);
        for (int i = 0; i < entries.size(); i++) {
            int y = contentTop + i * ROW_H - listScroll;
            if (y + ROW_H < contentTop || y > listBottom) continue;
            Entry e = entries.get(i);
            float rowT = easeOutCubic(Mth.clamp((openTicks - i * 0.6f) / 5f, 0f, 1f));
            int x = leftX - (int) ((1f - rowT) * 22f);
            boolean hover = inRect(mx, my, leftX, Math.max(y, contentTop),
                    leftW, Math.min(y + ROW_H, listBottom) - Math.max(y, contentTop));
            if (e == selected) {
                g.fillGradient(leftX, y, leftX + leftW, y + ROW_H,
                        blend(0xFF1A1008, 0xFFC9A84C, 0.32F), blend(0xFF120A04, 0xFFC9A84C, 0.18F));
            } else if (hover) g.fill(leftX, y, leftX + leftW, y + ROW_H, HOVER_BG);
            ItemStack icon = e.icon();
            if (!icon.isEmpty()) g.renderItem(icon, x + 4, y + 6);
            else g.drawString(this.font, "?", x + 9, y + 10, RED, false);
            Component name = !icon.isEmpty() ? icon.getHoverName()
                    : Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.unknown_item");
            g.drawString(this.font, name, x + 26, y + 5, !icon.isEmpty() ? TEXT : RED, false);
            g.drawString(this.font, e.itemId, x + 26, y + 17, MUTED, false);
            String meta = "×" + e.count + "  w" + trimFloat(e.weight);
            g.drawString(this.font, meta, leftX + leftW - this.font.width(meta) - 18, y + 10,
                    0xFFC8B898, false);
            if (hover) {
                boolean xH = inRect(mx, my, leftX + leftW - 14, y + 8, 12, 12);
                g.drawString(this.font, "×", leftX + leftW - 11, y + 10, xH ? RED : MUTED, false);
            }
            g.fill(leftX + 2, y + ROW_H - 1, leftX + leftW - 2, y + ROW_H, ROW_SEP);
        }
        g.disableScissor();
        drawScrollbar(g, leftX + leftW - 3, contentTop, listBottom, entries.size() * ROW_H, listScroll);
    }

    private void drawInventory(GuiGraphics g, int mx, int my) {
        g.drawString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.inventory"),
                rightX, contentTop - 1, GOLD, false);
        int gt = contentTop + 11, gb = panelY + panelH - 28;
        g.renderOutline(rightX - 1, gt - 1, rightW + 2, gb - gt + 2, IDLE_BORDER);
        List<ItemStack> items = invItems();
        if (items.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.inventory_empty"),
                    rightX + rightW / 2, (gt + gb) / 2 - 4, MUTED);
            return;
        }
        int cols = Math.max(1, (rightW - 4) / CELL);
        int tr = (items.size() + cols - 1) / cols;
        int vr = Math.max(1, (gb - gt - 4) / CELL);
        gridScroll = Mth.clamp(gridScroll, 0, Math.max(0, tr - vr));
        g.enableScissor(rightX, gt, rightX + rightW, gb);
        for (int i = 0; i < items.size(); i++) {
            int r = i / cols - gridScroll;
            if (r < 0 || r >= vr) continue;
            int cx = rightX + 2 + (i % cols) * CELL, cy = gt + 2 + r * CELL;
            g.renderItem(items.get(i), cx + 2, cy + 2);
            g.renderItemDecorations(this.font, items.get(i), cx + 2, cy + 2);
        }
        g.disableScissor();
        drawScrollbar(g, rightX + rightW - 3, gt, gb, tr * CELL, gridScroll * CELL);
    }

    private void drawEditor(GuiGraphics g) {
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

    private void drawButtons(GuiGraphics g, int mx, int my) {
        int y = panelY + panelH - 24;
        int sx = panelX + panelW - PAD - 128 - 72;
        boolean sH = inRect(mx, my, sx, y, 64, 18);
        g.fill(sx, y, sx + 64, y + 18, sH ? blend(0xFF1A1008, GOLD, 0.35F) : 0x551A1008);
        g.renderOutline(sx, y, 64, 18, sH ? GOLD : BORDER);
        g.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.airdrop_edit.save"),
                sx + 32, y + 5, GOLD);
        int dx = sx + 72;
        boolean dH = inRect(mx, my, dx, y, 64, 18);
        g.fill(dx, y, dx + 64, y + 18, dH ? HOVER_BG : 0x551A1008);
        g.renderOutline(dx, y, 64, 18, dH ? GOLD : IDLE_BORDER);
        g.drawCenteredString(this.font, Component.translatable("gui.done"), dx + 32, y + 5, TEXT);
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int bottom, int ch, int sp) {
        int vh = bottom - top;
        if (ch <= vh) return;
        int th = Math.max(18, vh * vh / ch);
        int ty = top + (vh - th) * sp / Math.max(1, ch - vh);
        g.fill(x, top, x + 3, bottom, 0x33000000);
        g.fill(x, ty, x + 3, ty + th, GOLD);
    }

    // ── Interaction ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (btn != 0) return false;
        // Entry list
        if (inRect(mx, my, leftX, contentTop, leftW, listBottom - contentTop)) {
            int idx = ((int) my - contentTop + listScroll) / ROW_H;
            if (idx >= 0 && idx < entries.size()) {
                int ry = contentTop + idx * ROW_H - listScroll;
                if (inRect(mx, my, leftX + leftW - 14, ry + 8, 12, 12)) {
                    if (selected == entries.get(idx)) selected = null;
                    entries.remove(idx);
                    refreshEditors(); click();
                    return true;
                }
                selected = entries.get(idx);
                refreshEditors(); click();
                return true;
            }
            return true;
        }
        // Inventory grid
        int gt = contentTop + 11, gb = panelY + panelH - 28;
        if (inRect(mx, my, rightX, gt, rightW, gb - gt)) {
            List<ItemStack> items = invItems();
            int cols = Math.max(1, (rightW - 4) / CELL);
            int col = ((int) mx - rightX - 2) / CELL;
            int row = ((int) my - gt - 2) / CELL + gridScroll;
            int idx = row * cols + col;
            if (col >= 0 && col < cols && idx >= 0 && idx < items.size()) {
                addFromInv(items.get(idx));
            }
            return true;
        }
        // Buttons
        int by = panelY + panelH - 24;
        int sx = panelX + panelW - PAD - 128 - 72;
        if (inRect(mx, my, sx, by, 64, 18)) {
            ClientPlayNetworking.send(new LootTableSaveC2SPacket(buildTable()));
            click(); return true;
        }
        if (inRect(mx, my, sx + 72, by, 64, 18)) { onClose(); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < rightX - PAD / 2.0) { listScroll -= (int) (sy * ROW_H); return true; }
        gridScroll -= (int) Math.signum(sy);
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    private void addFromInv(ItemStack stack) {
        if (entries.size() >= MAX_ENTRIES) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Entry e = new Entry(id, stack.getCount(), 1.0F);
        entries.add(e); selected = e;
        listScroll = Math.max(0, entries.size() * ROW_H - (listBottom - contentTop));
        refreshEditors(); click();
    }

    private List<ItemStack> invItems() {
        List<ItemStack> out = new ArrayList<>();
        if (this.minecraft == null || this.minecraft.player == null) return out;
        Inventory inv = this.minecraft.player.getInventory();
        List<Item> seen = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.is(Items.BARRIER) || seen.contains(s.getItem())) continue;
            seen.add(s.getItem());
            out.add(s);
        }
        return out;
    }

    private SixtySecondsLootTable buildTable() {
        SixtySecondsLootTable t = new SixtySecondsLootTable();
        List<SixtySecondsLootTable.Entry> list = new ArrayList<>();
        for (Entry e : entries) {
            if (!e.itemId.isEmpty()) {
                list.add(new SixtySecondsLootTable.Entry(e.itemId,
                        Math.max(1, e.count), Math.max(0, e.weight)));
            }
        }
        if (!list.isEmpty()) t.categories.put("airdrop", list);
        return t;
    }

    private void click() {
        if (this.minecraft != null) this.minecraft.getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ── Utilities ───────────────────────────────────────────────────

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float easeOutCubic(float t) { float f = 1f - t; return 1f - f * f * f; }

    private static int blend(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    private static String trimFloat(float v) {
        return v == (int) v ? Integer.toString((int) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private static int parseInt(String s, int fb) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fb; }
    }

    private static float parseFloat(String s, float fb) {
        try { return Float.parseFloat(s.trim()); } catch (NumberFormatException e) { return fb; }
    }

    private static final class Entry {
        String itemId;
        int count;
        float weight;
        Entry(String itemId, int count, float weight) {
            this.itemId = itemId; this.count = count; this.weight = weight;
        }
        ItemStack icon() {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return ItemStack.EMPTY;
            return new ItemStack(BuiltInRegistries.ITEM.get(rl));
        }
    }
}
