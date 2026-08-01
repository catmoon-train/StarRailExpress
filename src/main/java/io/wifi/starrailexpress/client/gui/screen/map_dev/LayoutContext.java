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

    public LayoutContext(int panelLeftX, int panelTopY, int panelWidth, int panelHeight,
            int contentStartY, int contentEndY, int gutter, Font font) {
        this.panelLeftX = panelLeftX;
        this.panelTopY = panelTopY;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.contentStartY = contentStartY;
        this.contentEndY = contentEndY;
        this.gutter = gutter;
        this.font = font;
    }

    public int contentWidth() {
        return panelWidth - gutter * 2;
    }

    public int columnWidth(int columns, int gap) {
        return (contentWidth() - gap * (columns - 1)) / columns;
    }

    public int leftColumnX() {
        return panelLeftX + gutter;
    }

    public int rightColumnX(int columns, int gap) {
        return leftColumnX() + columnWidth(columns, gap) + gap;
    }
}