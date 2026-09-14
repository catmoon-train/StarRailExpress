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

package io.wifi.starrailexpress.customblock;

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
 * 自定义方块管理界面（列表 / 新建 / 删除），与 {@link CustomBlockData} 的编辑器配套。
 */
@Environment(EnvType.CLIENT)
public class CustomBlockManageScreen extends Screen {

    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 380;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 12;

    private int panelLeftX, panelTopY;
    private final Supplier<Screen> backScreenSupplier;
    private List<CustomBlockData> blocks = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;
    /** 需要重建界面时置为 true，在 render 中统一重建（避免在控件回调里改控件列表）。 */
    private boolean pendingRebuild = false;

    private void requestRebuild() {
        this.pendingRebuild = true;
    }

    public CustomBlockManageScreen(Supplier<Screen> backSupplier) {
        super(Component.translatable("sre.custom_block.manage.title"));
        this.backScreenSupplier = backSupplier;
    }

    public CustomBlockManageScreen(Screen backScreen) {
        this(() -> backScreen);
    }

    @Override
    protected void init() {
        panelLeftX = (width - PANEL_WIDTH) / 2;
        panelTopY = (height - PANEL_HEIGHT) / 2;

        CustomBlockConfig config = CustomBlockConfig.loadPreferWorldPath(minecraft.getSingleplayerServer());
        blocks = config.blocks;

        maxScroll = Math.max(0, (blocks.size() - VISIBLE_ROWS) * ROW_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(VISIBLE_ROWS + 1, blocks.size() - startIdx);

        for (int i = 0; i < visibleCount; i++) {
            final int index = startIdx + i;
            if (index >= blocks.size())
                break;
            CustomBlockData block = blocks.get(index);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);

            String displayText = block.id
                    + (block.displayName == null || block.displayName.isEmpty() ? "" : " (" + block.displayName + ")");
            ModernButton nameBtn = ModernButton
                    .builder(Component.literal(displayText), b -> minecraft.setScreen(new CustomBlockScreen(block)))
                    .bounds(panelLeftX + 10, y, 300, 20).accentBar(AccentSide.LEFT).build();
            addRenderableWidget(nameBtn);

            ModernButton delBtn = ModernButton.builder(Component.literal("X"), b -> {
                config.blocks.remove(index);
                config.savePreferWorldPath(minecraft.getSingleplayerServer());
                var server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            CustomBlockReloadCommand.reload(server);
                        } catch (Exception ignored) {
                        }
                    });
                }
                requestRebuild();
            }).bounds(panelLeftX + 318, y, 30, 20).accentBar(AccentSide.RIGHT).build();
            addRenderableWidget(delBtn);
        }

        ModernButton backBtn = ModernButton
                .builder(Component.translatable("sre.custom_block.back"),
                        b -> minecraft.setScreen(backScreenSupplier.get()))
                .bounds(panelLeftX + 10, panelTopY + PANEL_HEIGHT - 28, 90, 20).accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(backBtn);

        ModernButton newBtn = ModernButton
                .builder(Component.translatable("sre.custom_block.new"),
                        b -> minecraft.setScreen(new CustomBlockScreen()))
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
                .translatable("sre.custom_block.manage.title")
                .withStyle(s -> s.withColor(0x55BBFF).withBold(true)), centerX, panelTopY + 10, 0xFFFFFF);

        int listTop = panelTopY + 32;
        int listBottom = panelTopY + PANEL_HEIGHT - 34;
        g.enableScissor(panelLeftX + 8, listTop, panelLeftX + PANEL_WIDTH - 16, listBottom);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(VISIBLE_ROWS + 1, blocks.size() - startIdx);
        for (int i = 0; i < visibleCount; i++) {
            int index = startIdx + i;
            if (index >= blocks.size())
                break;
            CustomBlockData block = blocks.get(index);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);
            g.drawString(font, summary(block).copy().withStyle(Style.EMPTY.withColor(0x98A2B3)),
                    panelLeftX + 356, y + 6, 0xFFFFFF, false);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            renderVScrollbar(g, mouseX, mouseY);
        }
        if (blocks.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("sre.custom_block.manage.empty").withStyle(s -> s.withColor(0x98A2B3)),
                    centerX, panelTopY + PANEL_HEIGHT / 2, 0xFFFFFF);
        }
    }

    /** 列表右侧摘要（全部走翻译键）。 */
    private static Component summary(CustomBlockData block) {
        Component appearance;
        if (block.packTexturePath != null && !block.packTexturePath.isBlank()) {
            appearance = Component.translatable("sre.custom_block.summary.texture");
        } else if (block.inheritBlock != null && !block.inheritBlock.isBlank()) {
            appearance = Component.translatable("sre.custom_block.summary.inherit", block.inheritBlock);
        } else {
            appearance = Component.translatable("sre.custom_block.summary.none");
        }
        Component eventCount = Component.translatable("sre.custom_block.summary.events",
                block.events == null ? 0 : block.events.size());
        return appearance.copy().append(Component.literal("  ")).append(eventCount);
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
