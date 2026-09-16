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

import net.minecraft.client.gui.Font;

public class LayoutContext {
    public final int panelLeftX, panelTopY;
    public final int panelWidth, panelHeight;
    public final int contentStartY;
    public final int contentEndY;
    public final int gutter;
    public final Font font;
    /**
     * 内容区的右边界（不含尾部留白与滚动条槽）。
     *
     * <p>
     * 由界面按 {@code EditorLayout} 算好传进来：滚动条槽是永久预留的，所以模块的控件右端到
     * 这里为止就不会钻到滚动条底下。以前每个模块各自用 {@code panelWidth - 10} 或直接
     * {@code contentWidth()} 推，结果最小面板宽度下会压住滚动条。
     */
    public final int contentRight;
    /** 自绘头部区域：标题条下方、页签栏上方（模块可以往这里放自己的固定行，例如搜索框）。 */
    public final int headerTop;
    public final int headerBottom;

    public LayoutContext(int panelLeftX, int panelTopY, int panelWidth, int panelHeight,
            int contentStartY, int contentEndY, int gutter, Font font) {
        this(panelLeftX, panelTopY, panelWidth, panelHeight, contentStartY, contentEndY, gutter, font,
                panelLeftX + panelWidth - gutter - 4);
    }

    public LayoutContext(int panelLeftX, int panelTopY, int panelWidth, int panelHeight,
            int contentStartY, int contentEndY, int gutter, Font font, int contentRight) {
        this(panelLeftX, panelTopY, panelWidth, panelHeight, contentStartY, contentEndY, gutter, font,
                contentRight, contentStartY, contentStartY);
    }

    public LayoutContext(int panelLeftX, int panelTopY, int panelWidth, int panelHeight,
            int contentStartY, int contentEndY, int gutter, Font font, int contentRight, int headerTop,
            int headerBottom) {
        this.panelLeftX = panelLeftX;
        this.panelTopY = panelTopY;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.contentStartY = contentStartY;
        this.contentEndY = contentEndY;
        this.gutter = gutter;
        this.font = font;
        this.contentRight = contentRight;
        this.headerTop = headerTop;
        this.headerBottom = headerBottom;
    }

    /** 内容区右边界：模块把一行控件排到这里为止。 */
    public int rightEdge() {
        return contentRight;
    }

    /** 一行可用的总宽度（不含尾部留白与滚动条槽）。 */
    public int contentWidth() {
        return Math.max(1, contentRight - leftColumnX());
    }

    public int columnWidth(int columns, int gap) {
        return Math.max(1, (contentWidth() - gap * (columns - 1)) / columns);
    }

    public int leftColumnX() {
        return panelLeftX + gutter;
    }

    public int rightColumnX(int columns, int gap) {
        return leftColumnX() + columnWidth(columns, gap) + gap;
    }

    /** 从右边界往回数 {@code width} 得到的 X（右对齐控件用）。 */
    public int rightAlignedX(int width) {
        return Math.max(leftColumnX(), rightEdge() - width);
    }
}