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
    private static final int GUI_SIZE = 500;
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
        int size = guiSize();
        int left = guiLeft(size);
        int top = guiTop(size);
        graphics.blit(BACKGROUND, left, top, 0, 0, size, size, TEXTURE_SIZE, TEXTURE_SIZE);

        graphics.drawCenteredString(this.font, Component.literal("想要复仇吗"), left + size * 72 / 100,
                top + size * 21 / 100, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("不想，我会继续逃"), left + size / 2,
                top + size * 42 / 100, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("想，我要摧毁他"), left + size / 2,
                top + size * 54 / 100, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int size = guiSize();
        if (button == 0 && inside(mouseX, mouseY, guiLeft(size), guiTop(size), size,
                218, 773, 1250, 900)) answer();
        return true;
    }

    private void answer() {
        if (answered) return;
        answered = true;
        ClientPlayNetworking.send(new PurpleMonsterEventC2SPacket(eventId,
                PurpleMonsterEventC2SPacket.Action.ANSWER, null));
    }

    private static boolean inside(double mouseX, double mouseY, int left, int top, int size,
                                  int x1, int y1, int x2, int y2) {
        return mouseX >= left + x1 * size / (double) TEXTURE_SIZE
                && mouseX <= left + x2 * size / (double) TEXTURE_SIZE
                && mouseY >= top + y1 * size / (double) TEXTURE_SIZE
                && mouseY <= top + y2 * size / (double) TEXTURE_SIZE;
    }

    private int guiSize() {
        return Math.min(GUI_SIZE, Math.min(this.width - 20, this.height - 20));
    }

    private int guiLeft(int size) { return (this.width - size) / 2; }

    private int guiTop(int size) { return (this.height - size) / 2; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
