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

package org.agmas.noellesroles.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 抽象像素屏幕类
 * <p>
 *     - 用于实现需要像素级放大的屏幕
 * </p>
 */
public class AbstractPixelScreen extends Screen {
    protected AbstractPixelScreen(Component component) {
        super(component);
    }
    @Override
    protected void init()
    {
        super.init();
        centerX = width / 2;
        centerY = height / 2;
    }
    protected int centerX = 0;
    protected int centerY = 0;
    protected int pixelSize = 1;// 最大（默认）像素缩放的大小
}
