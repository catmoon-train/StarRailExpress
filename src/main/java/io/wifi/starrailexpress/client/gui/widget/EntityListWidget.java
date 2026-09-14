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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 可滚动的实体类型列表：一行一个类型（显示名 + 注册 ID），支持 ID / 显示名 / 拼音过滤。
 *
 * <p>做成控件而不是表单行，是为了让"输入框过滤"不必重建界面——
 * 重建会清掉焦点，等于输入框只能打一个字。
 */
public class EntityListWidget extends AbstractWidget {

    private static final int ROW_HEIGHT = 12;
    private static final int COLOR_BG = 0x66000000;
    private static final int COLOR_BORDER = 0xFF5A4530;
    private static final int COLOR_HOVER = 0x33FFFFFF;
    private static final int COLOR_SELECTION = 0x88D4AF37;
    private static final int COLOR_TEXT = 0xFFFFF4DC;
    private static final int COLOR_MUTED = 0xFF9E8B6E;

    private static List<Entry> allEntries;

    private final Consumer<EntityType<?>> onSelect;
    private final List<Entry> base;

    private List<Entry> matched;
    private ResourceLocation selected;
    private double scroll;

    public EntityListWidget(int x, int y, int width, int height, Consumer<EntityType<?>> onSelect) {
        super(x, y, width, height, Component.empty());
        this.onSelect = onSelect;
        this.base = allEntries();
        this.matched = this.base;
    }

    /** 预计算一次：类型、ID、显示名、搜索串。 */
    private record Entry(EntityType<?> type, ResourceLocation id, String label, String search) {
    }

    private static synchronized List<Entry> allEntries() {
        if (allEntries == null) {
            List<Entry> entries = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                if (type == EntityType.PLAYER) {
                    // 客户端没有可用的 player 渲染实例，也不适合塞进方块里
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                String name = SafeNames.of(type);
                entries.add(new Entry(type, id, name,
                        id.toString().toLowerCase(Locale.ROOT) + '\u0000'
                                + name.toLowerCase(Locale.ROOT) + '\u0000'
                                + PinYinUtils.toSearchablePinyin(name).toLowerCase(Locale.ROOT)));
            }
            entries.sort(Comparator.comparing(entry -> entry.id().toString()));
            allEntries = List.copyOf(entries);
        }
        return allEntries;
    }

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

    public void setSelected(ResourceLocation id) {
        this.selected = id;
    }

    public boolean isEmpty() {
        return this.matched.isEmpty();
    }

    public void renderEmptyHint(GuiGraphics graphics, Font font) {
        if (!this.matched.isEmpty()) {
            return;
        }
        graphics.drawString(font, Component.translatable("gui.display_block.entity_search.empty"),
                this.getX() + 4, this.getY() + 3, COLOR_MUTED, false);
    }

    private int maxScroll() {
        return Math.max(0, this.matched.size() * ROW_HEIGHT - this.getHeight());
    }

    private void setScroll(double value) {
        this.scroll = Math.max(0.0D, Math.min(maxScroll(), value));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.getX();
        int top = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();
        graphics.fill(left, top, left + width, top + height, COLOR_BG);
        graphics.renderOutline(left, top, width, height, COLOR_BORDER);

        int hovered = this.isHovered() ? rowAt(mouseY) : -1;
        int firstRow = Math.max(0, (int) (this.scroll / ROW_HEIGHT));
        int lastRow = Math.min(this.matched.size() - 1, (int) ((this.scroll + height) / ROW_HEIGHT));

        Font font = Minecraft.getInstance().font;
        graphics.enableScissor(left, top, left + width, top + height);
        for (int row = firstRow; row <= lastRow; row++) {
            Entry entry = this.matched.get(row);
            int rowY = (int) (top + row * ROW_HEIGHT - this.scroll);
            if (entry.id().equals(this.selected)) {
                graphics.fill(left + 1, rowY, left + width - 1, rowY + ROW_HEIGHT, COLOR_SELECTION);
            } else if (row == hovered) {
                graphics.fill(left + 1, rowY, left + width - 1, rowY + ROW_HEIGHT, COLOR_HOVER);
            }
            graphics.drawString(font, entry.label(), left + 3, rowY + 2, COLOR_TEXT, false);
            String id = entry.id().toString();
            graphics.drawString(font, id, left + width - 5 - font.width(id), rowY + 2, COLOR_MUTED, false);
        }
        graphics.disableScissor();
    }

    private int rowAt(double mouseY) {
        int local = (int) (mouseY - this.getY() + this.scroll);
        if (local < 0) {
            return -1;
        }
        int row = local / ROW_HEIGHT;
        return row < this.matched.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || button != 0 || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int row = rowAt(mouseY);
        if (row < 0) {
            return false;
        }
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        Entry entry = this.matched.get(row);
        this.selected = entry.id();
        this.onSelect.accept(entry.type());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        setScroll(this.scroll - scrollY * ROW_HEIGHT * 2.0D);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
