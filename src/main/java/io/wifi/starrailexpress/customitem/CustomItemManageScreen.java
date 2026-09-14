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

package io.wifi.starrailexpress.customitem;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
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
 * 自定义列车物品管理界面（列表 / 新建 / 删除），与 {@code CustomModifierManageScreen} 对应。
 */
@Environment(EnvType.CLIENT)
public class CustomItemManageScreen extends Screen {

    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 380;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 12;

    private int panelLeftX, panelTopY;
    private final Supplier<Screen> backScreenSupplier;
    private List<CustomItemData> items = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;
    /** 需要重建界面时置为 true，在 render 中统一重建（避免在控件回调里改控件列表）。 */
    private boolean pendingRebuild = false;

    private void requestRebuild() {
        this.pendingRebuild = true;
    }

    public CustomItemManageScreen(Supplier<Screen> backSupplier) {
        super(Component.translatable("sre.custom_item.manage.title"));
        this.backScreenSupplier = backSupplier;
    }

    public CustomItemManageScreen(Screen backScreen) {
        this(() -> backScreen);
    }

    @Override
    protected void init() {
        panelLeftX = (width - PANEL_WIDTH) / 2;
        panelTopY = (height - PANEL_HEIGHT) / 2;

        CustomItemConfig config = CustomItemConfig.loadPreferWorldPath(minecraft.getSingleplayerServer());
        items = config.items;

        maxScroll = Math.max(0, (items.size() - VISIBLE_ROWS) * ROW_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(VISIBLE_ROWS + 1, items.size() - startIdx);

        for (int i = 0; i < visibleCount; i++) {
            final int index = startIdx + i;
            if (index >= items.size())
                break;
            CustomItemData item = items.get(index);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);

            String displayText = item.id
                    + (item.displayName == null || item.displayName.isEmpty() ? "" : " (" + item.displayName + ")");
            ModernButton nameBtn = ModernButton
                    .builder(Component.literal(displayText), b -> minecraft.setScreen(new CustomItemScreen(item)))
                    .bounds(panelLeftX + 10, y, 260, 20).accentBar(AccentSide.LEFT).build();
            addRenderableWidget(nameBtn);

            ModernButton delBtn = ModernButton.builder(Component.literal("X"), b -> {
                config.items.remove(index);
                config.savePreferWorldPath(minecraft.getSingleplayerServer());
                var server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            CustomItemLoader.reload(server);
                            io.wifi.starrailexpress.network.CustomItemServerNetwork.clearCache();
                            io.wifi.starrailexpress.network.CustomItemServerNetwork.syncToAllPlayers(server);
                        } catch (Exception ignored) {
                        }
                    });
                }
                requestRebuild();
            }).bounds(panelLeftX + 278, y, 30, 20).accentBar(AccentSide.RIGHT).build();
            addRenderableWidget(delBtn);
        }

        ModernButton backBtn = ModernButton
                .builder(Component.translatable("sre.custom_item.back"),
                        b -> minecraft.setScreen(backScreenSupplier.get()))
                .bounds(panelLeftX + 10, panelTopY + PANEL_HEIGHT - 28, 90, 20).accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(backBtn);

        ModernButton newBtn = ModernButton
                .builder(Component.translatable("sre.custom_item.new"),
                        b -> minecraft.setScreen(new CustomItemScreen()))
                .bounds(panelLeftX + 110, panelTopY + PANEL_HEIGHT - 28, 110, 20).accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(newBtn);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        SREPanelStyle.drawPanel(g, panelLeftX - 6, panelTopY - 3, PANEL_WIDTH + 12, PANEL_HEIGHT + 6);
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
                .translatable("sre.custom_item.manage.title")
                .withStyle(s -> s.withColor(0xFFD4AF37).withBold(true)), centerX, panelTopY + 10, 0xFFFFFF);

        int listTop = panelTopY + 32;
        int listBottom = panelTopY + PANEL_HEIGHT - 34;
        g.enableScissor(panelLeftX + 8, listTop, panelLeftX + PANEL_WIDTH - 16, listBottom);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(VISIBLE_ROWS + 1, items.size() - startIdx);
        for (int i = 0; i < visibleCount; i++) {
            int index = startIdx + i;
            if (index >= items.size())
                break;
            CustomItemData item = items.get(index);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);
            Component info = Component.translatable("sre.custom_item.kind." + item.kind().name().toLowerCase())
                    .append(Component.literal("  ").append(summary(item)));
            g.drawString(font, info.copy().withStyle(Style.EMPTY.withColor(0xFF9E8B6E)),
                    panelLeftX + 316, y + 6, 0xFFFFFF, false);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            renderVScrollbar(g, mouseX, mouseY);
        }
        if (items.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("sre.custom_item.manage.empty").withStyle(s -> s.withColor(0xFF9E8B6E)),
                    centerX, panelTopY + PANEL_HEIGHT / 2, 0xFFFFFF);
        }
    }

    /** 列表右侧摘要（全部走翻译键）。 */
    private static Component summary(CustomItemData item) {
        return switch (item.kind()) {
            case BASIC -> Component.translatable("sre.custom_item.summary.commands", item.commands.size());
            case CHARGE -> Component
                    .translatable("sre.custom_item.summary.charge", item.chargeTicks)
                    .append(item.affectOthers ? Component.translatable("sre.custom_item.summary.multi")
                            : Component.empty());
            case GUN -> Component
                    .translatable(item.autoFire ? "sre.custom_item.summary.auto" : "sre.custom_item.summary.manual")
                    .append(item.ammoSystem
                            ? Component.translatable("sre.custom_item.summary.ammo", item.maxAmmo)
                            : Component.empty());
            case VANILLA_WEAPON -> Component.translatable("sre.custom_item.summary.damage", item.virtualDamage);
            case FOOD -> Component
                    .translatable("sre.custom_item.summary.nutrition", item.nutrition)
                    .append(item.isDrink ? Component.translatable("sre.custom_item.summary.drink")
                            : Component.empty());
        };
    }

    private void renderVScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + PANEL_WIDTH - 12;
        int sbY = panelTopY + 34;
        int sbH = PANEL_HEIGHT - 68;
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
