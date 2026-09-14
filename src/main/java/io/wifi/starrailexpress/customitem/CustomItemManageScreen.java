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
        super(Component.translatableWithFallback("sre.custom_item.manage.title", "管理自定义列车物品"));
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
                .builder(Component.translatableWithFallback("sre.custom_item.back", "返回"),
                        b -> minecraft.setScreen(backScreenSupplier.get()))
                .bounds(panelLeftX + 10, panelTopY + PANEL_HEIGHT - 28, 90, 20).accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(backBtn);

        ModernButton newBtn = ModernButton
                .builder(Component.translatableWithFallback("sre.custom_item.new", "新建物品"),
                        b -> minecraft.setScreen(new CustomItemScreen()))
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
                .translatableWithFallback("sre.custom_item.manage.title", "管理自定义列车物品")
                .withStyle(s -> s.withColor(0x55BBFF).withBold(true)), centerX, panelTopY + 10, 0xFFFFFF);

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
            String info = Component
                    .translatableWithFallback("sre.custom_item.kind." + item.kind().name().toLowerCase(),
                            item.kind().name())
                    .getString()
                    + "  " + summary(item);
            g.drawString(font, Component.literal(info).withStyle(Style.EMPTY.withColor(0x98A2B3)),
                    panelLeftX + 316, y + 6, 0xFFFFFF, false);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            renderVScrollbar(g, mouseX, mouseY);
        }
        if (items.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatableWithFallback("sre.custom_item.manage.empty", "§7暂无自定义列车物品"),
                    centerX, panelTopY + PANEL_HEIGHT / 2, 0xFFFFFF);
        }
    }

    private static String summary(CustomItemData item) {
        return switch (item.kind()) {
            case BASIC -> "指令:" + item.commands.size();
            case CHARGE -> item.chargeTicks + "t" + (item.affectOthers ? " 群体" : "");
            case GUN -> (item.autoFire ? "自动" : "手动") + (item.ammoSystem ? " 弹药" + item.maxAmmo : "");
            case VANILLA_WEAPON -> "伤害:" + item.virtualDamage;
            case FOOD -> "饥饿:" + item.nutrition + (item.isDrink ? " 饮料" : "");
        };
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
