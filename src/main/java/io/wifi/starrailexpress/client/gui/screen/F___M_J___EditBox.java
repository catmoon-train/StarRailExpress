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
