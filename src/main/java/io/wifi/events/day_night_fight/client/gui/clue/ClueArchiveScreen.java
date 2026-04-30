package io.wifi.events.day_night_fight.client.gui.clue;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class ClueArchiveScreen extends Screen {
    private long openTime;

    public ClueArchiveScreen() {
        super(Component.translatable("screen.sre.clue_archive.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = Util.getMillis();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        float t = Mth.clamp((Util.getMillis() - openTime) / 400.0f, 0f, 1f);
        int alpha = (int) (220 * t);
        int panelW = (int) (this.width * 0.7f);
        int panelH = (int) (this.height * 0.75f);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        graphics.fill(x, y, x + panelW, y + panelH, (alpha << 24) | 0x101826);
        graphics.drawCenteredString(this.font, Component.translatable("screen.sre.clue_archive.header"), this.width / 2, y + 14, 0xAEE7FF);
        graphics.drawString(this.font, Component.translatable("screen.sre.clue_archive.line1"), x + 20, y + 42, 0xD9E8FF, false);
        graphics.drawString(this.font, Component.translatable("screen.sre.clue_archive.line2"), x + 20, y + 58, 0xD9E8FF, false);
        graphics.drawString(this.font, Component.translatable("screen.sre.clue_archive.line3"), x + 20, y + 74, 0xD9E8FF, false);
        graphics.drawString(this.font, Component.translatable("screen.sre.clue_archive.line4"), x + 20, y + 90, 0xD9E8FF, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
