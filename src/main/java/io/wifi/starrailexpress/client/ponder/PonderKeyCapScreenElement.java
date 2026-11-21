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

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 键帽图标：交给 Ponder 的 {@code overlay().showControls(...).showing(icon)} 使用。
 *
 * <p>这样按键提示和 Ponder 自带的鼠标左/右键图标走同一套摆放逻辑——锚在世界坐标上、
 * 带对话框描边、随镜头与文本框自动挪位，而不是自己算屏幕位置。
 */
public class PonderKeyCapScreenElement implements ScreenElement {

    private static final int HEIGHT = 14;

    private final String keyName;
    private final String caption;
    private final int accent;

    public PonderKeyCapScreenElement(String keyName, String caption, int accent) {
        this.keyName = keyName == null ? "" : keyName;
        this.caption = caption == null ? "" : caption;
        this.accent = accent;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        Font font = Minecraft.getInstance().font;
        int width = Math.max(HEIGHT, font.width(keyName) + 6);

        graphics.fill(x, y, x + width, y + HEIGHT, 0xE0141418);
        graphics.renderOutline(x, y, width, HEIGHT, accent);
        graphics.drawString(font, keyName, x + (width - font.width(keyName)) / 2,
                y + (HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);

        if (!caption.isEmpty()) {
            graphics.drawString(font, caption, x, y + HEIGHT + 3, 0xFFFFFFFF, false);
        }
    }
}
