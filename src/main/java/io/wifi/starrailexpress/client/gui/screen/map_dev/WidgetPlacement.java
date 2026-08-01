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

import net.minecraft.client.gui.components.AbstractWidget;

public class WidgetPlacement {
    public final AbstractWidget widget;
    public final int relativeY; // 相对于内容起始 Y 的偏移

    public WidgetPlacement(AbstractWidget widget, int relativeY) {
        this.widget = widget;
        this.relativeY = relativeY;
    }
}