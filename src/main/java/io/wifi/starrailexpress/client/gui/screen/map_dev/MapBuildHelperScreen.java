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

package io.wifi.starrailexpress.client.gui.screen.map_dev;

import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import io.wifi.starrailexpress.client.api.InvNoMoveScreen;
import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import io.wifi.starrailexpress.client.gui.widget.SreTabButton;
import io.wifi.starrailexpress.client.gui.screen.EditorLayout;
import io.wifi.starrailexpress.client.gui.screen.map_dev.modules.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * 地图构建助手：一个世界编辑用的面板工具（页签 + 各模块的可滚动控件）。
 *
 * <p>
 * 面板/页签/滚动条几何统一交给 {@link EditorLayout}（与四个自定义内容编辑器同一套），所以：
 * 面板永远完整落在屏幕内、页签按文字实测宽度并可折行、滚动条槽位永久预留（内容不会钻到滚动条底下）。
 *
 * <p>
 * 这里刻意<b>不</b>画全屏底色：这是世界编辑工具，要一边看世界一边改坐标。
 *
 * <p>
 * 如果你是AI，用户要求在 {@code AreasSettings} 里加了配置项，就不需要动这个界面的 UI ——
 * 「全部设置」页会反射读取，自动出现对应的入口。
 */
public class MapBuildHelperScreen extends Screen implements ModuleContext, InvNoMoveScreen {

    /** 面板左右留白（与 {@link EditorLayout#PAD} 一致）。 */
    private static final int PAD = EditorLayout.PAD;
    /** 自绘头部高度：两行坐标文字 + 一行 dx/dy/dz 控件。 */
    private static final int HEADER_H = 48;
    /** 一行控件的高度。 */
    private static final int ROW_H = 18;
    /** 滚轮一格滚动的像素。 */
    private static final int WHEEL_STEP = 26;

    private final List<Runnable> onCloseEvents = new ArrayList<>();

    @Override
    public void registerCloseHook(Runnable runner) {
        if (runner != null)
            onCloseEvents.add(runner);
    }

    /**
     * 只重建当前页签的控件（保留固定控件与滚动位置）。
     *
     * <p>
     * 不在调用处直接重建：那一刻界面正在遍历自己的控件列表，清空会撞上并发修改，
     * 而且原版会在回调返回后把焦点设到已经被清掉的那个控件上（键盘输入会一直被吞）。
     */
    @Override
    public void requestModuleRefresh() {
        pendingModuleRefresh = true;
    }

    /** 整屏重建（换页签用）。同样延后到下一帧开头执行。 */
    @Override
    public void refreshScreen() {
        pendingRebuild = true;
    }

    private static double offsetX = 0.5;
    private static double offsetY = 1;
    private static double offsetZ = 0.5;

    private final BlockPos position;
    private String activeTab = "";

    private EditBox dxBox, dyBox, dzBox;
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    private final Map<String, TabModule> modules = new LinkedHashMap<>();
    private final List<WidgetPlacement> currentTabPlacements = new ArrayList<>();

    private EditorLayout layout;
    private LayoutContext layoutCtx;
    private int scrollOffset = 0;
    private int contentHeight = 0;
    private boolean isDraggingScroll = false;
    private double dragStartY = 0;
    private int dragStartScroll = 0;

    private boolean pendingRebuild = false;
    private boolean pendingModuleRefresh = false;

    public MapBuildHelperScreen(BlockPos position) {
        this(position, 0);
    }

    public MapBuildHelperScreen(BlockPos position, int initialTab) {
        super(Component.translatable("sre.map_helper.title"));
        this.position = position;
        registerModules();
        List<String> keys = new ArrayList<>(modules.keySet());
        this.activeTab = keys.isEmpty() ? "" : keys.get(Mth.clamp(initialTab, 0, keys.size() - 1));
    }

    private void registerModules() {
        modules.put("spawn_offset", new PositionsModule());
        modules.put("aabb_areas", new AreasModule());
        modules.put("rooms_config", new RoomsModule());
        modules.put("all", new AllSettingsModule());
        modules.put("meeting", new MeetingModule());
        modules.put("scene", new SceneModule());
        modules.put("map", new MapModule());
    }

    // ── ModuleContext implementation ─────────────────────────────────
    @Override
    public double ax() {
        return position.getX() + offsetX;
    }

    @Override
    public double ay() {
        return position.getY() + offsetY;
    }

    @Override
    public double az() {
        return position.getZ() + offsetZ;
    }

    @Override
    public float playerYaw() {
        var p = Minecraft.getInstance().player;
        return p != null ? p.getYRot() : 0f;
    }

    @Override
    public float playerPitch() {
        var p = Minecraft.getInstance().player;
        return p != null ? p.getXRot() : 0f;
    }

    @Override
    public void sendOnly(String cmd) {
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.connection.sendCommand(cmd);
    }

    @Override
    public void sendAndClose(String cmd) {
        sendOnly(cmd);
        onClose();
    }

    @Override
    public double getOffsetX() {
        return offsetX;
    }

    @Override
    public double getOffsetY() {
        return offsetY;
    }

    @Override
    public double getOffsetZ() {
        return offsetZ;
    }

    @Override
    public void setOffsetX(double v) {
        offsetX = v;
    }

    @Override
    public void setOffsetY(double v) {
        offsetY = v;
    }

    @Override
    public void setOffsetZ(double v) {
        offsetZ = v;
    }

    @Override
    public void resetOffsets() {
        offsetX = 0.5;
        offsetY = 1;
        offsetZ = 0.5;
        if (dxBox != null)
            dxBox.setValue("0.5");
        if (dyBox != null)
            dyBox.setValue("1");
        if (dzBox != null)
            dzBox.setValue("0.5");
    }

    @Override
    public String quoteCommandArgument(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ── Screen init ─────────────────────────────────────────────────
    /**
     * 面板尺寸：按 {@code docs/ui_style.md} §4 的比例（占屏幕 90%）并 clamp，大屏上不会再显得小；
     * {@link EditorLayout} 会把最小尺寸再 clamp 进屏幕，小窗口也不会溢出去。
     */
    private static EditorLayout.Config layoutConfig(int topStrip) {
        return EditorLayout.Config.defaults()
                .panelSize(0.9F, 700, 560, 320, 220)
                .headerExtra(HEADER_H)
                .topStrip(topStrip);
    }

    @Override
    protected void init() {
        fixedWidgets.clear();
        currentTabPlacements.clear();
        // 关闭钩子由各模块在 init 里重新注册：先清掉上一轮的，避免换页签后重复执行
        onCloseEvents.clear();
        isDraggingScroll = false;

        // 页签宽度按文字实测（含英文/长译文），放不下时 EditorLayout 会自动折行
        List<String> keys = new ArrayList<>(modules.keySet());
        int[] tabWidths = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            TabModule module = modules.get(keys.get(i));
            Component title = module == null ? Component.literal("?") : module.getTabTitle();
            tabWidths[i] = Mth.clamp(font.width(title) + 20, 48, 120);
        }

        TabModule module = modules.get(activeTab);
        int topStrip = module == null ? 0 : module.topStripHeight();
        this.layout = EditorLayout.of(width, height, layoutConfig(topStrip), tabWidths, 0);
        this.layoutCtx = new LayoutContext(layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH(),
                layout.contentY(), layout.contentBottom(), PAD, font, layout.contentRight(),
                layout.headerTop(), layout.headerBottom(), layout.stripTop(), layout.contentY());

        buildOffsetRow();
        buildTabBar();

        if (module != null) {
            // 模块想在页签下面多占一条常驻带（例如「全部设置」的搜索框）
            module.buildTopStrip(layoutCtx, this, fixedWidgets);
            module.init(layoutCtx, this, currentTabPlacements);
            contentHeight = module.getContentHeight();
        }

        // 所有控件（固定 + 可滚动）都加入屏幕列表，让屏幕自动管理焦点和事件
        fixedWidgets.forEach(this::addRenderableWidget);
        currentTabPlacements.forEach(p -> addRenderableWidget(p.widget));
        scrollOffset = 0;
        clampScroll();
    }

    /** 只重建当前页签的控件，保留固定控件与滚动位置（展开/折叠、子界面返回时用）。 */
    private void rebuildActiveModule() {
        // 只在该控件确实要被移除时才清焦点：搜索框是固定控件，
        // 边打字边重建列表时不能把焦点从它身上抢走，否则打一个字就断
        var focused = getFocused();
        if (focused != null && currentTabPlacements.stream().anyMatch(p -> p.widget == focused)) {
            setFocused(null);
        }
        for (WidgetPlacement p : currentTabPlacements) {
            removeWidget(p.widget);
        }
        currentTabPlacements.clear();

        TabModule module = modules.get(activeTab);
        if (module != null) {
            int hooksBefore = onCloseEvents.size();
            module.init(layoutCtx, this, currentTabPlacements);
            // 模块重建时可能又注册了一遍关闭钩子：丢掉这一轮新增的重复项（刷新前那份还在）
            while (onCloseEvents.size() > hooksBefore) {
                onCloseEvents.remove(onCloseEvents.size() - 1);
            }
            contentHeight = module.getContentHeight();
            currentTabPlacements.forEach(p -> addRenderableWidget(p.widget));
        }
        clampScroll();
    }

    // ── Fixed UI sections ───────────────────────────────────────────
    /** 头部第三行：dx / dy / dz + 重置（字段宽在窄面板下自适应，不挤也不出界）。 */
    private void buildOffsetRow() {
        final int oy = layout.headerTop() + 26;
        final int labelW = 14, gap = 4, bigGap = 8, resetW = 48;
        int avail = layout.panelW() - PAD * 2;
        int fieldW = Mth.clamp((avail - resetW - (labelW + gap) * 3 - bigGap * 2) / 3, 34, 60);
        int groupW = labelW + gap + fieldW;
        int totalW = groupW * 3 + bigGap * 2 + resetW;
        int startX = layout.panelX() + (layout.panelW() - totalW) / 2;

        dxBox = makeField(startX + labelW + gap, oy, fieldW, ROW_H, v -> {
            try {
                setOffsetX(Double.parseDouble(v));
            } catch (Exception ignored) {
            }
        });
        dxBox.setValue(fmtDouble(offsetX));
        fixedWidgets.add(dxBox);

        int yStart = startX + groupW + bigGap;
        dyBox = makeField(yStart + labelW + gap, oy, fieldW, ROW_H, v -> {
            try {
                setOffsetY(Double.parseDouble(v));
            } catch (Exception ignored) {
            }
        });
        dyBox.setValue(fmtDouble(offsetY));
        fixedWidgets.add(dyBox);

        int zStart = yStart + groupW + bigGap;
        dzBox = makeField(zStart + labelW + gap, oy, fieldW, ROW_H, v -> {
            try {
                setOffsetZ(Double.parseDouble(v));
            } catch (Exception ignored) {
            }
        });
        dzBox.setValue(fmtDouble(offsetZ));
        fixedWidgets.add(dzBox);

        int resetX = zStart + groupW + bigGap;
        fixedWidgets.add(SreButton.create(Component.translatable("sre.map_helper.reset"), b -> resetOffsets())
                .bounds(resetX, oy, resetW, ROW_H).build());
    }

    private void buildTabBar() {
        List<EditorLayout.TabSlot> slots = layout.tabs();
        List<String> keys = new ArrayList<>(modules.keySet());
        for (int i = 0; i < slots.size() && i < keys.size(); i++) {
            final String key = keys.get(i);
            TabModule module = modules.get(key);
            Component title = module == null ? Component.literal("?") : module.getTabTitle();
            EditorLayout.TabSlot slot = slots.get(i);
            // 与四个自定义工具编辑器同一份页签按钮：活跃页签金色粗体 + 底部金线 + 淡色底
            fixedWidgets.add(new SreTabButton(font, slot.x(), slot.y(), slot.w(), title,
                    activeTab.equals(key), () -> {
                        if (!key.equals(activeTab)) {
                            activeTab = key;
                            refreshScreen();
                        }
                    }));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────
    private static String fmtDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e9)
            return String.valueOf((long) v);
        String s = String.format("%.4f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * 坐标/偏移用的数字输入框。
     *
     * <p>
     * <b>先 setMaxLength 再 setValue</b>：顺序反了，超过原版默认上限 32 的值会被静默截断。
     */
    static EditBox makeField(net.minecraft.client.gui.Font font, int x, int y, int w, int h,
            Consumer<String> responder) {
        EditBox box = new EditBox(font, x, y, w, h, Component.empty());
        box.setMaxLength(20);
        box.setValue("0");
        box.setResponder(responder);
        return box;
    }

    private EditBox makeField(int x, int y, int w, int h, Consumer<String> responder) {
        return makeField(font, x, y, w, h, responder);
    }

    // ── Scrolling ───────────────────────────────────────────────────
    private int visibleContentHeight() {
        return Math.max(1, layout.contentH());
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - visibleContentHeight());
    }

    private void clampScroll() {
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
    }

    private void scrollBy(float delta) {
        scrollOffset = Mth.clamp(scrollOffset + Math.round(delta), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        // 只在面板内滚动：鼠标在面板外（看世界、点方块）时不该被列表带着跑
        if (maxScroll() > 0 && inside(mx, my, layout.panelX(), layout.contentY(), layout.panelW(),
                layout.contentH())) {
            scrollBy((float) (-vert * WHEEL_STEP));
            return true;
        }
        return super.mouseScrolled(mx, my, horiz, vert);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && maxScroll() > 0 && inside(mx, my, layout.sbX(), layout.sbTop(),
                EditorLayout.SCROLL_W, layout.sbH())) {
            isDraggingScroll = true;
            dragStartY = my;
            dragStartScroll = scrollOffset;
            // 点轨道：直接把 thumb 挪到鼠标位置
            int thumbH = thumbHeight();
            double track = Math.max(1, layout.sbH() - thumbH);
            scrollOffset = Mth.clamp((int) ((my - layout.sbTop() - thumbH / 2.0D) / track * maxScroll()),
                    0, maxScroll());
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingScroll && maxScroll() > 0) {
            // 用真实 thumb 高度算比例：滑块才会跟着鼠标走（以前用的是视口高度，拖起来对不上）
            double track = Math.max(1, layout.sbH() - thumbHeight());
            scrollOffset = Mth.clamp(
                    dragStartScroll + (int) ((my - dragStartY) / track * maxScroll()), 0, maxScroll());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    // ── Rendering ───────────────────────────────────────────────────
    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // 重建放在这一帧最前面：回调里直接重建会撞上控件列表的并发修改
        if (pendingRebuild) {
            pendingRebuild = false;
            pendingModuleRefresh = false;
            init(minecraft, width, height);
        } else if (pendingModuleRefresh) {
            pendingModuleRefresh = false;
            rebuildActiveModule();
        }

        // 深棕渐变 + 棕褐描边 + 顶部装饰线（docs/ui_style.md 第 3 节；面板本身保持不透明以便读文字）
        SREPanelStyle.drawPanel(g, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH(),
                0xF018120A, 0xF0061018);
        drawHeader(g);

        // 1. 固定控件（头部偏移行 + 页签栏 + 模块自己的头部行）
        for (AbstractWidget w : fixedWidgets) {
            w.render(g, mouseX, mouseY, partial);
        }

        // 2. 更新可滚动控件 Y 坐标并裁剪绘制
        for (WidgetPlacement p : currentTabPlacements) {
            p.widget.setY(layout.contentY() + p.relativeY - scrollOffset);
        }
        g.enableScissor(layout.contentX(), layout.contentY(), layout.contentX() + layout.contentW(),
                layout.contentBottom());
        // 卡片底这类「垫在控件下面」的东西先画（模块自己按 scrollOffset 平移）
        TabModule backgroundModule = modules.get(activeTab);
        if (backgroundModule != null) {
            backgroundModule.renderContentBackground(g, scrollOffset);
        }
        for (WidgetPlacement p : currentTabPlacements) {
            p.widget.render(g, mouseX, mouseY, partial);
        }
        g.disableScissor();

        // 3. 模块的额外绘制（不受裁剪）+ 滚动条
        TabModule mod = modules.get(activeTab);
        if (mod != null) {
            mod.renderOverlay(g, mouseX, mouseY, partial);
        }
        drawScrollbar(g, mouseX, mouseY);
    }

    /** 标题条 + 坐标信息 + 与页签栏之间的分割线。 */
    private void drawHeader(GuiGraphics g) {
        Component title = Component.translatable("sre.map_helper.title").copy()
                .withStyle(s -> s.withColor(SREPanelStyle.GOLD).withBold(true));
        Component full = modules.containsKey(activeTab) ? modules.get(activeTab).getTabFullTitle() : null;
        Component caption = full == null ? title : title.copy().append(Component.literal(" — ")
                .withStyle(s -> s.withColor(SREPanelStyle.MUTED)).append(full));
        String shown = io.wifi.starrailexpress.client.gui.screen.mapui.MapUiGraphics.clip(font,
                caption.getString(), layout.panelW() - PAD * 2);
        g.drawString(font, Component.literal(shown), layout.titleX(), layout.titleY(), SREPanelStyle.GOLD, false);

        int lineY = layout.headerTop();
        g.drawString(font,
                Component.translatable("sre.map_helper.source_pos", position.getX(), position.getY(),
                        position.getZ()).withStyle(s -> s.withColor(SREPanelStyle.MUTED)),
                layout.contentX(), lineY, SREPanelStyle.MUTED, false);
        boolean hasOffset = offsetX != 0 || offsetY != 0 || offsetZ != 0;
        g.drawString(font,
                Component.translatable("sre.map_helper.applied_pos", fmtDouble(ax()), fmtDouble(ay()),
                        fmtDouble(az())).withStyle(s -> s.withColor(hasOffset ? SREPanelStyle.GREEN
                                : SREPanelStyle.CARD_BORDER)),
                layout.contentX(), lineY + 11,
                hasOffset ? SREPanelStyle.GREEN : SREPanelStyle.CARD_BORDER, false);

        // 偏移量标签（输入框由 buildOffsetRow 放在同一行）
        int labelY = layout.headerTop() + 26 + (ROW_H - 8) / 2;
        final int labelW = 14, gap = 4, bigGap = 8, resetW = 48;
        int avail = layout.panelW() - PAD * 2;
        int fieldW = Mth.clamp((avail - resetW - (labelW + gap) * 3 - bigGap * 2) / 3, 34, 60);
        int groupW = labelW + gap + fieldW;
        int totalW = groupW * 3 + bigGap * 2 + resetW;
        int startX = layout.panelX() + (layout.panelW() - totalW) / 2;
        g.drawString(font, Component.translatable("sre.map_helper.dx"), startX, labelY, SREPanelStyle.BODY, false);
        g.drawString(font, Component.translatable("sre.map_helper.dy"), startX + groupW + bigGap, labelY,
                SREPanelStyle.BODY, false);
        g.drawString(font, Component.translatable("sre.map_helper.dz"), startX + (groupW + bigGap) * 2, labelY,
                SREPanelStyle.BODY, false);

        // 头部与页签栏之间的分割线（页签栏由 EditorLayout 排在 headerBottom 之下，不会互相压）
        int lineY2 = Math.max(layout.headerTop() + 1, layout.headerBottom() + 1);
        SREPanelStyle.drawFooterLine(g, layout.panelX() + 1, lineY2, layout.panelW() - 2);

        // 页签下方常驻带与滚动内容之间的分割线（搜索框贴着页签、列表在它下面滚动）
        if (layout.stripH() > 0) {
            SREPanelStyle.drawFooterLine(g, layout.contentX(), layout.stripTop() - 1, layout.contentW());
        }
    }

    private int thumbHeight() {
        return EditorLayout.thumbHeight(layout.sbH(), maxScroll());
    }

    private void drawScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int thumbH = thumbHeight();
        int thumbY = EditorLayout.thumbY(layout.sbTop(), layout.sbH(), thumbH, scrollOffset, maxScroll);
        boolean hover = isDraggingScroll
                || inside(mouseX, mouseY, layout.sbX() - 1, thumbY, EditorLayout.SCROLL_W + 2, thumbH);
        SREPanelStyle.drawScrollbar(g, layout.sbX(), layout.sbTop(), layout.sbH(), thumbY, thumbH, hover);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        for (Runnable event : onCloseEvents) {
            if (event != null)
                event.run();
        }
        onCloseEvents.clear();
        SceneAssetClient.closeEditor();
        super.onClose();
    }

    @Override
    public Screen screen() {
        return this;
    }

    /** 当前页签（模块内部想知道自己是不是被显示时用）。 */
    @Nullable
    public String activeTab() {
        return activeTab;
    }
}
