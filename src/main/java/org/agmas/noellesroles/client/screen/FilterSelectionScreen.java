package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.vertex.BufferUploader;
import io.wifi.starrailexpress.client.util.PinYinUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.function.Consumer;

/**
 * 一个通用的筛选选择界面，用于从给定选项列表中选择一个或多个条目。
 * <p>
 * 主要特性：
 * <ul>
 * <li>居中标题与支持自动换行的副标题（通过 {@code font.split}）</li>
 * <li>搜索框：支持拼音搜索（通过 {@link PinYinUtils}）和普通文本匹配</li>
 * <li>支持单选/多选模式，通过构造参数或建造者控制</li>
 * <li>列表项宽度统一，长文本自动截断并显示省略号（{@code font.plainSubstrByWidth}）</li>
 * <li>右侧滚动条支持鼠标拖拽和滚轮滚动</li>
 * <li>底部提供“取消”和“确认”按钮，点击后均返回上级页面</li>
 * <li>按 ESC 键同样返回上级页面</li>
 * <li>支持通过建造者设置默认选中的条目（{@link Builder#defaultSelections(Set)} /
 * {@link Builder#addDefaultSelection(String)}）</li>
 * <li>在多选模式下提供“全选”和“取消全选”按钮（基于当前过滤结果）</li>
 * </ul>
 * 视觉风格参考了 {@code RoleIntroduceScreen} 的深色渐变与金属质感配色。
 * </p>
 * <p>
 * 推荐使用 {@link Builder} 以流式 API 构造实例：
 * 
 * <pre>{@code
 * FilterSelectionScreen screen = FilterSelectionScreen.builder(parent)
 *         .title(Component.translatable("screen.filter_selection.title"))
 *         .subtitle(Component.literal("请选择要筛选的项目").withStyle(ChatFormatting.GRAY))
 *         .options(optionMap)
 *         .multiSelect(true)
 *         .defaultSelections(Set.of("id1", "id2"))
 *         .callback(selected -> {
 *             // 处理结果
 *         })
 *         .build();
 * }</pre>
 * </p>
 *
 * @see Screen
 * @see PinYinUtils
 * @see Builder
 */
public class FilterSelectionScreen extends Screen {

    // ── 参数 ──────────────────────────────────────────────
    private final Component titleComp;
    private final Component subtitleComp;
    private final Screen parent;
    private final LinkedHashMap<String, Component> options;
    private final boolean multiSelect;
    private final Consumer<Set<String>> callback;

    // ── 状态 ──────────────────────────────────────────────
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private List<String> filteredIds = new ArrayList<>();

    // 搜索
    private EditBox searchWidget;
    private String searchContent = null;

    // 滚动
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragStartY = 0;
    private int dragStartOffset = 0;

    // 全选/取消全选按钮
    private Button selectAllButton;
    private Button deselectAllButton;

    // 布局常量
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_SPACING = 2;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int PANEL_PAD = 6;
    private static final int TOP_BAR_H = 20;
    private static final int TITLE_HEIGHT = 16;
    private static final int SUBTITLE_TOP_OFFSET = 2;
    private static final int SEARCH_TOP_OFFSET = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ALL_BUTTON_HEIGHT = 16; // 全选按钮高度
    private static final int ALL_BUTTON_GAP = 4; // 全选按钮与列表的间距

    // 动态计算的坐标
    private int listX, listY, listW, listH;
    private int cancelX, cancelW, confirmX, confirmW;
    private int selectAllX, selectAllW, deselectAllX, deselectAllW, allButtonsY;
    private int panelWidth, panelHeight;
    private int usableWidth;

    /**
     * 私有构造函数，由建造者调用。
     */
    private FilterSelectionScreen(Component title, Component subtitle, Screen parent,
            LinkedHashMap<String, Component> options,
            boolean multiSelect,
            Consumer<Set<String>> callback,
            Set<String> defaultSelections) {
        super(title);
        this.titleComp = Objects.requireNonNull(title, "title cannot be null");
        this.subtitleComp = Objects.requireNonNull(subtitle, "subtitle cannot be null");
        this.parent = Objects.requireNonNull(parent, "parent screen cannot be null");
        this.options = Objects.requireNonNull(options, "options cannot be null");
        this.multiSelect = multiSelect;
        this.callback = Objects.requireNonNull(callback, "callback cannot be null");

        if (defaultSelections != null && !defaultSelections.isEmpty()) {
            for (String id : defaultSelections) {
                if (options.containsKey(id)) {
                    selectedIds.add(id);
                }
            }
            if (!multiSelect && selectedIds.size() > 1) {
                Iterator<String> it = selectedIds.iterator();
                String first = it.next();
                selectedIds.clear();
                selectedIds.add(first);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        initSearchBox();
        initAllButtons();
        refreshFilteredList();
    }

    private void computeLayout() {
        usableWidth = Math.min((int) (width * 0.7), 500);
        panelWidth = usableWidth;
        int top = 30;
        int bottom = 30;
        panelHeight = height - top - bottom;
        int panelX = (width - panelWidth) / 2;
        int panelY = top;

        int searchY = panelY + SUBTITLE_TOP_OFFSET + TITLE_HEIGHT + getSubtitleHeight() + SEARCH_TOP_OFFSET;
        // 为全选按钮预留空间，调整列表底部位置
        int listAreaTop = searchY + TOP_BAR_H + 6;
        int listAreaBottom = panelY + panelHeight - PANEL_PAD - BUTTON_HEIGHT - 10 - ALL_BUTTON_HEIGHT - ALL_BUTTON_GAP;
        listX = panelX + PANEL_PAD;
        listY = listAreaTop;
        listW = panelWidth - PANEL_PAD * 2 - SCROLL_W - 2;
        listH = listAreaBottom - listAreaTop;

        // 全选按钮位置：列表下方，确认按钮上方
        allButtonsY = listAreaBottom + ALL_BUTTON_GAP;
        int halfW = (listW) / 2 - 4;
        selectAllX = listX;
        selectAllW = halfW;
        deselectAllX = listX + halfW + 8;
        deselectAllW = halfW;

        // 确认取消按钮整体下移
        int buttonY = allButtonsY + ALL_BUTTON_HEIGHT + ALL_BUTTON_GAP;
        int buttonSpacing = 10;
        int buttonWidth = 80;
        confirmX = panelX + panelWidth - PANEL_PAD - buttonWidth;
        confirmW = buttonWidth;
        cancelX = confirmX - buttonWidth - buttonSpacing;
        cancelW = buttonWidth;
    }

    private int getSubtitleHeight() {
        int maxTextWidth = usableWidth - PANEL_PAD * 2 - 10;
        List<FormattedCharSequence> lines = font.split(subtitleComp, maxTextWidth);
        return lines.size() * (font.lineHeight + 2);
    }

    private void initSearchBox() {
        int sx = (width - usableWidth) / 2 + PANEL_PAD;
        int sy = 30 + SUBTITLE_TOP_OFFSET + TITLE_HEIGHT + getSubtitleHeight() + SEARCH_TOP_OFFSET;
        int sw = usableWidth - PANEL_PAD * 2;

        if (searchWidget == null) {
            searchWidget = new EditBox(font, sx, sy, sw, TOP_BAR_H, Component.empty());
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder"));
            searchWidget.setResponder(text -> {
                searchContent = text.isEmpty() ? null : text;
                scrollOffset = 0;
                refreshFilteredList();
            });
        } else {
            searchWidget.setPosition(sx, sy);
            searchWidget.setWidth(sw);
        }
        addRenderableWidget(searchWidget);
    }

    private void initAllButtons() {
        // 移除旧按钮（如果存在）
        if (selectAllButton != null)
            removeWidget(selectAllButton);
        if (deselectAllButton != null)
            removeWidget(deselectAllButton);

        selectAllButton = Button.builder(
                Component.translatable("screen.filter_selection.select_all"),
                btn -> {
                    if (multiSelect) {
                        playClickSound();
                        selectedIds.addAll(filteredIds);
                    }
                })
                .bounds(selectAllX, allButtonsY, selectAllW, ALL_BUTTON_HEIGHT)
                .build();
        deselectAllButton = Button.builder(
                Component.translatable("screen.filter_selection.deselect_all"),
                btn -> {
                    if (multiSelect) {
                        playClickSound();
                        selectedIds.clear();
                    }
                })
                .bounds(deselectAllX, allButtonsY, deselectAllW, ALL_BUTTON_HEIGHT)
                .build();

        // 单选模式下禁用全选按钮
        if (!multiSelect) {
            selectAllButton.active = false;
            deselectAllButton.active = false;
        }

        addRenderableWidget(selectAllButton);
        addRenderableWidget(deselectAllButton);
    }

    private void playClickSound() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    private void refreshFilteredList() {
        filteredIds.clear();
        for (String id : options.keySet()) {
            Component nameComp = options.get(id);
            String name = nameComp.getString();
            if (searchContent == null ||
                    name.toLowerCase().contains(searchContent.toLowerCase()) ||
                    id.toLowerCase().contains(searchContent.toLowerCase()) ||
                    PinYinUtils.contains(searchContent, name)) {
                filteredIds.add(id);
            }
        }
        if (!multiSelect && selectedIds.size() > 1) {
            String keep = selectedIds.iterator().next();
            selectedIds.clear();
            selectedIds.add(keep);
        }
        updateScrollParams();
    }

    private void updateScrollParams() {
        int totalH = filteredIds.size() * (ROW_HEIGHT + ROW_SPACING) - ROW_SPACING;
        maxScroll = Math.max(0, totalH - listH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    @Override
    public void onClose() {
        if (this.minecraft == null)
            return;
        this.minecraft.screen = parent;
        this.removed();
        if (this.minecraft.screen != null) {
            this.minecraft.screen.added();
        }
        BufferUploader.reset();
        if (this.minecraft.screen != null) {
            this.minecraft.mouseHandler.releaseMouse();
            KeyMapping.releaseAll();
            this.minecraft.noRender = false;
        } else {
            this.minecraft.getSoundManager().resume();
            this.minecraft.mouseHandler.grabMouse();
        }
        this.minecraft.updateTitle();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && parent != null) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ═══════════════════════════════════════════════════
    // 渲染
    // ═══════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.parent.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        int panelX = (width - panelWidth) / 2;
        int panelY = 30;

        drawPanelBg(g, panelX, panelY, panelWidth, panelHeight);

        int titleY = panelY + 4;
        Component safeTitle = titleComp != null ? titleComp : Component.empty();
        g.drawCenteredString(font, safeTitle, width / 2, titleY, 0xF5E8C8);

        int subX = panelX + PANEL_PAD + 4;
        int subY = titleY + TITLE_HEIGHT + SUBTITLE_TOP_OFFSET;
        int subMaxW = usableWidth - PANEL_PAD * 2 - 8;
        Component safeSubtitle = subtitleComp != null ? subtitleComp : Component.empty();
        for (FormattedCharSequence line : font.split(safeSubtitle, subMaxW)) {
            g.drawString(font, line, subX, subY, 0x9E8B6E);
            subY += font.lineHeight + 2;
        }

        renderList(g, mouseX, mouseY);
        renderButtons(g, mouseX, mouseY);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < filteredIds.size(); i++) {
            String id = filteredIds.get(i);
            int rowY = listY + i * (ROW_HEIGHT + ROW_SPACING) - scrollOffset;
            if (rowY + ROW_HEIGHT < listY || rowY > listY + listH)
                continue;

            boolean hovered = isInRect(mouseX, mouseY, listX, rowY, listW, ROW_HEIGHT);
            boolean selected = selectedIds.contains(id);
            drawRow(g, id, rowY, selected, hovered);
        }
        g.disableScissor();

        int sbX = listX + listW + 2;
        int totalH = Math.max(1, filteredIds.size() * (ROW_HEIGHT + ROW_SPACING));
        renderVScrollbar(g, sbX, listY, listH, scrollOffset, maxScroll, totalH, mouseX, mouseY, isDraggingScroll);
    }

    private void drawRow(GuiGraphics g, String id, int y, boolean selected, boolean hovered) {
        int bgColor = selected ? 0xFF5A4520 : (hovered ? 0xFF2A2A15 : 0xFF1A1008);
        g.fill(listX, y, listX + listW, y + ROW_HEIGHT, bgColor);
        g.fill(listX, y + ROW_HEIGHT - 1, listX + listW, y + ROW_HEIGHT, 0x228B6914);

        int checkX = listX + 4;
        int checkY = y + (ROW_HEIGHT - 9) / 2;
        drawCheckbox(g, checkX, checkY, selected);

        Component nameComp = options.get(id);
        int textX = checkX + 12 + 4;
        int maxTextW = listW - (textX - listX) - 4;
        String display = nameComp != null ? font.plainSubstrByWidth(nameComp.getString(), maxTextW) : "";
        int textColor = selected ? 0xFFF5E8C8 : (hovered ? 0xFFFFF4DC : 0xFFC8B78A);
        g.drawString(font, display, textX, y + (ROW_HEIGHT - font.lineHeight) / 2, textColor);
    }

    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        int size = 10;
        if (checked) {
            g.fill(x, y, x + size, y + size, 0xFF2A1A0A);
            g.drawCenteredString(font, Component.literal("√"), x + size / 2, y + size / 2 - font.lineHeight / 2,
                    0xFFB8960C);
            g.renderOutline(x, y, size, size, 0xFFB8960C);
        } else {
            g.fill(x, y, x + size, y + size, 0xFF2A1A0A);
            g.renderOutline(x, y, size, size, 0xFF8B6914);
        }
    }

    private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
        int cy = allButtonsY + ALL_BUTTON_HEIGHT + ALL_BUTTON_GAP;
        drawButton(g, cancelX, cy, cancelW, BUTTON_HEIGHT, CommonComponents.GUI_CANCEL, mouseX, mouseY);
        drawButton(g, confirmX, cy, confirmW, BUTTON_HEIGHT, Component.translatable("gui.done"), mouseX, mouseY);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component text, int mouseX, int mouseY) {
        boolean hovered = isInRect(mouseX, mouseY, x, y, w, h);
        int bg = hovered ? 0xFF5A4520 : 0xFF3A2A10;
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, 0xFF8B6914);
        g.drawCenteredString(font, text, x + w / 2, y + (h - font.lineHeight) / 2, 0xFFFFFFFF);
    }

    // ═══════════════════════════════════════════════════
    // 鼠标交互
    // ═══════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // 列表项点击
            if (isInRect((int) mx, (int) my, listX, listY, listW, listH)) {
                for (int i = 0; i < filteredIds.size(); i++) {
                    int rowY = listY + i * (ROW_HEIGHT + ROW_SPACING) - scrollOffset;
                    if (isInRect((int) mx, (int) my, listX, rowY, listW, ROW_HEIGHT)) {
                        playClickSound();
                        String id = filteredIds.get(i);
                        if (multiSelect) {
                            if (selectedIds.contains(id))
                                selectedIds.remove(id);
                            else
                                selectedIds.add(id);
                        } else {
                            selectedIds.clear();
                            selectedIds.add(id);
                        }
                        return true;
                    }
                }
            }

            // 滚动条
            int sbX = listX + listW + 2;
            if (isInRect((int) mx, (int) my, sbX, listY, SCROLL_W, listH) && maxScroll > 0) {
                isDraggingScroll = true;
                dragStartY = my;
                dragStartOffset = scrollOffset;
                return true;
            }

            // 确认/取消按钮由 super 处理，这里我们不用重复，直接交给 super.mouseClicked
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingScroll && maxScroll > 0) {
            int totalH = Math.max(1, filteredIds.size() * (ROW_HEIGHT + ROW_SPACING));
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (listH * Math.min(1f, (float) listH / totalH)));
            double trackH = listH - thumbH;
            if (trackH > 0)
                scrollOffset = Mth.clamp((int) (dragStartOffset + (my - dragStartY) / trackH * maxScroll), 0,
                        maxScroll);
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
        if (isInRect((int) mx, (int) my, listX, listY, listW, listH)) {
            scrollOffset = Mth.clamp((int) (scrollOffset - scrollY * (ROW_HEIGHT + ROW_SPACING)), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ═══════════════════════════════════════════════════
    // 工具
    // ═══════════════════════════════════════════════════

    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xD81A1008, 0xD820140A);
        g.renderOutline(x, y, w, h, 0xFF8B6914);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFE8C0);
    }

    private void renderVScrollbar(GuiGraphics g, int x, int y, int h,
            int scroll, int maxScroll, int totalContentH,
            int mouseX, int mouseY, boolean dragging) {
        g.fill(x, y, x + SCROLL_W, y + h, 0xFF1A1008);
        g.fill(x + 1, y + 1, x + SCROLL_W - 1, y + h - 1, 0x558B6914);
        if (maxScroll <= 0)
            return;

        float ratio = Math.min(1f, (float) h / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) scroll / maxScroll));
        boolean hl = dragging || isInRect(mouseX, mouseY, x, thumbY, SCROLL_W, thumbH);

        g.fill(x, thumbY, x + SCROLL_W, thumbY + thumbH, hl ? 0xFFC9A84C : 0xFF8B6914);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + thumbH - 1, hl ? 0xFFD4AF37 : 0xFFB8960C);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    // ═══════════════════════════════════════════════════
    // 建造者
    // ═══════════════════════════════════════════════════

    public static Builder builder(Screen parent) {
        return new Builder(parent);
    }

    /**
     * 用于逐步配置 {@link FilterSelectionScreen} 的建造者。
     */
    public static class Builder {
        private final Screen parent;
        private Component title = Component.empty();
        private Component subtitle = Component.empty();
        private LinkedHashMap<String, Component> options = new LinkedHashMap<>();
        private boolean multiSelect = false;
        private Consumer<Set<String>> callback = ids -> {
        };
        private Set<String> defaultSelections = new LinkedHashSet<>();

        public Builder(Screen parent) {
            this.parent = Objects.requireNonNull(parent, "parent screen cannot be null");
        }

        public Builder title(Component title) {
            this.title = title != null ? title : Component.empty();
            return this;
        }

        public Builder subtitle(Component subtitle) {
            this.subtitle = subtitle != null ? subtitle : Component.empty();
            return this;
        }

        public Builder options(Map<String, Component> options) {
            this.options = options != null ? new LinkedHashMap<>(options) : new LinkedHashMap<>();
            return this;
        }

        public Builder addOption(String id, Component displayName) {
            this.options.put(id, displayName);
            return this;
        }

        public Builder multiSelect(boolean multiSelect) {
            this.multiSelect = multiSelect;
            return this;
        }

        public Builder callback(Consumer<Set<String>> callback) {
            this.callback = Objects.requireNonNull(callback, "callback cannot be null");
            return this;
        }

        public Builder defaultSelections(Set<String> selections) {
            this.defaultSelections = selections != null ? new LinkedHashSet<>(selections) : new LinkedHashSet<>();
            return this;
        }

        public Builder addDefaultSelection(String id) {
            this.defaultSelections.add(id);
            return this;
        }

        public FilterSelectionScreen build() {
            return new FilterSelectionScreen(title, subtitle, parent, options, multiSelect, callback,
                    defaultSelections);
        }
    }

    /**
     * 安全地显示此屏幕，不会误删除父窗口。
     */
    public void show(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft cannot be null");
        minecraft.screen = this;
        this.added();
        BufferUploader.reset();
        minecraft.mouseHandler.releaseMouse();
        KeyMapping.releaseAll();
        this.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        minecraft.noRender = false;
    }
}