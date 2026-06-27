package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.client.util.PinYinUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
import java.util.function.Function;

import com.mojang.blaze3d.vertex.BufferUploader;

/**
 * 一个通用的筛选选择界面，用于从给定选项列表中选择一个或多个条目。
 * <p>
 * 主要特性：
 * <ul>
 * <li>居中标题与支持自动换行的副标题（通过 {@code font.split}）</li>
 * <li>搜索框：支持拼音搜索（通过 {@link PinYinUtils}）和普通文本匹配</li>
 * <li>支持单选/多选模式，由构造参数控制</li>
 * <li>列表项宽度统一，长文本自动截断并显示省略号（{@code font.plainSubstrByWidth}）</li>
 * <li>右侧滚动条支持鼠标拖拽和滚轮滚动</li>
 * <li>底部提供“取消”和“确认”按钮，点击后均返回上级页面</li>
 * <li>按 ESC 键同样返回上级页面</li>
 * </ul>
 * 视觉风格参考了 {@code RoleIntroduceScreen} 的深色渐变与金属质感配色。
 * </p>
 * <p>
 * 推荐使用 {@link Builder} 以流式 API 构造实例：
 * 
 * <pre>{@code
 * FilterSelectionScreen screen = FilterSelectionScreen.builder(parent)
 *         .title(Component.translatable("gui.filter.title"))
 *         .subtitle(Component.literal("请选择要筛选的项目").withStyle(ChatFormatting.GRAY))
 *         .options(optionMap)
 *         .multiSelect(true)
 *         .callback(selected -> {
 *             // 处理结果
 *             return null;
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
    private final LinkedHashMap<String, Component> options; // 保持顺序
    private final boolean multiSelect;
    private final Consumer<Set<String>> callback;

    // ── 状态 ──────────────────────────────────────────────
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private List<String> filteredIds = new ArrayList<>(); // 当前显示条目的 ID 列表

    // 搜索
    private EditBox searchWidget;
    private String searchContent = null;

    // 滚动
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isDraggingScroll = false;
    private double dragStartY = 0;
    private int dragStartOffset = 0;

    // 布局常量
    private static final int ROW_HEIGHT = 22; // 每个选项的高度
    private static final int ROW_SPACING = 2; // 间距
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int PANEL_PAD = 6;
    private static final int TOP_BAR_H = 20; // 搜索框高度
    private static final int TITLE_HEIGHT = 16; // 标题行高（估计）
    private static final int SUBTITLE_TOP_OFFSET = 2; // 副标题距标题距离
    private static final int SEARCH_TOP_OFFSET = 6; // 搜索框距副标题区域距离
    private static final int BUTTON_HEIGHT = 20;

    // 动态计算的坐标
    private int listX, listY, listW, listH; // 列表区域
    private int cancelX, cancelW, confirmX, confirmW; // 按钮区域
    private int panelWidth, panelHeight;
    private int usableWidth;

    /**
     * 构造一个新的筛选选择界面（私有，通过建造者调用）。
     *
     * @param title       界面的主标题 {@link Component}，不可为 {@code null}
     * @param subtitle    副标题/提示文本 {@link Component}，不可为 {@code null}
     * @param parent      父级屏幕，用于 ESC 或按钮点击后返回，不可为 {@code null}
     * @param options     选项映射（ID -> 显示名称），顺序由传入的 {@link LinkedHashMap} 保证，不可为
     *                    {@code null}
     * @param multiSelect 是否允许多选；{@code true} 为多选，{@code false} 为单选
     * @param callback    确认后的回调函数，参数为选中 ID 的集合（单选时集合只包含一个元素），不可为 {@code null}
     */
    private FilterSelectionScreen(Component title, Component subtitle, Screen parent,
            LinkedHashMap<String, Component> options,
            boolean multiSelect,
            Consumer<Set<String>> callback) {
        super(title);
        this.titleComp = Objects.requireNonNull(title, "title cannot be null");
        this.subtitleComp = Objects.requireNonNull(subtitle, "subtitle cannot be null");
        this.parent = Objects.requireNonNull(parent, "parent screen cannot be null");
        this.options = Objects.requireNonNull(options, "options cannot be null");
        this.multiSelect = multiSelect;
        this.callback = Objects.requireNonNull(callback, "callback cannot be null");
    }

    /**
     * 初始化界面，计算布局并添加搜索框等组件。
     */
    @Override
    protected void init() {
        super.init();
        computeLayout();
        initSearchBox();
        refreshFilteredList();
    }

    /**
     * 根据当前窗口大小计算各个区域的坐标和尺寸。
     */
    private void computeLayout() {
        // 整体面板居中，宽度为屏幕宽度的 70%，最大 500
        usableWidth = Math.min((int) (width * 0.7), 500);
        panelWidth = usableWidth;
        // 总高度：标题区 + 搜索框 + 列表区 + 按钮区，上边距 30，下边距 30
        int top = 30;
        int bottom = 30;
        panelHeight = height - top - bottom;
        int panelX = (width - panelWidth) / 2;
        int panelY = top;

        // 搜索框 Y 坐标：紧接在副标题区域之下
        int searchY = panelY + SUBTITLE_TOP_OFFSET + TITLE_HEIGHT + getSubtitleHeight() + SEARCH_TOP_OFFSET;
        // 列表区域
        int listAreaTop = searchY + TOP_BAR_H + 6;
        int listAreaBottom = panelY + panelHeight - PANEL_PAD - BUTTON_HEIGHT - 10;
        listX = panelX + PANEL_PAD;
        listY = listAreaTop;
        listW = panelWidth - PANEL_PAD * 2 - SCROLL_W - 2;
        listH = listAreaBottom - listAreaTop;

        // 按钮
        int buttonY = listAreaBottom + 4;
        int buttonSpacing = 10;
        int buttonWidth = 80;
        confirmX = panelX + panelWidth - PANEL_PAD - buttonWidth;
        confirmW = buttonWidth;
        cancelX = confirmX - buttonWidth - buttonSpacing;
        cancelW = buttonWidth;
    }

    /**
     * 计算副标题自动换行后的总高度。
     *
     * @return 副标题文本块的实际像素高度
     */
    private int getSubtitleHeight() {
        int maxTextWidth = usableWidth - PANEL_PAD * 2 - 10;
        List<FormattedCharSequence> lines = font.split(subtitleComp, maxTextWidth);
        return lines.size() * (font.lineHeight + 2);
    }

    /**
     * 初始化搜索框，设置其大小、位置和响应回调。
     */
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

    /**
     * 根据当前搜索词和拼音过滤选项列表，并更新滚动参数。
     * <p>
     * 过滤逻辑：
     * <ul>
     * <li>若搜索框为空，则显示全部选项；</li>
     * <li>否则，分别匹配选项显示文本、选项 ID 和拼音（通过 {@link PinYinUtils#contains}）。</li>
     * </ul>
     * </p>
     */
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
        // 自动处理多选限制（如果是单选，保留第一个已选的）
        if (!multiSelect && selectedIds.size() > 1) {
            String keep = selectedIds.iterator().next();
            selectedIds.clear();
            selectedIds.add(keep);
        }
        updateScrollParams();
    }

    /**
     * 根据过滤后的列表项数量和列表区域高度，计算最大滚动值，并钳制当前滚动偏移。
     */
    private void updateScrollParams() {
        int totalH = filteredIds.size() * (ROW_HEIGHT + ROW_SPACING) - ROW_SPACING;
        maxScroll = Math.max(0, totalH - listH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    /**
     * 关闭当前界面，返回父级屏幕。同时作为 ESC 和取消按钮的默认行为。
     */
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

    /**
     * 处理键盘按键事件，包括 ESC 返回上级。
     *
     * @param keyCode   按键码
     * @param scanCode  扫描码
     * @param modifiers 修饰键（如 Shift、Ctrl）
     * @return 是否已消费该事件
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC 返回上级
        if (keyCode == 256 && parent != null) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ═══════════════════════════════════════════════════
    // 渲染
    // ═══════════════════════════════════════════════════

    /**
     * 绘制整个筛选界面，包括背景面板、标题、副标题、列表、按钮。
     *
     * @param g           GUI 绘制上下文
     * @param mouseX      鼠标 X 坐标
     * @param mouseY      鼠标 Y 坐标
     * @param partialTick 帧间插值 tick
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int panelX = (width - panelWidth) / 2;
        int panelY = 30;

        // ── 面板背景 ──────────────────────────────────
        drawPanelBg(g, panelX, panelY, panelWidth, panelHeight);

        // ── 标题与副标题 ──────────────────────────────
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

        // ── 列表 ──────────────────────────────────────
        renderList(g, mouseX, mouseY);

        // ── 按钮 ──────────────────────────────────────
        renderButtons(g, mouseX, mouseY);
    }

    /**
     * 渲染可滚动列表区域，包含所有过滤后的行和右侧滚动条。
     *
     * @param g      GUI 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     */
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

        // 滚动条
        int sbX = listX + listW + 2;
        int totalH = Math.max(1, filteredIds.size() * (ROW_HEIGHT + ROW_SPACING));
        renderVScrollbar(g, sbX, listY, listH, scrollOffset, maxScroll, totalH, mouseX, mouseY, isDraggingScroll);
    }

    /**
     * 绘制列表中的一行，包括背景、复选框和截断后的文本。
     *
     * @param g        GUI 绘制上下文
     * @param id       选项 ID
     * @param y        行的 Y 坐标
     * @param selected 是否已选中
     * @param hovered  鼠标是否悬停在该行上
     */
    private void drawRow(GuiGraphics g, String id, int y, boolean selected, boolean hovered) {
        // 背景
        int bgColor = selected ? 0xFF5A4520 : (hovered ? 0xFF2A2A15 : 0xFF1A1008);
        g.fill(listX, y, listX + listW, y + ROW_HEIGHT, bgColor);
        // 底部亮线
        g.fill(listX, y + ROW_HEIGHT - 1, listX + listW, y + ROW_HEIGHT, 0x228B6914);

        // 复选框
        int checkX = listX + 4;
        int checkY = y + (ROW_HEIGHT - 9) / 2;
        drawCheckbox(g, checkX, checkY, selected);

        // 文本
        Component nameComp = options.get(id);
        int textX = checkX + 12 + 4;
        int maxTextW = listW - (textX - listX) - 4;
        // nameComp 不会为 null，但兜底处理
        String display = nameComp != null ? font.plainSubstrByWidth(nameComp.getString(), maxTextW) : "";
        int textColor = selected ? 0xFFF5E8C8 : (hovered ? 0xFFFFF4DC : 0xFFC8B78A);
        g.drawString(font, display, textX, y + (ROW_HEIGHT - font.lineHeight) / 2, textColor);
    }

    /**
     * 绘制一个简易的复选框。
     *
     * @param g       GUI 绘制上下文
     * @param x       复选框左上角 X 坐标
     * @param y       复选框左上角 Y 坐标
     * @param checked 是否已勾选
     */
    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        int size = 10;
        g.fill(x, y, x + size, y + size, 0xFF2A1A0A);
        g.renderOutline(x, y, size, size, 0xFF8B6914);
        if (checked) {
            // 简单的对勾绘制
            g.fill(x + 2, y + 4, x + 4, y + size - 2, 0xFFD4AF37);
            g.fill(x + 4, y + size - 4, x + size - 2, y + 2, 0xFFD4AF37);
        }
    }

    /**
     * 渲染底部的“取消”和“确认”按钮。
     *
     * @param g      GUI 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     */
    private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
        int cy = listY + listH + 4;
        drawButton(g, cancelX, cy, cancelW, BUTTON_HEIGHT, CommonComponents.GUI_CANCEL, mouseX, mouseY);
        drawButton(g, confirmX, cy, confirmW, BUTTON_HEIGHT, Component.translatable("gui.done"), mouseX, mouseY);
    }

    /**
     * 绘制一个按钮，带有悬停高亮效果。
     *
     * @param g      GUI 绘制上下文
     * @param x      按钮左上角 X 坐标
     * @param y      按钮左上角 Y 坐标
     * @param w      按钮宽度
     * @param h      按钮高度
     * @param text   按钮显示的文本
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     */
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

    /**
     * 处理鼠标点击事件，包括列表项选中、滚动条拖拽开始和按钮点击。
     *
     * @param mx     鼠标 X 坐标
     * @param my     鼠标 Y 坐标
     * @param button 鼠标按钮（0 为左键）
     * @return 是否消费了该事件
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // 列表项点击
            if (isInRect((int) mx, (int) my, listX, listY, listW, listH)) {
                for (int i = 0; i < filteredIds.size(); i++) {
                    int rowY = listY + i * (ROW_HEIGHT + ROW_SPACING) - scrollOffset;
                    if (isInRect((int) mx, (int) my, listX, rowY, listW, ROW_HEIGHT)) {

                        this.minecraft.getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
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

            // 滚动条按下
            int sbX = listX + listW + 2;
            if (isInRect((int) mx, (int) my, sbX, listY, SCROLL_W, listH) && maxScroll > 0) {
                isDraggingScroll = true;
                dragStartY = my;
                dragStartOffset = scrollOffset;
                return true;
            }

            // 取消按钮
            if (isInRect((int) mx, (int) my, cancelX, listY + listH + 4, cancelW, BUTTON_HEIGHT)) {

                this.minecraft.getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                onClose();
                return true;
            }
            // 确认按钮
            if (isInRect((int) mx, (int) my, confirmX, listY + listH + 4, confirmW, BUTTON_HEIGHT)) {

                this.minecraft.getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                if (callback != null) {
                    callback.accept(new LinkedHashSet<>(selectedIds));
                }
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /**
     * 处理鼠标拖拽事件，用于滚动条拖拽。
     *
     * @param mx     鼠标 X 坐标
     * @param my     鼠标 Y 坐标
     * @param button 鼠标按钮
     * @param dx     水平移动量
     * @param dy     垂直移动量
     * @return 是否消费了该事件
     */
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

    /**
     * 处理鼠标释放事件，结束滚动条拖拽状态。
     *
     * @param mx     鼠标 X 坐标
     * @param my     鼠标 Y 坐标
     * @param button 鼠标按钮
     * @return 是否消费了该事件
     */
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    /**
     * 处理鼠标滚轮事件，用于列表滚动。
     *
     * @param mx      鼠标 X 坐标
     * @param my      鼠标 Y 坐标
     * @param scrollX 水平滚动量
     * @param scrollY 垂直滚动量
     * @return 是否消费了该事件
     */
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (isInRect((int) mx, (int) my, listX, listY, listW, listH)) {
            scrollOffset = Mth.clamp((int) (scrollOffset - scrollY * (ROW_HEIGHT + ROW_SPACING)), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ═══════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════

    /**
     * 判断一个点是否在指定矩形内。
     *
     * @param px 点的 X 坐标
     * @param py 点的 Y 坐标
     * @param x  矩形左上角 X 坐标
     * @param y  矩形左上角 Y 坐标
     * @param w  矩形宽度
     * @param h  矩形高度
     * @return 如果点在矩形内则返回 {@code true}
     */
    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /**
     * 绘制深色木质纹理的背景面板，带有金色边框和顶部高光。
     *
     * @param g GUI 绘制上下文
     * @param x 面板左上角 X 坐标
     * @param y 面板左上角 Y 坐标
     * @param w 面板宽度
     * @param h 面板高度
     */
    private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xD81A1008, 0xD820140A);
        g.renderOutline(x, y, w, h, 0xFF8B6914);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFE8C0);
    }

    /**
     * 绘制自定义样式的垂直滚动条。
     *
     * @param g             GUI 绘制上下文
     * @param x             滚动条左上角 X 坐标
     * @param y             滚动条左上角 Y 坐标
     * @param h             滚动条高度
     * @param scroll        当前滚动偏移
     * @param maxScroll     最大滚动偏移
     * @param totalContentH 内容总高度
     * @param mouseX        鼠标 X 坐标
     * @param mouseY        鼠标 Y 坐标
     * @param dragging      是否正在拖拽
     */
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

    /**
     * 创建用于构建 {@link FilterSelectionScreen} 的建造者实例。
     *
     * @param parent 父级屏幕，不可为 {@code null}
     * @return 新的建造者
     */
    public static Builder builder(Screen parent) {
        return new Builder(parent);
    }

    /**
     * 用于逐步配置 {@link FilterSelectionScreen} 的建造者。
     * <p>
     * 所有参数都有默认值，至少需要调用 {@link #build()} 才能构造实例。
     * 使用示例：
     * 
     * <pre>{@code
     * FilterSelectionScreen screen = FilterSelectionScreen.builder(parent)
     *         .title(Component.literal("选择物品"))
     *         .subtitle(Component.translatable("gui.filter.tip"))
     *         .options(optionMap)
     *         .multiSelect(true)
     *         .callback(selected -> {
     *             // 处理选择结果
     *             return null;
     *         })
     *         .build();
     * }</pre>
     * </p>
     */
    public static class Builder {
        private final Screen parent;
        private Component title = Component.empty();
        private Component subtitle = Component.empty();
        private LinkedHashMap<String, Component> options = new LinkedHashMap<>();
        private boolean multiSelect = false;
        private Consumer<Set<String>> callback = ids -> {
        };

        /**
         * 创建建造者，必须指定父级屏幕。
         *
         * @param parent 父级屏幕，不可为 null
         * @throws NullPointerException 如果 parent 为 null
         */
        public Builder(Screen parent) {
            this.parent = Objects.requireNonNull(parent, "parent screen cannot be null");
        }

        /**
         * 设置标题，支持 {@link Component} 样式。若传入 {@code null} 则使用空文本。
         *
         * @param title 标题组件
         * @return 建造者自身，用于链式调用
         */
        public Builder title(Component title) {
            this.title = title != null ? title : Component.empty();
            return this;
        }

        /**
         * 设置副标题/提示文本，支持自动换行。若传入 {@code null} 则使用空文本。
         *
         * @param subtitle 副标题组件
         * @return 建造者自身，用于链式调用
         */
        public Builder subtitle(Component subtitle) {
            this.subtitle = subtitle != null ? subtitle : Component.empty();
            return this;
        }

        /**
         * 设置选项映射（ID → 显示名称），会保持传入的顺序。
         * 若传入 {@code null} 则使用空映射。
         *
         * @param options 选项映射
         * @return 建造者自身，用于链式调用
         */
        public Builder options(Map<String, Component> options) {
            this.options = options != null ? new LinkedHashMap<>(options) : new LinkedHashMap<>();
            return this;
        }

        /**
         * 添加单个选项。
         *
         * @param id          选项唯一标识
         * @param displayName 选项显示名称
         * @return 建造者自身，用于链式调用
         */
        public Builder addOption(String id, Component displayName) {
            this.options.put(id, displayName);
            return this;
        }

        /**
         * 设置是否允许多选，默认为 {@code false}（单选）。
         *
         * @param multiSelect true 为多选，false 为单选
         * @return 建造者自身，用于链式调用
         */
        public Builder multiSelect(boolean multiSelect) {
            this.multiSelect = multiSelect;
            return this;
        }

        /**
         * 设置确认后的回调，参数为选中 ID 的集合（多选时可能有多个，单选时包含一个元素）。
         * 回调在点击确认按钮后、关闭界面前执行。不允许为 {@code null}。
         *
         * @param callback 回调函数，不可为 null
         * @return 建造者自身，用于链式调用
         * @throws NullPointerException 如果 callback 为 null
         */
        public Builder callback(Consumer<Set<String>> callback) {
            this.callback = Objects.requireNonNull(callback, "callback cannot be null");
            return this;
        }

        /**
         * 构建 {@link FilterSelectionScreen} 实例。
         *
         * @return 配置好的筛选界面实例
         */
        public FilterSelectionScreen build() {
            return new FilterSelectionScreen(title, subtitle, parent, options, multiSelect, callback);
        }
    }

    /**
     * 安全地显示此屏幕，不会误删除父窗口。
     *
     * @param minecraft Minecraft 实例，不可为 null
     * @throws NullPointerException 如果 minecraft 为 null
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