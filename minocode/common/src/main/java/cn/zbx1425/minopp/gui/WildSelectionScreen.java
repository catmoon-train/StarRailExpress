package cn.zbx1425.minopp.gui;

import cn.zbx1425.minopp.game.Card;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.network.C2SPlayCardPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class WildSelectionScreen extends Screen {

    private final BlockPos gamePos;
    private final CardPlayer player;
    private final Card handCard;
    private final boolean shout;

    public WildSelectionScreen(BlockPos gamePos, CardPlayer player, Card handCard, boolean shout) {
        super(Component.translatable("gui.minopp.wild_selection.title"));
        this.gamePos = gamePos;
        this.player = player;
        this.handCard = handCard;
        this.shout = shout;
    }

    int BTN_WIDTH = 60;
    int BTN_HEIGHT = 20;
    int BTN_SPACING = 10;
    int MARGIN = 8;

    int PANEL_HEIGHT = MARGIN + 9 + MARGIN + BTN_HEIGHT + MARGIN + BTN_HEIGHT + MARGIN;
    int PANEL_WIDTH = MARGIN + BTN_WIDTH * 4 + BTN_SPACING * 3 + MARGIN;

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        int xOff = (width - PANEL_WIDTH) / 2;
        int yOff = (height - PANEL_HEIGHT) / 2;

        for (Card.Suit suit : Card.Suit.values()) {
            if (suit == Card.Suit.WILD) continue;
            addRenderableWidget(Button.builder(Component.translatable("game.minopp.card.suit." + suit.name().toLowerCase(), "").withColor(suit.color), e -> {
                C2SPlayCardPacket.Client.sendPlayCardC2S(gamePos, player, handCard, suit, shout);
                onClose();
            }).pos(xOff + MARGIN + (BTN_WIDTH + BTN_SPACING) * suit.ordinal(), yOff + MARGIN + 9 + MARGIN).size(BTN_WIDTH, BTN_HEIGHT).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), e -> {
            onClose();
        }).pos(xOff + MARGIN + (BTN_WIDTH + BTN_SPACING) * 2 + BTN_WIDTH / 2, yOff + MARGIN + 9 + MARGIN + BTN_HEIGHT + MARGIN).size(BTN_WIDTH, BTN_HEIGHT).build());
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int xOff = (width - PANEL_WIDTH) / 2;
        int yOff = (height - PANEL_HEIGHT) / 2;
        guiGraphics.fill(xOff + MARGIN, yOff + MARGIN, xOff + PANEL_WIDTH + MARGIN, yOff + PANEL_HEIGHT + MARGIN, 0x66000000);
        guiGraphics.fill(xOff, yOff, xOff + PANEL_WIDTH, yOff + PANEL_HEIGHT, 0xFF313031);
        guiGraphics.fill(xOff, yOff + PANEL_HEIGHT - BTN_HEIGHT, xOff + PANEL_WIDTH, yOff + PANEL_HEIGHT, 0x66546E7A);
        guiGraphics.drawCenteredString(font, title, width / 2, yOff + MARGIN, 0xFFFFFFFF);
        if (shout) {
            guiGraphics.drawString(font, Component.translatable("gui.minopp.play.cursor.shout"), xOff + MARGIN, yOff + PANEL_HEIGHT - MARGIN - 9, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
