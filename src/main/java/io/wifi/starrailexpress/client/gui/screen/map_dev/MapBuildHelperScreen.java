package io.wifi.starrailexpress.client.gui.screen.map_dev;

import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import io.wifi.starrailexpress.client.gui.screen.map_dev.modules.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.util.*;
import java.util.function.Consumer;

public class MapBuildHelperScreen extends Screen implements ModuleContext {

    private static double offsetX = 0.5;
    private static double offsetY = 1;
    private static double offsetZ = 0.5;

    private final BlockPos position;
    private int activeTab = 0;

    private EditBox dxBox, dyBox, dzBox;
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    private final Map<Integer, TabModule> modules = new LinkedHashMap<>();
    private final List<WidgetPlacement> currentTabPlacements = new ArrayList<>();

    private int panelWidth, panelHeight;
    private static final int MAX_PANEL_WIDTH = 500;
    private static final int MIN_PANEL_WIDTH = 340;
    private static final int MIN_PANEL_HEIGHT = 200;
    private int panelLeftX, panelTopY;

    private int scrollOffset = 0;
    private int contentHeight = 0;
    private boolean isDraggingScroll = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

    private LayoutContext layoutCtx;

    public MapBuildHelperScreen(BlockPos position) {
        this(position, 0);
    }

    public MapBuildHelperScreen(BlockPos position, int initialTab) {
        super(Component.translatable("sre.map_helper.title"));
        this.position = position;
        this.activeTab = Math.max(0, Math.min(7, initialTab));
        registerModules();
    }

    private void registerModules() {
        modules.put(0, new PositionsModule());
        modules.put(1, new AreasModule());
        modules.put(2, new BooleanSettingsModule());
        modules.put(3, new RoomsModule());
        modules.put(4, new EnvironmentModule());
        modules.put(5, new SceneModule());
        modules.put(6, new MapModule());
        modules.put(7, new AllSettingsModule());
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
        dxBox.setValue("0.5");
        dyBox.setValue("1");
        dzBox.setValue("0.5");
    }

    @Override
    public void refreshScreen() {
        init(minecraft, width, height);
    }

    @Override
    public String quoteCommandArgument(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ── Screen init ─────────────────────────────────────────────────
    @Override
    protected void init() {
        panelWidth = Math.min(Math.max((int) (width * 0.7f), MIN_PANEL_WIDTH), MAX_PANEL_WIDTH);
        panelHeight = Math.min(454, height - 40);
        panelHeight = Math.max(panelHeight, MIN_PANEL_HEIGHT);
        panelLeftX = (width - panelWidth) / 2;
        panelTopY = (height - panelHeight) / 2;

        int titleHeight = 40;
        int offsetRowHeight = 42;
        int tabBarHeight = 30;
        int headerGap = 10;
        int bottomStatusHeight = 8;
        int contentStartY = panelTopY + titleHeight + offsetRowHeight + tabBarHeight + headerGap;
        int contentEndY = panelTopY + panelHeight - bottomStatusHeight;
        layoutCtx = new LayoutContext(panelLeftX, panelTopY, panelWidth, panelHeight, contentStartY, contentEndY, 6,
                font);

        clearWidgets();
        fixedWidgets.clear();
        currentTabPlacements.clear();

        buildOffsetRow();
        buildTabBar();

        TabModule module = modules.get(activeTab);
        if (module != null) {
            module.init(layoutCtx, this, currentTabPlacements);
            contentHeight = module.getContentHeight();
        }

        // 所有控件（固定 + 可滚动）都加入屏幕列表，让屏幕自动管理焦点和事件
        fixedWidgets.forEach(this::addRenderableWidget);
        currentTabPlacements.forEach(p -> addRenderableWidget(p.widget));
        scrollOffset = 0;
    }

    // ── Fixed UI sections ───────────────────────────────────────────
    private void buildOffsetRow() {
        final int oy = panelTopY + 68;
        final int fh = 18, labelW = 14, fieldW = 60, smallGap = 4, bigGap = 8, resetW = 48;
        int groupW = labelW + smallGap + fieldW;
        int totalW = groupW * 3 + bigGap * 2 + resetW;
        int startX = panelLeftX + (panelWidth - totalW) / 2;

        dxBox = makeField(startX + labelW + smallGap, oy, fieldW, fh, "0", v -> {
            try {
                setOffsetX(Double.parseDouble(v));
            } catch (Exception ignored) {
            }
        });
        dxBox.setValue(fmtDouble(offsetX));
        fixedWidgets.add(dxBox);

        int yStart = startX + groupW + bigGap;
        dyBox = makeField(yStart + labelW + smallGap, oy, fieldW, fh, "1", v -> {
            try {
                setOffsetY(Double.parseDouble(v));
            } catch (Exception ignored) {
            }
        });
        dyBox.setValue(fmtDouble(offsetY));
        fixedWidgets.add(dyBox);

        int zStart = yStart + groupW + bigGap;
        dzBox = makeField(zStart + labelW + smallGap, oy, fieldW, fh, "0", v -> {
            try {
                setOffsetZ(Double.parseDouble(v));
            } catch (Exception ignored) {
            }
        });
        dzBox.setValue(fmtDouble(offsetZ));
        fixedWidgets.add(dzBox);

        int resetX = zStart + groupW + bigGap;
        fixedWidgets.add(ModernButton.builder(Component.translatable("sre.map_helper.reset"), b -> resetOffsets())
                .bounds(resetX, oy, resetW, fh).accentBar(AccentSide.BOTTOM).build());
    }

    private void buildTabBar() {
        int tabY = panelTopY + 90, tabH = 22, tabGap = 5;
        int tabW = Math.min(42, (panelWidth - 12 - 7 * tabGap) / 8);
        int totalTabW = tabW * 8 + tabGap * 7;
        int startX = panelLeftX + (panelWidth - totalTabW) / 2;

        for (int i = 0; i < 8; i++) {
            final int idx = i;
            TabModule mod = modules.get(idx);
            Component title = mod != null ? mod.getTabTitle() : Component.literal("?");
            var builder = ModernButton.builder(title, b -> {
                activeTab = idx;
                refreshScreen();
            })
                    .bounds(startX + i * (tabW + tabGap), tabY, tabW, tabH);
            if (activeTab == idx)
                builder.accentBar(AccentSide.BOTTOM);
            else
                builder.accentBar();
            fixedWidgets.add(builder.build());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────
    private EditBox makeField(int x, int y, int w, int h, String defaultVal, Consumer<String> responder) {
        EditBox box = new EditBox(font, x, y, w, h, Component.empty());
        box.setValue(defaultVal);
        box.setMaxLength(20);
        box.setResponder(responder);
        return box;
    }

    private static String fmtDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e9)
            return String.valueOf((long) v);
        String s = String.format("%.4f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // ── Scrolling ───────────────────────────────────────────────────
    private int visibleContentHeight() {
        return layoutCtx.contentEndY - layoutCtx.contentStartY;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        if (contentHeight > visibleContentHeight()) {
            scrollOffset = Math.max(0,
                    Math.min(scrollOffset - (int) vert * 20, contentHeight - visibleContentHeight()));
            return true;
        }
        return super.mouseScrolled(mx, my, horiz, vert);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 滚动条拖拽处理（优先）
        if (contentHeight > visibleContentHeight()) {
            int barX = panelLeftX + panelWidth - 8, barY = layoutCtx.contentStartY, barH = visibleContentHeight();
            if (mx >= barX && mx <= barX + 4 && my >= barY && my <= barY + barH) {
                isDraggingScroll = true;
                dragStartY = (int) my;
                dragStartScroll = scrollOffset;
                return true;
            }
        }
        // 剩余事件交给屏幕自动分发给所有控件
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingScroll && contentHeight > visibleContentHeight()) {
            int delta = (int) (my - dragStartY);
            scrollOffset = Math.max(0, Math.min(
                    dragStartScroll + delta * (contentHeight - visibleContentHeight()) / visibleContentHeight(),
                    contentHeight - visibleContentHeight()));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    // ── Rendering ───────────────────────────────────────────────────
    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // 背景与顶部装饰线
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + panelWidth + 6, panelTopY + panelHeight + 3, 0xCC080C18);
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + panelWidth + 6, panelTopY - 2, 0xFF5577CC);

        // 1. 绘制固定控件（不受裁剪，且不会被 super.render 重复绘制）
        for (AbstractWidget w : fixedWidgets) {
            w.render(g, mouseX, mouseY, partial);
        }

        // 2. 更新可滚动控件 Y 坐标并裁剪绘制
        for (WidgetPlacement p : currentTabPlacements) {
            p.widget.setY(layoutCtx.contentStartY + p.relativeY - scrollOffset);
        }

        g.enableScissor(layoutCtx.panelLeftX, layoutCtx.contentStartY,
                layoutCtx.panelLeftX + layoutCtx.panelWidth, layoutCtx.contentEndY);
        for (WidgetPlacement p : currentTabPlacements) {
            p.widget.render(g, mouseX, mouseY, partial);
        }
        g.disableScissor();

        // 3. 固定文本 overlay 和模块额外内容
        drawFixedOverlays(g);
        TabModule mod = modules.get(activeTab);
        if (mod != null)
            mod.renderOverlay(g, mouseX, mouseY, partial);
        drawScrollbar(g);

        // 4. 手动绘制 tooltip（因为未调用 super.render）
    }

    private void drawFixedOverlays(GuiGraphics g) {
        int cx = panelLeftX + panelWidth / 2;
        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.title").withStyle(s -> s.withColor(0x55BBFF).withBold(true)), cx,
                panelTopY + 10, 0xFFFFFF);
        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.source_pos", position.getX(), position.getY(), position.getZ())
                        .withStyle(s -> s.withColor(0x778899)),
                cx, panelTopY + 22, 0xFFFFFF);
        boolean hasOffset = offsetX != 0 || offsetY != 0 || offsetZ != 0;
        g.drawCenteredString(font, Component.translatable("sre.map_helper.applied_pos", ax(), ay(), az())
                .withStyle(s -> s.withColor(hasOffset ? 0x55DD88 : 0x445566)), cx, panelTopY + 32, 0xFFFFFF);

        // 偏移量标签
        final int oy = panelTopY + 68;
        int labelW = 14, fieldW = 60, smallGap = 4, bigGap = 8, resetW = 48;
        int groupW = labelW + smallGap + fieldW;
        int totalW = groupW * 3 + bigGap * 2 + resetW;
        int startX = panelLeftX + (panelWidth - totalW) / 2;
        g.drawString(font, Component.translatable("sre.map_helper.dx"), startX, oy + 4, 0xAABBCC, false);
        g.drawString(font, Component.translatable("sre.map_helper.dy"), startX + groupW + bigGap, oy + 4, 0xAABBCC,
                false);
        g.drawString(font, Component.translatable("sre.map_helper.dz"), startX + (groupW + bigGap) * 2, oy + 4,
                0xAABBCC, false);

        // 分割线
        g.fill(panelLeftX, panelTopY + 86, panelLeftX + panelWidth, panelTopY + 87, 0x33AABBCC);
        g.fill(panelLeftX, panelTopY + 110, panelLeftX + panelWidth, panelTopY + 111, 0x33AABBCC);

        // 当前标签标题
        String[] tabTitlesKeys = { "spawn_offset", "aabb_areas", "boolean_settings", "rooms_config", "environment",
                "scene", "map", "all" };
        g.drawString(font,
                Component.translatable("sre.map_helper.tab_title." + tabTitlesKeys[activeTab])
                        .withStyle(Style.EMPTY.withColor(0x5577CC).withBold(true)),
                panelLeftX + 6, panelTopY + 94, 0xFFFFFF, false);
    }

    private void drawScrollbar(GuiGraphics g) {
        int visibleH = visibleContentHeight();
        if (contentHeight > visibleH) {
            int barX = panelLeftX + panelWidth - 8, barY = layoutCtx.contentStartY, barH = visibleH;
            int thumbH = Math.max(20, (int) ((float) visibleH / contentHeight * barH));
            int thumbY = barY + (int) ((float) scrollOffset / (contentHeight - visibleH) * (barH - thumbH));
            g.fill(barX, barY, barX + 4, barY + barH, 0x22FFFFFF);
            g.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xCC5577CC);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        SceneAssetClient.closeEditor();
        super.onClose();
    }
}