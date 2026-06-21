package io.wifi.ConfigCompact.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TestScreen extends Screen {
    Screen parent;

    public TestScreen(Screen screen) {
        super(Component.literal("Test Screen"));
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
