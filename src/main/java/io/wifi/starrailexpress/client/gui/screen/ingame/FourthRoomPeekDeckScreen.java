package io.wifi.starrailexpress.client.gui.screen.ingame;

import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientSnapshot;
import io.wifi.starrailexpress.client.fourthroom.FourthRoomClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FourthRoomPeekDeckScreen extends Screen {
    private static final int CARD_WIDTH = 108;
    private static final int CARD_HEIGHT = 148;
    @Nullable
    private final Screen parent;

    public FourthRoomPeekDeckScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.fourth_room.peek_deck_title"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        FourthRoomClientSnapshot snapshot = FourthRoomClientState.snapshot();
        if (!snapshot.active()) {
            onClose();
            return;
        }

        graphics.fillGradient(0, 0, width, height, 0xE612151D, 0xF005060A);
        int panelWidth = Math.min(width - 80, 920);
        int panelHeight = Math.min(height - 72, 420);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        drawPanel(graphics, panelX, panelY, panelWidth, panelHeight, 0xD8181C26, 0x66D7BC7D);

        graphics.drawCenteredString(font, title, width / 2, panelY + 14, 0xFFF3DCA0);
        graphics.drawCenteredString(font,
                Component.translatable("screen.fourth_room.peek_deck_subtitle", snapshot.viewer().peekCards().size(),
                        snapshot.viewer().drawPileSize()),
                width / 2, panelY + 30, 0xFFD7E3F0);
        graphics.drawCenteredString(font,
                Component.translatable("screen.fourth_room.peek_deck_hint"),
                width / 2, panelY + panelHeight - 18, 0xFFBFC7D1);

        renderDeckPile(graphics, panelX + panelWidth - 170, panelY + panelHeight / 2 + 78, snapshot.viewer().drawPileSize());
        renderPeekCards(graphics, snapshot.viewer().peekCards(), panelX + 24, panelY + 56, panelWidth - 220, panelHeight - 100);
    }

    private void renderDeckPile(GuiGraphics graphics, int centerX, int bottomY, int pileSize) {
        int layers = Math.max(2, Math.min(7, pileSize));
        for (int index = 0; index < layers; index++) {
            graphics.pose().pushPose();
            graphics.pose().translate(centerX + index * 2.0F, bottomY - index * 4.0F, 20.0F + index);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(-7.0F + index * 2.0F));
            int left = -CARD_WIDTH / 2;
            int top = -CARD_HEIGHT;
            graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, 0xFFF1E6D1);
            graphics.renderOutline(left, top, CARD_WIDTH, CARD_HEIGHT, 0xFF9A7947);
            graphics.fill(left + 8, top + 8, left + CARD_WIDTH - 8, top + 24, 0xFFE7D0A0);
            graphics.drawCenteredString(font, Component.translatable("screen.fourth_room.peek_deck_stack"), 0, top + 12,
                    0xFF221B14);
            graphics.pose().popPose();
        }
        graphics.drawCenteredString(font,
                Component.translatable("screen.fourth_room.peek_deck_count", pileSize),
                centerX, bottomY + 18, 0xFFE7D0A0);
    }

    private void renderPeekCards(GuiGraphics graphics, List<FourthRoomClientSnapshot.PeekCard> cards, int x, int y,
            int width, int height) {
        if (cards.isEmpty()) {
            drawPanel(graphics, x, y + 42, width, height - 60, 0xAA141820, 0x444C5868);
            graphics.drawCenteredString(font, Component.translatable("screen.fourth_room.peek_deck_empty"),
                    x + width / 2, y + height / 2, 0xFFD0D5DC);
            return;
        }
        int count = cards.size();
        int spacing = Math.min(124, Math.max(98, (width - CARD_WIDTH) / Math.max(1, count - 1)));
        int totalWidth = CARD_WIDTH + Math.max(0, count - 1) * spacing;
        int startCenterX = x + Math.max(CARD_WIDTH / 2, (width - totalWidth) / 2 + CARD_WIDTH / 2);
        int bottomY = y + height - 10;
        for (int index = 0; index < cards.size(); index++) {
            float mid = (cards.size() - 1) / 2.0F;
            int centerX = startCenterX + index * spacing;
            float rotation = (index - mid) * 4.5F;
            drawPeekCard(graphics, cards.get(index), centerX, bottomY, rotation);
        }
    }

    private void drawPeekCard(GuiGraphics graphics, FourthRoomClientSnapshot.PeekCard card, int centerX, int bottomY,
            float rotation) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, bottomY, 30.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        int left = -CARD_WIDTH / 2;
        int top = -CARD_HEIGHT;
        int border = card.skill() ? 0xFF4F87B7 : 0xFF8B6B3C;
        graphics.fill(left, top, left + CARD_WIDTH, top + CARD_HEIGHT, 0xFFF2E7D1);
        graphics.renderOutline(left, top, CARD_WIDTH, CARD_HEIGHT, border);
        graphics.fill(left + 8, top + 8, left + CARD_WIDTH - 8, top + 24, 0xFFEAD8B4);
        graphics.drawCenteredString(font, Component.literal(fit(card.displayName(), CARD_WIDTH - 18)), 0, top + 12,
                0xFF221B14);
        graphics.drawString(font, Component.translatable(card.skill() ? "screen.fourth_room.peek_deck_skill" : "screen.fourth_room.peek_deck_top"), left + 10, top + 34, 0xFF705F4A, false);
        List<FormattedCharSequence> lines = font.split(Component.literal(card.description()), CARD_WIDTH - 18);
        int textY = top + 52;
        for (int index = 0; index < Math.min(6, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), left + 10, textY, 0xFF382F27, false);
            textY += 11;
        }
        graphics.pose().popPose();
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    private String fit(String text, int width) {
        return font.plainSubstrByWidth(text, Math.max(24, width));
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
