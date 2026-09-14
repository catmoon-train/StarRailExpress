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

package io.wifi.starrailexpress.custommodifier;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 自定义修饰符管理界面（列表 / 新建 / 删除），与 {@code CustomRoleManageScreen} 对应。
 */
@Environment(EnvType.CLIENT)
public class CustomModifierManageScreen extends Screen {

    private static final int PREF_PANEL_WIDTH = 460;
    private static final int PREF_PANEL_HEIGHT = 380;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int PREF_VISIBLE_ROWS = 12;

    /** 实际面板尺寸（小窗口下会被裁进窗口）。 */
    private int panelW;
    private int panelH;
    /** 实际可见行数（由面板高度算出）。 */
    private int visibleRows;
    /** 列表行布局（由面板宽度算出）。 */
    private int nameW;
    private int delX;
    private int infoX;

    private int panelLeftX, panelTopY;
    private final Supplier<Screen> backScreenSupplier;
    private List<CustomModifierData> modifiers = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;
    /** 需要重建界面时置为 true，在 render 中统一重建（避免在控件回调里改控件列表）。 */
    private boolean pendingRebuild = false;

    private void requestRebuild() {
        this.pendingRebuild = true;
    }

    public CustomModifierManageScreen(Supplier<Screen> backSupplier) {
        super(Component.translatableWithFallback("sre.custom_modifier.manage.title", "管理自定义修饰符"));
        this.backScreenSupplier = backSupplier;
    }

    public CustomModifierManageScreen(Screen backScreen) {
        this(() -> backScreen);
    }

    @Override
    protected void init() {
        // 小窗口下面板跟着窗口收缩（列表靠滚动查看），保证面板不超出显示区域
        panelW = Math.min(PREF_PANEL_WIDTH, Math.max(240, width - 8));
        panelH = Math.min(PREF_PANEL_HEIGHT, Math.max(140, height - 8));
        panelLeftX = (width - panelW) / 2;
        panelTopY = (height - panelH) / 2;
        visibleRows = Math.max(2, Math.min(PREF_VISIBLE_ROWS, (panelH - 68) / ROW_HEIGHT));
        nameW = Math.min(220, Math.max(80, panelW - 60));
        delX = panelLeftX + 10 + nameW + 8;
        infoX = delX + 38;

        CustomModifierConfig config = CustomModifierConfig.loadPreferWorldPath(minecraft.getSingleplayerServer());
        modifiers = config.modifiers;

        maxScroll = Math.max(0, (modifiers.size() - visibleRows) * ROW_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(visibleRows + 1, modifiers.size() - startIdx);

        for (int i = 0; i < visibleCount; i++) {
            final int index = startIdx + i;
            if (index >= modifiers.size())
                break;
            CustomModifierData modifier = modifiers.get(index);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);

            String displayText = modifier.englishId
                    + (modifier.displayName == null || modifier.displayName.isEmpty() ? ""
                            : " (" + modifier.displayName + ")");
            Button nameBtn = Button
                    .builder(Component.literal(displayText), b -> minecraft.setScreen(new CustomModifierScreen(modifier)))
                    .bounds(panelLeftX + 10, y, nameW, 20).build();
            addRenderableWidget(nameBtn);

            Button delBtn = Button.builder(Component.literal("X"), b -> {
                config.modifiers.remove(index);
                config.savePreferWorldPath(minecraft.getSingleplayerServer());
                var server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            CustomModifierLoader.reload(server);
                        } catch (Exception ignored) {
                        }
                    });
                }
                requestRebuild();
            }).bounds(panelLeftX + delX, y, 30, 20).build();
            addRenderableWidget(delBtn);
        }

        Button backBtn = Button
                .builder(Component.translatableWithFallback("sre.custom_modifier.back", "返回"),
                        b -> minecraft.setScreen(backScreenSupplier.get()))
                .bounds(panelLeftX + 10, panelTopY + panelH - 28, 90, 20).build();
        addRenderableWidget(backBtn);

        Button newBtn = Button
                .builder(Component.translatableWithFallback("sre.custom_modifier.new", "新建修饰符"),
                        b -> minecraft.setScreen(new CustomModifierScreen()))
                .bounds(panelLeftX + 110, panelTopY + panelH - 28, 110, 20).build();
        addRenderableWidget(newBtn);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        SREPanelStyle.drawPanel(g, panelLeftX - 6, panelTopY - 3, panelW + 12, panelH + 6);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuildWidgets();
            return;
        }
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        int centerX = panelLeftX + panelW / 2;
        g.drawCenteredString(font, Component
                .translatableWithFallback("sre.custom_modifier.manage.title", "管理自定义修饰符")
                .withStyle(s -> s.withColor(0xFFD4AF37).withBold(true)), centerX, panelTopY + 10, 0xFFFFFF);

        int listTop = panelTopY + 32;
        int listBottom = panelTopY + panelH - 34;
        g.enableScissor(panelLeftX + 8, listTop, panelLeftX + panelW - 16, listBottom);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(visibleRows + 1, modifiers.size() - startIdx);
        for (int i = 0; i < visibleCount; i++) {
            int index = startIdx + i;
            if (index >= modifiers.size())
                break;
            CustomModifierData modifier = modifiers.get(index);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);
            int color = modifier.getColor();
            String info = (modifier.hidden ? "[隐藏] " : "")
                    + "max:" + modifier.defaultMax + " 概率:" + modifier.defaultEnableChance
                    + (modifier.isGlobalTrigger() ? " 全局触发" : " 条件:" + modifier.conditions.size());
            g.drawString(font, Component.literal(info).withStyle(Style.EMPTY.withColor(color)),
                    panelLeftX + infoX, y + 6, 0xFFFFFF, false);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            renderVScrollbar(g, mouseX, mouseY);
        }
        if (modifiers.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatableWithFallback("sre.custom_modifier.manage.empty", "§7暂无自定义修饰符"),
                    centerX, panelTopY + panelH / 2, 0xFFFFFF);
        }
    }

    private void renderVScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + panelW - 12;
        int sbY = panelTopY + 34;
        int sbH = panelH - 68;
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hover = mouseX >= sbX && mouseX < sbX + SCROLL_W && mouseY >= thumbY
                && mouseY < thumbY + thumbH;
        SREPanelStyle.drawScrollbar(g, sbX, sbY, sbH, thumbY, thumbH, hover);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelLeftX && mouseX < panelLeftX + panelW
                && mouseY >= panelTopY + 32 && mouseY < panelTopY + panelH - 34 && maxScroll > 0) {
            scrollOffset = Mth.clamp((int) (scrollOffset - scrollY * ROW_HEIGHT), 0, maxScroll);
            requestRebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
