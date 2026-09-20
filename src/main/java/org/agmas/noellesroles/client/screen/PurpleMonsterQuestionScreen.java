package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.PurpleMonsterEventC2SPacket;

import java.util.UUID;

/** PNG-backed question panel with an intentionally disabled first choice. */
public final class PurpleMonsterQuestionScreen extends Screen {
    private static final int TEXTURE_SIZE = 1536;
    private static final int SOURCE_X = 122;
    private static final int SOURCE_Y = 78;
    private static final int SOURCE_WIDTH = 1196;
    private static final int SOURCE_HEIGHT = 940;
    private static final int MAX_GUI_WIDTH = 320;
    private static final net.minecraft.resources.ResourceLocation BACKGROUND = Noellesroles.id(
            "textures/gui/purple_monster_event_background.png");
    private final UUID eventId;
    private boolean answered;

    public PurpleMonsterQuestionScreen(UUID eventId) {
        super(Component.literal("想要复仇吗"));
        this.eventId = eventId;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        int width = guiWidth();
        int height = guiHeight(width);
        int left = guiLeft(width);
        int top = guiTop(height);
        graphics.blit(BACKGROUND, left, top, width, height, SOURCE_X, SOURCE_Y,
                SOURCE_WIDTH, SOURCE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);

        graphics.drawCenteredString(this.font, Component.literal("想要复仇吗"), left + width * 81 / 100,
                top + height * 25 / 100, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("不想，我会继续逃"), left + width / 2,
                top + height * 60 / 100, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("想，我要摧毁他"), left + width / 2,
                top + height * 80 / 100, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int width = guiWidth();
        int height = guiHeight(width);
        if (button == 0 && inside(mouseX, mouseY, guiLeft(width), guiTop(height), width, height,
                218, 773, 1250, 900)) answer();
        return true;
    }

    private void answer() {
        if (answered) return;
        answered = true;
        ClientPlayNetworking.send(new PurpleMonsterEventC2SPacket(eventId,
                PurpleMonsterEventC2SPacket.Action.ANSWER, null));
    }

    private static boolean inside(double mouseX, double mouseY, int left, int top, int width, int height,
                                  int x1, int y1, int x2, int y2) {
        return mouseX >= left + (x1 - SOURCE_X) * width / (double) SOURCE_WIDTH
                && mouseX <= left + (x2 - SOURCE_X) * width / (double) SOURCE_WIDTH
                && mouseY >= top + (y1 - SOURCE_Y) * height / (double) SOURCE_HEIGHT
                && mouseY <= top + (y2 - SOURCE_Y) * height / (double) SOURCE_HEIGHT;
    }

    private int guiWidth() {
        return Math.min(MAX_GUI_WIDTH, Math.min(this.width - 20,
                (this.height - 20) * SOURCE_WIDTH / SOURCE_HEIGHT));
    }

    private int guiHeight(int width) { return width * SOURCE_HEIGHT / SOURCE_WIDTH; }

    private int guiLeft(int width) { return (this.width - width) / 2; }

    private int guiTop(int height) { return (this.height - height) / 2; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
