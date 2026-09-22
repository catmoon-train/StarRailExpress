package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.MushroomCultivationC2SPacket;

import java.util.ArrayList;
import java.util.List;

public class MushroomCultivationScreen extends Screen {
    private final List<Integer> pours = new ArrayList<>();
    private int dragging = -1;
    private int left;
    private int top;
    private int tableLeft;
    private int tableTop;

    public MushroomCultivationScreen() {
        super(Component.translatable("screen.noellesroles.mushroom.title"));
    }

    @Override
    protected void init() {
        left = (width - 360) / 2;
        top = (height - 220) / 2;
        tableLeft = left + 110;
        tableTop = top + 65;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(left, top, left + 360, top + 220, 0xF01C2028);
        graphics.fill(left + 8, top + 8, left + 352, top + 42, 0xFF344657);
        graphics.drawCenteredString(font, title, width / 2, top + 19, 0xFFFFFFFF);
        graphics.fill(tableLeft, tableTop, tableLeft + 140, tableTop + 105, 0xFF8A6549);
        graphics.fill(tableLeft + 8, tableTop + 8, tableLeft + 132, tableTop + 97, 0xFFB88A61);
        graphics.renderItem(ModItems.MUSHROOM_SAMPLE.getDefaultInstance(), tableLeft + 59, tableTop + 40);
        drawBottle(graphics, left + 28, top + 85, 0xFF55C7A5, "A", true);
        drawBottle(graphics, left + 286, top + 85, 0xFFB96CFF, "B", true);
        graphics.drawCenteredString(font, Component.translatable("screen.noellesroles.mushroom.drag"),
                width / 2, top + 184, 0xFFE7E7E7);
        graphics.drawCenteredString(font, Component.translatable("screen.noellesroles.mushroom.progress",
                pours.size(), 3), width / 2, top + 201, 0xFFB8D8FF);
        super.render(graphics, mouseX, mouseY, delta);
        if (dragging >= 0) {
            int color = dragging == 0 ? 0xFF55C7A5 : 0xFFB96CFF;
            drawBottle(graphics, mouseX - 21, mouseY - 29, color, dragging == 0 ? "A" : "B", false);
        }
    }

    private void drawBottle(GuiGraphics graphics, int x, int y, int color, String label, boolean background) {
        if (background) graphics.fill(x, y, x + 42, y + 58, 0xFF20252E);
        graphics.fill(x + 10, y + 16, x + 32, y + 49, color);
        graphics.fill(x + 14, y + 8, x + 28, y + 18, 0xFFD8D8D8);
        graphics.drawCenteredString(font, label, x + 21, y + 62, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || pours.size() >= 3) return true;
        if (inside(mouseX, mouseY, left + 20, top + 75, left + 75, top + 155)) dragging = 0;
        if (inside(mouseX, mouseY, left + 275, top + 75, left + 340, top + 155)) dragging = 1;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging >= 0) {
            if (inside(mouseX, mouseY, tableLeft, tableTop, tableLeft + 140, tableTop + 105)) {
                pours.add(dragging);
                if (pours.size() == 3) {
                    ClientPlayNetworking.send(new MushroomCultivationC2SPacket(pours.stream().mapToInt(i -> i).toArray()));
                    onClose();
                }
            }
            dragging = -1;
        }
        return true;
    }

    private boolean inside(double x, double y, int x1, int y1, int x2, int y2) {
        return x >= x1 && x < x2 && y >= y1 && y < y2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
