package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.vertex.BufferUploader;
import io.wifi.starrailexpress.client.util.PinYinUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
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
 * 一个通用的筛选选择界面，支持键盘导航、多选/单选、拼音搜索等。
 * 视觉风格参考了 RoleIntroduceScreen 的深色渐变与金属质感配色。
 */
public class FilterSelectionScreen extends Screen {

    private final Component titleComp;
    private final Component subtitleComp;
    private final Screen parent;
    private final LinkedHashMap<String, Component> options;
    private final boolean multiSelect;
    private final Consumer<Set<String>> callback;

    private final Set<String> selectedIds = new LinkedHashSet<>();
    private List<String> filteredIds = new ArrayList<>();
    private int highlightedIndex = 0;

    private EditBox searchWidget;
    private String searchContent = null;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragStartY = 0;
    private int dragStartOffset = 0;

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
    private static final int BUTTON_GAP = 4;
    private static final int MAX_BUTTON_WIDTH = 100;

    // 动态坐标
    private int listX, listY, listW, listH;
    private int selectAllX, selectAllW;
    private int deselectAllX, deselectAllW;
    private int cancelX, cancelW;
    private int confirmX, confirmW;
    private int buttonsY;
    private int panelWidth, panelHeight;
    private int usableWidth;

    @Override
    public void resize(Minecraft minecraft, int i, int j) {
        super.resize(minecraft, i, j);
        this.parent.resize(minecraft, i, j);
    }

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
        clearWidgets();
        computeLayout();
        initSearchBox();
        initButtons();
        refreshFilteredList();
        if (!filteredIds.isEmpty())
            highlightedIndex = 0;
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
        int listAreaTop = searchY + TOP_BAR_H + 6;
        int listAreaBottom = panelY + panelHeight - PANEL_PAD - BUTTON_HEIGHT - 10;
        listX = panelX + PANEL_PAD;
        listY = listAreaTop;
        listW = panelWidth - PANEL_PAD * 2 - SCROLL_W - 2;
        listH = listAreaBottom - listAreaTop;

        buttonsY = listAreaBottom + 8;

        int buttonCount = 4;
        int totalGap = BUTTON_GAP * (buttonCount - 1);
        int totalNaturalWidth = buttonCount * MAX_BUTTON_WIDTH + totalGap;

        int btnW;
        if (totalNaturalWidth <= listW) {
            btnW = MAX_BUTTON_WIDTH;
            int totalButtonsW = buttonCount * btnW + totalGap;
            int startX = listX + listW - totalButtonsW;
            selectAllX = startX;
            deselectAllX = selectAllX + btnW + BUTTON_GAP;
            cancelX = deselectAllX + btnW + BUTTON_GAP;
            confirmX = cancelX + btnW + BUTTON_GAP;
        } else {
            int usableButtonArea = listW - totalGap;
            btnW = Math.max(1, usableButtonArea / buttonCount);
            int totalButtonsW = buttonCount * btnW + totalGap;
            int startX = listX;
            selectAllX = startX;
            deselectAllX = selectAllX + btnW + BUTTON_GAP;
            cancelX = deselectAllX + btnW + BUTTON_GAP;
            confirmX = cancelX + btnW + BUTTON_GAP;
        }
        selectAllW = btnW;
        deselectAllW = btnW;
        cancelW = btnW;
        confirmW = btnW;
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
        setFocused(searchWidget);
    }

    private void initButtons() {
        addRenderableWidget(new StyledButton(selectAllX, buttonsY, selectAllW, BUTTON_HEIGHT,
                Component.translatable("screen.filter_selection.select_all"),
                btn -> {
                    if (multiSelect) {
                        playClickSound();
                        selectedIds.addAll(filteredIds);
                    }
                },
                multiSelect));

        addRenderableWidget(new StyledButton(deselectAllX, buttonsY, deselectAllW, BUTTON_HEIGHT,
                Component.translatable("screen.filter_selection.deselect_all"),
                btn -> {
                    if (multiSelect) {
                        playClickSound();
                        selectedIds.clear();
                    }
                },
                multiSelect));

        addRenderableWidget(new StyledButton(cancelX, buttonsY, cancelW, BUTTON_HEIGHT,
                CommonComponents.GUI_CANCEL,
                btn -> {
                    playClickSound();
                    onClose();
                },
                true));

        addRenderableWidget(new StyledButton(confirmX, buttonsY, confirmW, BUTTON_HEIGHT,
                Component.translatable("gui.done"),
                btn -> {
                    playClickSound();
                    callback.accept(new LinkedHashSet<>(selectedIds));
                    onClose();
                },
                true));
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
        highlightedIndex = filteredIds.isEmpty() ? 0 : Math.min(highlightedIndex, filteredIds.size() - 1);
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

    // ========== 键盘导航修复 ==========
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && parent != null) {
            onClose();
            return true;
        }

        // 如果焦点在搜索框，按下导航键(↑↓Enter)时将焦点移出搜索框，以便列表可以接收导航事件
        if (getFocused() == searchWidget) {
            if (keyCode == 265 || keyCode == 264 || keyCode == 257 || keyCode == 335) {
                searchWidget.setFocused(false);
                setFocused(null);
            }
        }

        // 列表键盘导航：仅当焦点不在搜索框且不在任何按钮上时
        if (getFocused() != searchWidget && !isFocusedOnButton()) {
            if (keyCode == 265 || keyCode == 264) { // ↑ ↓
                if (!filteredIds.isEmpty()) {
                    highlightedIndex = keyCode == 265 ? Math.max(0, highlightedIndex - 1)
                            : Math.min(filteredIds.size() - 1, highlightedIndex + 1);
                    int rowY = highlightedIndex * (ROW_HEIGHT + ROW_SPACING);
                    if (rowY < scrollOffset)
                        scrollOffset = rowY;
                    else if (rowY + ROW_HEIGHT > scrollOffset + listH)
                        scrollOffset = rowY + ROW_HEIGHT - listH;
                    scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
                    return true;
                }
            } else if (keyCode == 257 || keyCode == 335) { // Enter
                if (!filteredIds.isEmpty() && highlightedIndex < filteredIds.size()) {
                    playClickSound();
                    String id = filteredIds.get(highlightedIndex);
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

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isFocusedOnButton() {
        return getFocused() instanceof StyledButton;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        this.renderBlurredBackground(f);
        this.renderMenuBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.parent.render(g, mouseX, mouseY, partialTick);

        int panelX = (width - panelWidth) / 2;
        int panelY = 30;
        super.render(g, mouseX, mouseY, partialTick);

        drawPanelBg(g, panelX, panelY, panelWidth, panelHeight);

        int titleY = panelY + 4;
        g.drawCenteredString(font, titleComp, width / 2, titleY, 0xF5E8C8);

        int subX = panelX + PANEL_PAD + 4;
        int subY = titleY + TITLE_HEIGHT + SUBTITLE_TOP_OFFSET;
        int subMaxW = usableWidth - PANEL_PAD * 2 - 8;
        for (FormattedCharSequence line : font.split(subtitleComp, subMaxW)) {
            g.drawString(font, line, subX, subY, 0x9E8B6E);
            subY += font.lineHeight + 2;
        }

        renderList(g, mouseX, mouseY);
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
            boolean highlighted = (i == highlightedIndex && !isFocusedOnButton() && getFocused() != searchWidget);
            drawRow(g, id, rowY, selected, hovered, highlighted);
        }
        g.disableScissor();

        int sbX = listX + listW + 2;
        int totalH = Math.max(1, filteredIds.size() * (ROW_HEIGHT + ROW_SPACING));
        renderVScrollbar(g, sbX, listY, listH, scrollOffset, maxScroll, totalH, mouseX, mouseY, isDraggingScroll);
    }

    private void drawRow(GuiGraphics g, String id, int y, boolean selected, boolean hovered, boolean highlighted) {
        int bgColor;
        if (selected)
            bgColor = 0xFF5A4520;
        else if (highlighted)
            bgColor = 0xFF4A3A20;
        else if (hovered)
            bgColor = 0xFF2A2A15;
        else
            bgColor = 0xFF1A1008;

        g.fill(listX, y, listX + listW, y + ROW_HEIGHT, bgColor);
        g.fill(listX, y + ROW_HEIGHT - 1, listX + listW, y + ROW_HEIGHT, 0x228B6914);

        if (highlighted) {
            g.renderOutline(listX, y, listW, ROW_HEIGHT, 0xCCD4AF37);
        }

        int checkX = listX + 4;
        int checkY = y + (ROW_HEIGHT - 9) / 2;
        drawCheckbox(g, checkX, checkY, selected);

        Component nameComp = options.get(id);
        int textX = checkX + 12 + 4;
        int maxTextW = listW - (textX - listX) - 4;
        String display = font.plainSubstrByWidth(nameComp.getString(), maxTextW);
        int textColor = selected ? 0xFFF5E8C8 : (highlighted ? 0xFFFFF4DC : (hovered ? 0xFFFFF4DC : 0xFFC8B78A));
        g.drawString(font, display, textX, y + (ROW_HEIGHT - font.lineHeight) / 2, textColor);
    }

    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        int size = 10;
        if (checked) {
            g.fill(x, y, x + size, y + size, 0xFF2A1A0A);
            g.drawCenteredString(font, Component.literal("✔"), x + size / 2, y + size / 2 - font.lineHeight / 2,
                    0xFFB8960C);
            g.renderOutline(x, y, size, size, 0xFFB8960C);
        } else {
            g.fill(x, y, x + size, y + size, 0xFF2A1A0A);
            g.renderOutline(x, y, size, size, 0xFF8B6914);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (isInRect((int) mx, (int) my, listX, listY, listW, listH)) {
                for (int i = 0; i < filteredIds.size(); i++) {
                    int rowY = listY + i * (ROW_HEIGHT + ROW_SPACING) - scrollOffset;
                    if (isInRect((int) mx, (int) my, listX, rowY, listW, ROW_HEIGHT)) {
                        playClickSound();
                        highlightedIndex = i;
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
                        setFocused(null);
                        return true;
                    }
                }
            }

            int sbX = listX + listW + 2;
            if (isInRect((int) mx, (int) my, sbX, listY, SCROLL_W, listH) && maxScroll > 0) {
                isDraggingScroll = true;
                dragStartY = my;
                dragStartOffset = scrollOffset;
                return true;
            }
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

    // 自定义按钮
    private class StyledButton extends Button {
        private final boolean active;

        StyledButton(int x, int y, int width, int height, Component message,
                OnPress onPress, boolean active) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.active = active;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hovered = active && isMouseOver(mouseX, mouseY);
            boolean focused = isFocused();
            int bg = active ? (hovered ? 0xFF5A4520 : (focused ? 0xFF4A3A20 : 0xFF3A2A10)) : 0xFF1A1008;
            int textColor = active ? (hovered ? 0xFFFFF4DC : (focused ? 0xFFFFF4DC : 0xFFC8B78A)) : 0x666666;
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            if (focused) {
                g.renderOutline(getX(), getY(), width, height, 0xCCD4AF37);
            } else {
                g.renderOutline(getX(), getY(), width, height, active ? 0xFF8B6914 : 0xFF5A4530);
            }
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - font.lineHeight) / 2,
                    textColor);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        }
    }

    // 建造者
    public static Builder builder(Screen parent) {
        return new Builder(parent);
    }

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