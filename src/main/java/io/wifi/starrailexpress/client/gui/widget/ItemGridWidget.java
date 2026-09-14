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

package io.wifi.starrailexpress.client.gui.widget;

import io.wifi.starrailexpress.client.util.PinYinUtils;
import io.wifi.starrailexpress.client.util.SafeNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 可滚动的图标网格选择器：方块和物品共用，选中回调交出对应的 {@link ItemStack}。
 *
 * <p>过滤支持 注册 ID / 显示名 / 显示名拼音。滚轮只在鼠标位于网格内时滚动网格自己
 * （返回 true 吃掉事件），否则事件会冒泡给界面去滚动整页。
 */
public class ItemGridWidget extends AbstractWidget {

    private static final int CELL = 18;
    private static final int COLOR_GRID_BG = 0x66000000;
    private static final int COLOR_HOVER = 0x33FFFFFF;
    private static final int COLOR_SELECTION = 0x88D4AF37;
    private static final int COLOR_BORDER = 0xFF5A4530;

    /** 缓存：全量方块表 / 全量物品表都只建一次（注册表在运行期不变）。 */
    private static List<Entry> blockEntries;
    private static List<Entry> itemEntries;

    private final Consumer<ItemStack> onSelect;
    /** 未过滤的基准表。 */
    private final List<Entry> base;

    private List<Entry> matched;
    private ItemStack selected = ItemStack.EMPTY;
    private double scroll;
    private int columns = 1;

    private ItemGridWidget(int x, int y, int width, int height, List<Entry> entries,
            Consumer<ItemStack> onSelect) {
        super(x, y, width, height, Component.empty());
        this.onSelect = onSelect;
        this.base = entries;
        this.matched = entries;
    }

    /** 有对应物品的方块（也就是能摆出来的方块）。 */
    public static ItemGridWidget forBlocks(int x, int y, int width, int height, Consumer<ItemStack> onSelect) {
        return new ItemGridWidget(x, y, width, height, blockEntries(), onSelect);
    }

    /** 所有物品（排除空气）。 */
    public static ItemGridWidget forItems(int x, int y, int width, int height, Consumer<ItemStack> onSelect) {
        return new ItemGridWidget(x, y, width, height, itemEntries(), onSelect);
    }

    private record Entry(ItemStack stack, String search) {
    }

    private static synchronized List<Entry> blockEntries() {
        if (blockEntries == null) {
            List<Entry> entries = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block == Blocks.AIR || block.asItem() == Items.AIR) {
                    continue;
                }
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                entries.add(new Entry(new ItemStack(block), searchKey(id, SafeNames.of(block))));
            }
            entries.sort(Comparator.comparing(entry -> registryId(entry.stack)));
            blockEntries = List.copyOf(entries);
        }
        return blockEntries;
    }

    private static synchronized List<Entry> itemEntries() {
        if (itemEntries == null) {
            List<Entry> entries = new ArrayList<>();
            for (var item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                entries.add(new Entry(new ItemStack(item), searchKey(id, SafeNames.of(item))));
            }
            entries.sort(Comparator.comparing(entry -> registryId(entry.stack)));
            itemEntries = List.copyOf(entries);
        }
        return itemEntries;
    }

    private static String registryId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String searchKey(String id, String name) {
        return id.toLowerCase(Locale.ROOT) + '\u0000'
                + name.toLowerCase(Locale.ROOT) + '\u0000'
                + PinYinUtils.toSearchablePinyin(name).toLowerCase(Locale.ROOT);
    }

    public void setSelected(ItemStack stack) {
        this.selected = stack == null ? ItemStack.EMPTY : stack;
    }

    /** 过滤词同时充当"手输 ID"的搜索词。 */
    public void setFilter(String filter) {
        String normalized = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            this.matched = this.base;
        } else {
            List<Entry> matches = new ArrayList<>();
            for (Entry entry : this.base) {
                if (entry.search().contains(normalized)) {
                    matches.add(entry);
                }
            }
            this.matched = List.copyOf(matches);
        }
        this.scroll = 0.0D;
    }

    /** 过滤结果里的第一个物品，用于手输 ID 时直接选中。 */
    public ItemStack firstMatch() {
        return this.matched.isEmpty() ? ItemStack.EMPTY : this.matched.get(0).stack();
    }

    public void renderEmptyHint(GuiGraphics graphics, Font font) {
        if (!this.matched.isEmpty()) {
            return;
        }
        graphics.drawString(font, Component.translatable("gui.display_block.item_search.empty"),
                this.getX() + 4, this.getY() + 4, 0xFF9E8B6E, false);
    }

    private int rowCount() {
        return (this.matched.size() + this.columns - 1) / this.columns;
    }

    private int maxScroll() {
        return Math.max(0, rowCount() * CELL - this.getHeight());
    }

    private void setScroll(double value) {
        this.scroll = Math.max(0.0D, Math.min(maxScroll(), value));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int width = this.getWidth();
        int height = this.getHeight();
        this.columns = Math.max(1, width / CELL);

        graphics.fill(this.getX(), this.getY(), this.getX() + width, this.getY() + height, COLOR_GRID_BG);
        graphics.renderOutline(this.getX(), this.getY(), width, height, COLOR_BORDER);

        int hovered = this.isHovered() ? cellAt(mouseX, mouseY) : -1;
        int rows = rowCount();
        // 只遍历可见的那几行：表里有上千项，全遍历是纯浪费（每格渲染都不便宜）
        int firstRow = Math.max(0, (int) (this.scroll / CELL));
        int lastRow = Math.min(rows - 1, (int) ((this.scroll + height) / CELL));

        graphics.enableScissor(this.getX(), this.getY(), this.getX() + width, this.getY() + height);
        for (int row = firstRow; row <= lastRow; row++) {
            int rowY = (int) (this.getY() + row * CELL - this.scroll);
            for (int column = 0; column < this.columns; column++) {
                int index = row * this.columns + column;
                if (index >= this.matched.size()) {
                    break;
                }
                int cellX = this.getX() + column * CELL;
                Entry entry = this.matched.get(index);
                if (isSelected(entry.stack())) {
                    graphics.fill(cellX, rowY, cellX + CELL, rowY + CELL, COLOR_SELECTION);
                } else if (index == hovered) {
                    graphics.fill(cellX, rowY, cellX + CELL, rowY + CELL, COLOR_HOVER);
                }
                graphics.renderItem(entry.stack(), cellX + 1, rowY + 1);
            }
        }
        graphics.disableScissor();
    }

    private boolean isSelected(ItemStack stack) {
        return !this.selected.isEmpty() && ItemStack.isSameItemSameComponents(this.selected, stack);
    }

    /** 把鼠标坐标换算成网格下标，不在网格内或越界返回 -1。 */
    private int cellAt(double mouseX, double mouseY) {
        int localX = (int) (mouseX - this.getX());
        int localY = (int) (mouseY - this.getY() + this.scroll);
        if (localX < 0 || localX >= this.columns * CELL || localY < 0) {
            return -1;
        }
        int index = (localY / CELL) * this.columns + localX / CELL;
        return index < this.matched.size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || button != 0 || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int index = cellAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        this.selected = this.matched.get(index).stack();
        this.onSelect.accept(this.selected);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        setScroll(this.scroll - scrollY * CELL * 1.5D);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
