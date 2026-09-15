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

import io.wifi.starrailexpress.client.gui.HintText;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 自定义内容（职业 / 修饰符 / 列车物品 / 方块）总列表界面的公共基类：布局、滚动、搜索、行控件与空态。
 *
 * <p>
 * 子类只需要提供数据源与类型相关的回调（{@link #rows}、{@link #rowId}、{@link #rowSummary}、
 * {@link #editorFor}、{@link #removeRow} 等），四个列表界面因此只剩薄薄一层。
 *
 * <p>
 * 排版按 {@code docs/ui_style.md}：面板宽 {@code min(700, width * 0.9)}、高
 * {@code clamp(height * 0.78, 230, 380)}，自上而下依次是标题 / 搜索框 / 列表 / 页脚，
 * 滚动条贴面板右侧内边距，列表区域用 scissor 裁剪。
 *
 * <p>
 * 滚动是<b>整行对齐</b>的（{@code scrollOffset} 永远是 {@link #ROW_HEIGHT} 的整数倍），
 * 于是每个行控件必然完整落在列表区内——行控件是普通 {@link Button}，不受 scissor 约束，
 * 只要不越界就不会压住页脚按钮或上面的搜索框。
 */
@Environment(EnvType.CLIENT)
public abstract class CustomContentManageScreen<T> extends Screen {

    // ==================== 布局常量 ====================

    /** 行高（行控件高 20，留 4 间距）。 */
    protected static final int ROW_HEIGHT = 24;
    private static final int PANEL_MAX_WIDTH = 700;
    private static final int PANEL_MIN_WIDTH = 240;
    private static final int PANEL_MIN_HEIGHT = 230;
    private static final int PANEL_MAX_HEIGHT = 380;
    /** 面板左右内边距。 */
    private static final int PAD = 10;
    /** 标题区高度。 */
    private static final int TITLE_H = 22;
    /** 搜索框高度（文档 §7：18px）。 */
    private static final int SEARCH_H = 18;
    /** 页脚预留高度（返回 / 新建按钮）。 */
    private static final int FOOTER_H = 34;
    /** 行内删除按钮宽度。 */
    private static final int DEL_W = 24;
    /** 行内元素间距。 */
    private static final int GAP = 6;
    /** 摘要列最小宽度：小于这个值摘要就没法看了，此时优先压缩名称列。 */
    private static final int SUMMARY_MIN_W = 110;
    private static final int NAME_MIN_W = 80;
    private static final int NAME_MAX_W = 300;
    /** 行控件高度（原版按钮高度）。 */
    private static final int ROW_WIDGET_H = 20;
    private static final int SCROLL_W = SREPanelStyle.SCROLL_WIDTH;

    // ==================== 布局状态 ====================

    private final Supplier<Screen> backScreenSupplier;

    private int panelW;
    private int panelH;
    private int panelLeftX;
    private int panelTopY;

    /** 列表区（裁剪区）。 */
    private int listTop;
    private int listBottom;
    private int listH;
    private int visibleRows;

    /** 行内布局。 */
    private int rowLeft;
    private int rowRight;
    private int nameW;
    private int delX;
    private int summaryX;
    private int summaryW;

    // ==================== 运行状态 ====================

    /** 当前的行控件（名称按钮 + 删除按钮），滚动 / 过滤 / 删除时只重建这些。 */
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private EditBox searchBox;
    private String filter = "";
    private List<T> filteredCache;

    private int scrollOffset;
    private int maxScroll;

    /** 行内容变了但正处于控件回调里，等下一帧统一重建（避免在控件回调里改控件列表）。 */
    private boolean pendingRowRefresh;

    private boolean draggingThumb;
    private int dragStartY;
    private int dragStartScroll;

    protected CustomContentManageScreen(Component title, Supplier<Screen> backScreenSupplier) {
        super(title);
        this.backScreenSupplier = backScreenSupplier;
    }

    // ==================== 子类接口 ====================

    /** 全部条目（未过滤，返回的应当是配置里的实时列表，删除时直接改它）。 */
    protected abstract List<T> rows();

    /** 条目 id（搜索匹配与行标签用）。 */
    protected abstract String rowId(T row);

    /** 条目显示名（可为空）。 */
    protected abstract String rowName(T row);

    /** 行右侧摘要（会被按列宽截断）。 */
    protected abstract Component rowSummary(T row);

    /** 行文字颜色。 */
    protected int rowColor(T row) {
        return SREPanelStyle.MUTED;
    }

    /** 打开该条目的编辑器。 */
    protected abstract Screen editorFor(T row);

    /** 打开「新建」编辑器。 */
    protected abstract Screen newEditor();

    /** 把条目从配置里移除（只改内存，落盘由 {@link #saveConfig} 负责）。 */
    protected abstract void removeRow(T row);

    /** 保存配置。 */
    protected abstract void saveConfig(MinecraftServer server);

    /** 通知服务端重载索引并同步给客户端。 */
    protected abstract void reloadServer(MinecraftServer server);

    /** 列表为空时的提示键。 */
    protected abstract String emptyKey();

    /** 返回按钮文案键。 */
    protected abstract String backKey();

    /** 新建按钮文案键。 */
    protected abstract String newKey();

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        // 文档 §4 的响应式尺寸；窗口极小时下限退让到「窗口内」，避免面板超出屏幕
        panelW = Mth.clamp((int) (width * 0.9F), Math.min(PANEL_MIN_WIDTH, Math.max(120, width - 8)),
                PANEL_MAX_WIDTH);
        panelH = Mth.clamp((int) (height * 0.78F), Math.min(PANEL_MIN_HEIGHT, Math.max(120, height - 8)),
                PANEL_MAX_HEIGHT);
        panelLeftX = (width - panelW) / 2;
        panelTopY = (height - panelH) / 2;

        // 行内布局：先给滚动条与删除按钮留位，剩下的宽度按「名称 / 摘要」两列切
        rowLeft = panelLeftX + PAD;
        rowRight = panelLeftX + panelW - PAD - SCROLL_W - 4;
        int rowW = rowRight - rowLeft;
        nameW = Mth.clamp(rowW - DEL_W - GAP * 2 - SUMMARY_MIN_W, NAME_MIN_W, NAME_MAX_W);
        delX = rowLeft + nameW + GAP;
        summaryX = delX + DEL_W + GAP;
        summaryW = Math.max(0, rowRight - summaryX);

        listTop = panelTopY + TITLE_H + SEARCH_H + GAP;
        listBottom = panelTopY + panelH - FOOTER_H;
        listH = Math.max(ROW_HEIGHT, listBottom - listTop);
        visibleRows = Math.max(1, listH / ROW_HEIGHT);

        buildSearchBox();
        buildFooter();

        // 面板尺寸可能变了：重新钳位并重建行
        refreshRowWidgets();
    }

    private void buildSearchBox() {
        if (searchBox == null) {
            searchBox = new EditBox(font, 0, 0, 10, SEARCH_H, Component.empty());
            Component hint = Component.translatable("sre.custom_content.manage.search_hint")
                    .withStyle(ChatFormatting.GRAY);
            searchBox.setHint(hint);
            // 输入框内画不下完整提示：悬停看全文
            searchBox.setTooltip(Tooltip.create(hint));
            searchBox.setMaxLength(64);
            searchBox.setResponder(this::onSearchChanged);
        }
        searchBox.setX(rowLeft);
        searchBox.setY(panelTopY + TITLE_H);
        searchBox.setWidth(panelW - PAD * 2);
        searchBox.setHeight(SEARCH_H);
        removeWidget(searchBox);
        addRenderableWidget(searchBox);

        // 过滤后没有结果时标题栏变红（与角色选择界面的搜索框一致）
        boolean noMatch = !filter.isEmpty() && filteredRows().isEmpty();
        searchBox.setTextColor(noMatch ? SREPanelStyle.RED : SREPanelStyle.TEXT);
    }

    private void buildFooter() {
        int footerY = panelTopY + panelH - 28;
        Button backBtn = Button.builder(Component.translatable(backKey()), b -> {
            playClick();
            minecraft.setScreen(backScreenSupplier.get());
        }).bounds(panelLeftX + PAD, footerY, 90, ROW_WIDGET_H).build();
        addRenderableWidget(backBtn);

        Button newBtn = Button.builder(Component.translatable(newKey()), b -> {
            playClick();
            minecraft.setScreen(newEditor());
        }).bounds(panelLeftX + PAD + 100, footerY, 110, ROW_WIDGET_H).build();
        addRenderableWidget(newBtn);
    }

    // ==================== 过滤 ====================

    private void onSearchChanged(String text) {
        filter = text == null ? "" : text.trim();
        filteredCache = null;
        scrollOffset = 0;
        // 输入回调里不动控件列表，交给下一帧
        pendingRowRefresh = true;
    }

    /** 过滤后的条目（结果按当前过滤串缓存）。 */
    protected final List<T> filteredRows() {
        if (filteredCache == null) {
            List<T> all = rows();
            if (filter.isEmpty()) {
                filteredCache = all;
            } else {
                String needle = filter.toLowerCase(Locale.ROOT);
                List<T> matched = new ArrayList<>();
                for (T row : all) {
                    String id = rowId(row);
                    String name = rowName(row);
                    if ((id != null && id.toLowerCase(Locale.ROOT).contains(needle))
                            || (name != null && name.toLowerCase(Locale.ROOT).contains(needle))) {
                        matched.add(row);
                    }
                }
                filteredCache = matched;
            }
        }
        return filteredCache;
    }

    /** 行标签：{@code id (显示名)}，没有显示名时只有 id。 */
    private String rowLabel(T row) {
        String id = rowId(row);
        String name = rowName(row);
        if (id == null) {
            return name == null ? "" : name;
        }
        if (name == null || name.isBlank() || name.equals(id)) {
            return id;
        }
        return id + " (" + name + ")";
    }

    // ==================== 行控件 ====================

    /** 只重建行控件（不碰搜索框与页脚，避免输入框丢焦点）。 */
    private void refreshRowWidgets() {
        for (AbstractWidget widget : rowWidgets) {
            removeWidget(widget);
        }
        rowWidgets.clear();

        List<T> list = filteredRows();
        maxScroll = Math.max(0, (list.size() - visibleRows) * ROW_HEIGHT);
        scrollOffset = snap(Mth.clamp(scrollOffset, 0, maxScroll));

        int start = firstVisibleIndex();
        for (int slot = 0; slot < visibleRows; slot++) {
            int index = start + slot;
            if (index >= list.size()) {
                break;
            }
            T row = list.get(index);
            int y = listTop + slot * ROW_HEIGHT;
            // 回调里按「可见槽位」取条目：滚动后同一槽位对应别的条目
            final int rowSlot = slot;

            String full = rowLabel(row);
            String label = MapUiGraphics.clip(font, full, Math.max(0, nameW - 12));
            Button nameBtn = Button.builder(Component.literal(label), b -> onRowClicked(rowSlot))
                    .bounds(rowLeft, y, nameW, ROW_WIDGET_H).build();
            if (!label.equals(full)) {
                // 被截断时给出完整文本
                nameBtn.setTooltip(Tooltip.create(Component.literal(full)));
            }
            addRenderableWidget(nameBtn);
            rowWidgets.add(nameBtn);

            Button delBtn = Button.builder(Component.literal("X"), b -> onDeleteClicked(rowSlot))
                    .bounds(delX, y, DEL_W, ROW_WIDGET_H).build();
            addRenderableWidget(delBtn);
            rowWidgets.add(delBtn);
        }
    }

    private int firstVisibleIndex() {
        return scrollOffset / ROW_HEIGHT;
    }

    private T rowAtSlot(int slot) {
        List<T> list = filteredRows();
        int index = firstVisibleIndex() + slot;
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private void onRowClicked(int slot) {
        T row = rowAtSlot(slot);
        if (row == null) {
            return;
        }
        playClick();
        minecraft.setScreen(editorFor(row));
    }

    private void onDeleteClicked(int slot) {
        T row = rowAtSlot(slot);
        if (row == null) {
            return;
        }
        playClick();
        removeRow(row);
        MinecraftServer server = minecraft == null ? null : minecraft.getSingleplayerServer();
        saveConfig(server);
        if (server != null) {
            server.execute(() -> {
                try {
                    reloadServer(server);
                } catch (Exception ignored) {
                }
            });
        }
        filteredCache = null;
        pendingRowRefresh = true;
    }

    // ==================== 绘制 ====================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        SREPanelStyle.drawPanel(g, panelLeftX - 6, panelTopY - 3, panelW + 12, panelH + 6);

        // 行背景与分隔线画在控件之前，避免盖住按钮
        List<T> list = filteredRows();
        int hovered = rowIndexAt(mouseX, mouseY);
        int start = firstVisibleIndex();
        for (int slot = 0; slot < visibleRows; slot++) {
            int index = start + slot;
            if (index >= list.size()) {
                break;
            }
            int y = listTop + slot * ROW_HEIGHT;
            if (index == hovered) {
                g.fill(rowLeft, y, rowRight, y + ROW_HEIGHT - 1, SREPanelStyle.ROW_HOVER);
            }
            g.fill(rowLeft, y + ROW_HEIGHT - 1, rowRight, y + ROW_HEIGHT, SREPanelStyle.ROW_SEPARATOR);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (pendingRowRefresh) {
            pendingRowRefresh = false;
            refreshRowWidgets();
        }
        // super.render 会先调 renderBackground 再画控件（搜索框 / 行 / 页脚）
        super.render(g, mouseX, mouseY, partialTick);

        int centerX = panelLeftX + panelW / 2;
        g.drawCenteredString(font, getTitle().copy().withStyle(s -> s.withColor(SREPanelStyle.GOLD).withBold(true)),
                centerX, panelTopY + 7, 0xFFFFFFFF);

        List<T> list = filteredRows();
        int start = firstVisibleIndex();
        int hoveredRow = rowIndexAt(mouseX, mouseY);
        // 摘要被截断且鼠标停在摘要列上时，悬停给出全文
        Component hoveredSummary = null;
        g.enableScissor(rowLeft, listTop, rowRight, listBottom);
        for (int slot = 0; slot < visibleRows; slot++) {
            int index = start + slot;
            if (index >= list.size()) {
                break;
            }
            T row = list.get(index);
            int y = listTop + slot * ROW_HEIGHT;
            Component summary = rowSummary(row);
            boolean clipped = font.width(summary) > summaryW;
            String shown = clipped ? MapUiGraphics.clip(font, summary.getString(), summaryW) : summary.getString();
            g.drawString(font, Component.literal(shown), summaryX, y + 7, rowColor(row), false);
            if (clipped && index == hoveredRow && mouseX >= summaryX && mouseX < rowRight) {
                hoveredSummary = summary;
            }
        }
        g.disableScissor();

        if (hoveredSummary != null) {
            // tooltip 不能被行区域的裁剪切掉，放在关闭裁剪之后
            g.renderTooltip(font, font.split(hoveredSummary, HintText.TOOLTIP_W), mouseX, mouseY);
        }

        if (maxScroll > 0) {
            drawScrollbar(g, mouseX, mouseY);
        }
        if (list.isEmpty()) {
            // 有内容但被过滤没了 / 真的一条都没有，两种提示分开
            boolean filtered = !filter.isEmpty() && !rows().isEmpty();
            g.drawCenteredString(font,
                    Component.translatable(filtered ? "sre.custom_content.manage.no_match" : emptyKey())
                            .withStyle(s -> s.withColor(SREPanelStyle.MUTED)),
                    centerX, listTop + listH / 2, 0xFFFFFFFF);
        }
    }

    private int rowIndexAt(double mouseX, double mouseY) {
        if (mouseX < rowLeft || mouseX >= rowRight || mouseY < listTop || mouseY >= listBottom) {
            return -1;
        }
        int slot = (int) ((mouseY - listTop) / ROW_HEIGHT);
        if (slot < 0 || slot >= visibleRows) {
            return -1;
        }
        int index = firstVisibleIndex() + slot;
        return index < filteredRows().size() ? index : -1;
    }

    // ==================== 滚动 ====================

    /** 滚动条几何：绘制与命中检测共用同一份算法。 */
    private record ScrollGeom(int x, int y, int height, int thumbY, int thumbH) {
    }

    private ScrollGeom scrollGeom() {
        int thumbH = Math.min(listH, Math.max(SREPanelStyle.SCROLL_MIN_THUMB,
                (int) (listH * Math.min(1f, (float) listH / Math.max(1, listH + maxScroll)))));
        int span = listH - thumbH;
        int thumbY = listTop + (maxScroll <= 0 || span <= 0 ? 0 : (int) ((long) span * scrollOffset / maxScroll));
        return new ScrollGeom(rowRight + 4, listTop, listH, thumbY, thumbH);
    }

    private void drawScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        ScrollGeom geom = scrollGeom();
        boolean onThumb = MapUiGraphics.isInRect(mouseX, mouseY, geom.x(), geom.thumbY(), SCROLL_W, geom.thumbH());
        boolean onTrack = MapUiGraphics.isInRect(mouseX, mouseY, geom.x(), geom.y(), SCROLL_W, geom.height());
        SREPanelStyle.drawScrollbar(g, geom.x(), geom.y(), geom.height(), geom.thumbY(), geom.thumbH(),
                onThumb || onTrack);
    }

    /** 滚动量永远是整行高的整数倍，保证行控件不会跨出列表区。 */
    private static int snap(int value) {
        return Math.floorDiv(Math.max(0, value), ROW_HEIGHT) * ROW_HEIGHT;
    }

    private void setScroll(int value) {
        int next = snap(Mth.clamp(value, 0, Math.max(0, maxScroll)));
        if (next == scrollOffset) {
            return;
        }
        scrollOffset = next;
        refreshRowWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0 && mouseX >= panelLeftX && mouseX < panelLeftX + panelW
                && mouseY >= panelTopY && mouseY < panelTopY + panelH) {
            setScroll(scrollOffset - (int) (scrollY * ROW_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll > 0) {
            ScrollGeom geom = scrollGeom();
            if (MapUiGraphics.isInRect(mouseX, mouseY, geom.x(), geom.y(), SCROLL_W, geom.height())) {
                if (mouseY >= geom.thumbY() && mouseY < geom.thumbY() + geom.thumbH()) {
                    draggingThumb = true;
                    dragStartY = (int) mouseY;
                    dragStartScroll = scrollOffset;
                } else {
                    // 点轨道：thumb 跳到该处
                    int span = geom.height() - geom.thumbH();
                    if (span > 0) {
                        int rel = Mth.clamp((int) mouseY - geom.thumbH() / 2 - geom.y(), 0, span);
                        setScroll((int) ((long) maxScroll * rel / span));
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingThumb) {
            ScrollGeom geom = scrollGeom();
            int span = geom.height() - geom.thumbH();
            if (span > 0) {
                setScroll(dragStartScroll + (int) ((long) ((int) mouseY - dragStartY) * maxScroll / span));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingThumb = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC 先清空搜索，再按一次才关界面
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && searchBox != null && !searchBox.getValue().isEmpty()) {
            searchBox.setValue("");
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== 杂项 ====================

    protected void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
