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

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 自定义修饰符管理界面（列表 / 新建 / 删除），与 {@code CustomRoleManageScreen} 对应。
 */
@Environment(EnvType.CLIENT)
public class CustomModifierManageScreen extends Screen {

    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 380;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 12;

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
        panelLeftX = (width - PANEL_WIDTH) / 2;
        panelTopY = (height - PANEL_HEIGHT) / 2;

        CustomModifierConfig config = CustomModifierConfig.loadPreferWorldPath(minecraft.getSingleplayerServer());
        modifiers = config.modifiers;

        maxScroll = Math.max(0, (modifiers.size() - VISIBLE_ROWS) * ROW_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(VISIBLE_ROWS + 1, modifiers.size() - startIdx);

        for (int i = 0; i < visibleCount; i++) {
            final int index = startIdx + i;
            if (index >= modifiers.size())
                break;
            CustomModifierData modifier = modifiers.get(index);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);

            String displayText = modifier.englishId
                    + (modifier.displayName == null || modifier.displayName.isEmpty() ? ""
                            : " (" + modifier.displayName + ")");
            ModernButton nameBtn = ModernButton
                    .builder(Component.literal(displayText), b -> minecraft.setScreen(new CustomModifierScreen(modifier)))
                    .bounds(panelLeftX + 10, y, 220, 20).accentBar(AccentSide.LEFT).build();
            addRenderableWidget(nameBtn);

            ModernButton delBtn = ModernButton.builder(Component.literal("X"), b -> {
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
            }).bounds(panelLeftX + 238, y, 30, 20).accentBar(AccentSide.RIGHT).build();
            addRenderableWidget(delBtn);
        }

        ModernButton backBtn = ModernButton
                .builder(Component.translatableWithFallback("sre.custom_modifier.back", "返回"),
                        b -> minecraft.setScreen(backScreenSupplier.get()))
                .bounds(panelLeftX + 10, panelTopY + PANEL_HEIGHT - 28, 90, 20).accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(backBtn);

        ModernButton newBtn = ModernButton
                .builder(Component.translatableWithFallback("sre.custom_modifier.new", "新建修饰符"),
                        b -> minecraft.setScreen(new CustomModifierScreen()))
                .bounds(panelLeftX + 110, panelTopY + PANEL_HEIGHT - 28, 110, 20).accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(newBtn);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY + PANEL_HEIGHT + 3, 0xCC080C18);
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY - 2, 0xFF5577CC);
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

        int centerX = panelLeftX + PANEL_WIDTH / 2;
        g.drawCenteredString(font, Component
                .translatableWithFallback("sre.custom_modifier.manage.title", "管理自定义修饰符")
                .withStyle(s -> s.withColor(0x55BBFF).withBold(true)), centerX, panelTopY + 10, 0xFFFFFF);

        int listTop = panelTopY + 32;
        int listBottom = panelTopY + PANEL_HEIGHT - 34;
        g.enableScissor(panelLeftX + 8, listTop, panelLeftX + PANEL_WIDTH - 16, listBottom);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(VISIBLE_ROWS + 1, modifiers.size() - startIdx);
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
                    panelLeftX + 276, y + 6, 0xFFFFFF, false);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            renderVScrollbar(g, mouseX, mouseY);
        }
        if (modifiers.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatableWithFallback("sre.custom_modifier.manage.empty", "§7暂无自定义修饰符"),
                    centerX, panelTopY + PANEL_HEIGHT / 2, 0xFFFFFF);
        }
    }

    private void renderVScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + PANEL_WIDTH - 12;
        int sbY = panelTopY + 34;
        int sbH = PANEL_HEIGHT - 68;
        g.fill(sbX, sbY, sbX + SCROLL_W, sbY + sbH, 0xFF111828);
        g.fill(sbX + 1, sbY + 1, sbX + SCROLL_W - 1, sbY + sbH - 1, 0x55334466);
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hover = mouseX >= sbX && mouseX < sbX + SCROLL_W && mouseY >= thumbY
                && mouseY < thumbY + thumbH;
        g.fill(sbX, thumbY, sbX + SCROLL_W, thumbY + thumbH, hover ? 0xFF8899CC : 0xFF556699);
        g.fill(sbX + 1, thumbY + 1, sbX + SCROLL_W - 1, thumbY + thumbH - 1, hover ? 0xFFAABBEE : 0xFF7788BB);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelLeftX && mouseX < panelLeftX + PANEL_WIDTH
                && mouseY >= panelTopY + 32 && mouseY < panelTopY + PANEL_HEIGHT - 34 && maxScroll > 0) {
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
