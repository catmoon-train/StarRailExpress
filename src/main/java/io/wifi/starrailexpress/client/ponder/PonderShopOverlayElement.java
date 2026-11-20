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

package io.wifi.starrailexpress.client.ponder;

import io.wifi.starrailexpress.api.SRERole;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 在思索场景里显示本项目的职业商店。
 *
 * <p>项目的商店界面 {@code LimitedInventoryScreen} 依赖真实的 {@code InventoryMenu}
 * 与服务端同步数据，无法在 Ponder 的离屏沙盒里构造。这里改为按同一份数据源
 * （{@code ShopContent#getShopEntries}）自绘一个等价的商店陈列：
 * 网格排布商品图标、价格与货币字形，配色沿用项目商店界面。
 *
 * <p>它是 {@link PonderOverlayElement}，因此跟随思索 UI 一起绘制在场景之上。
 */
public class PonderShopOverlayElement implements PonderOverlayElement {

    /** 单个商品格边长（GUI 像素），与商店界面的 30x30 图标位一致。 */
    private static final int CELL = 30;
    private static final int CELL_GAP = 8;
    private static final int ROW_GAP = 12;

    private final List<Entry> entries;
    @Nullable
    private final Component title;
    private final int columns;

    private boolean visible = true;

    /** 商店中的一个陈列项。 */
    public record Entry(ItemStack stack, int price, String priceText, int priceColor) {
    }

    public PonderShopOverlayElement(List<Entry> entries, @Nullable Component title, int columns) {
        this.entries = List.copyOf(entries);
        this.title = title;
        this.columns = Mth.clamp(columns, 1, Math.max(1, this.entries.size()));
    }

    /** 用职业的商店条目构造覆盖层；没有商品时返回 {@code null}。 */
    @Nullable
    public static PonderShopOverlayElement forRole(@Nullable SRERole role, int columns) {
        List<Entry> built = PonderShopEntries.build(role);
        if (built.isEmpty()) {
            return null;
        }
        Component title = Component.translatable("gui.starrailexpress.shop.role_shop");
        return new PonderShopOverlayElement(built, title, columns);
    }

    /** 把覆盖层加入场景；返回元素本身以便后续控制可见性。 */
    public PonderShopOverlayElement addTo(SceneBuilder scene) {
        scene.addInstruction(ps -> {
            ps.addElement(this);
            this.visible = true;
        });
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
        if (!visible || entries.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int rows = Math.ceilDiv(entries.size(), columns);
        int gridWidth = columns * CELL + (columns - 1) * CELL_GAP;
        int gridHeight = rows * CELL + (rows - 1) * (ROW_GAP + font.lineHeight);

        int centerX = screen.width / 2;
        // 靠下摆放，避免遮挡场景中心的模型与文字。
        int top = screen.height - gridHeight - 52;
        int left = centerX - gridWidth / 2;

        renderPanel(graphics, left - 10, top - 24, gridWidth + 20, gridHeight + 34);

        if (title != null) {
            graphics.drawCenteredString(font, title, centerX, top - 18, 0xFFD700);
        }

        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            int column = index % columns;
            int row = index / columns;
            int cellX = left + column * (CELL + CELL_GAP);
            int cellY = top + row * (CELL + ROW_GAP + font.lineHeight);

            renderCell(graphics, cellX, cellY);
            graphics.renderItem(entry.stack(), cellX + (CELL - 16) / 2, cellY + (CELL - 16) / 2);

            Component price = Component.literal(entry.priceText());
            graphics.drawCenteredString(font, price, cellX + CELL / 2,
                    cellY + CELL + 2, entry.priceColor());
        }
    }

    private static void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xC0101018);
        graphics.renderOutline(x, y, width, height, 0xFF3A3A4A);
    }

    private static void renderCell(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + CELL, y + CELL, 0xFF1B1B24);
        graphics.renderOutline(x, y, CELL, CELL, 0xFF55556A);
    }
}
