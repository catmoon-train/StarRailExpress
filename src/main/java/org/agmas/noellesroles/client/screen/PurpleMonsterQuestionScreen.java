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
    // Full visible panel bounds in the 1536x1536 PNG, including its outer shadow.
    private static final int SOURCE_X = 120;
    private static final int SOURCE_Y = 76;
    private static final int SOURCE_WIDTH = 1200;
    private static final int SOURCE_HEIGHT = 946;
    private static final int TITLE_CENTER_X = 1096;
    private static final int TITLE_CENTER_Y = 317;
    private static final int ESCAPE_CENTER_X = 725;
    private static final int ESCAPE_CENTER_Y = 640;
    private static final int DESTROY_CENTER_X = 733;
    private static final int DESTROY_CENTER_Y = 831;
    private static final int DESTROY_X1 = 214;
    private static final int DESTROY_Y1 = 770;
    private static final int DESTROY_X2 = 1253;
    private static final int DESTROY_Y2 = 895;
    private static final int MAX_GUI_WIDTH = 320;
    private static final net.minecraft.resources.ResourceLocation BACKGROUND = Noellesroles.id(
            "textures/gui/purple_monster_event_background.png");
    private final UUID eventId;
    private boolean answered;

    public PurpleMonsterQuestionScreen(UUID eventId) {
        super(Component.translatable("screen.noellesroles.purple_monster.question.title"));
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

        graphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.purple_monster.question.title"),
                sourceToScreenX(left, width, TITLE_CENTER_X), sourceToScreenY(top, height, TITLE_CENTER_Y),
                0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.purple_monster.question.escape"),
                sourceToScreenX(left, width, ESCAPE_CENTER_X), sourceToScreenY(top, height, ESCAPE_CENTER_Y),
                0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.purple_monster.question.destroy"),
                sourceToScreenX(left, width, DESTROY_CENTER_X), sourceToScreenY(top, height, DESTROY_CENTER_Y),
                0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int width = guiWidth();
        int height = guiHeight(width);
        if (button == 0 && inside(mouseX, mouseY, guiLeft(width), guiTop(height), width, height,
                DESTROY_X1, DESTROY_Y1, DESTROY_X2, DESTROY_Y2)) answer();
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

    private static int sourceToScreenX(int left, int width, int sourceX) {
        return left + (sourceX - SOURCE_X) * width / SOURCE_WIDTH;
    }

    private static int sourceToScreenY(int top, int height, int sourceY) {
        return top + (sourceY - SOURCE_Y) * height / SOURCE_HEIGHT;
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
