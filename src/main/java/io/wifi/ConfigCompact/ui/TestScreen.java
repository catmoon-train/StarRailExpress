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

package io.wifi.ConfigCompact.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TestScreen extends Screen {
    Screen parent;

    public TestScreen(Screen screen) {
        super(Component.literal("Test Screen"));
        this.parent = screen;
    }

    int lastKeyCode;
    int lastScanCode;
    int lastModifiers;

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font,
                Component.translatable("Width: %s, Height: %s, MouseX: %s, MouseY: %s", width, height, mouseX, mouseY), this.width / 2,
                20, java.awt.Color.WHITE.getRGB());

        context.drawCenteredString(font,
                Component.translatable("KeyCode: %s, ScanCode: %s, Modifiers: %s", lastKeyCode, lastScanCode,
                        lastModifiers),
                this.width / 2,
                40, java.awt.Color.CYAN.getRGB());
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.lastKeyCode = keyCode;
        this.lastScanCode = scanCode;
        this.lastModifiers = modifiers;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onClose() {
        this.minecraft.setScreen(parent);
    }

}
