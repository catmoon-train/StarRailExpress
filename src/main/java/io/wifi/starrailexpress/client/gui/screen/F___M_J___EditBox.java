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

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class F___M_J___EditBox extends EditBox {

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (!isMouseOver(mx, my))
            return false;
        this.setFocused(true);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        return false;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (!isVisible())
            return false;
        if (!this.isFocused())
            return false;

        var bl = super.keyPressed(k, s, m);
        if (!bl) {
            if (Minecraft.getInstance().options.keyInventory.matches(k, s)
                    || SREClient.statsKeybind.matches(k, s)) {
                bl = true;
            }
        }
        return bl;
    }

    public F___M_J___EditBox(Font font, int i, int j, int k, int m, Component component) {
        super(font, i, j, k, m, component);
    }

}
