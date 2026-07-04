package cn.zbx1425.minopp.gui;

import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemHandCards;
import cn.zbx1425.minopp.network.C2SSeatControlPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public class SeatControlScreen extends Screen {

    private final BlockPos gamePos;

    public SeatControlScreen(BlockPos gamePos) {
        super(Component.translatable("gui.minopp.seats.title"));
        this.gamePos = gamePos;
    }

    int BTN_WIDTH = 60;
    int BTN_HEIGHT = 20;
    int MARGIN = 8;

    int PANEL_HEIGHT = MARGIN + 9 + MARGIN
            + MARGIN + 9 + MARGIN + BTN_HEIGHT + MARGIN + 9 + MARGIN
            + MARGIN + BTN_HEIGHT + MARGIN;
    int PANEL_WIDTH = 260;

    private Button stopButton, startButton, leaveButton;

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        int xOff = (width - PANEL_WIDTH) / 2;
        int yOff = (height - PANEL_HEIGHT) / 2;
        stopButton = Button.builder(Component.translatable("gui.minopp.seats.stop"), e -> {
            C2SSeatControlPacket.Client.sendGameEnableC2S(gamePos, false);
            onClose();
        }).pos(xOff + MARGIN, yOff + PANEL_HEIGHT - MARGIN - BTN_HEIGHT).size(BTN_WIDTH, BTN_HEIGHT).build();
        stopButton.active = false;
        addRenderableWidget(stopButton);
        startButton = Button.builder(Component.translatable("gui.minopp.seats.start"), e -> {
            C2SSeatControlPacket.Client.sendGameEnableC2S(gamePos, true);
            onClose();
        }).pos(xOff + MARGIN + BTN_WIDTH + MARGIN, yOff + PANEL_HEIGHT - MARGIN - BTN_HEIGHT).size(BTN_WIDTH, BTN_HEIGHT).build();
        startButton.active = false;
        addRenderableWidget(startButton);
        leaveButton = Button.builder(Component.translatable("gui.minopp.seats.reset"), e -> {
            C2SSeatControlPacket.Client.sendResetSeatsC2S(gamePos);
            onClose();
        }).pos(xOff + PANEL_WIDTH - MARGIN - BTN_WIDTH, yOff + PANEL_HEIGHT - BTN_HEIGHT - MARGIN - BTN_HEIGHT).size(BTN_WIDTH, BTN_HEIGHT).build();
        leaveButton.active = false;
        addRenderableWidget(leaveButton);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        if (minecraft.level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
            startButton.active = tableEntity.game == null && tableEntity.getPlayersList().size() >= 2;
            stopButton.active = tableEntity.game != null;
            CardPlayer cardPlayer = ItemHandCards.getCardPlayer(minecraft.player);
            leaveButton.active = tableEntity.game == null && tableEntity.getPlayersList().contains(cardPlayer);

            int xOff = (width - PANEL_WIDTH) / 2;
            int yOff = (height - PANEL_HEIGHT) / 2;
            guiGraphics.fill(xOff + MARGIN, yOff + MARGIN, xOff + PANEL_WIDTH + MARGIN, yOff + PANEL_HEIGHT + MARGIN, 0x99000000);
            guiGraphics.fill(xOff, yOff, xOff + PANEL_WIDTH, yOff + PANEL_HEIGHT, 0xFF313031);
            guiGraphics.fill(xOff, yOff + PANEL_HEIGHT - BTN_HEIGHT, xOff + PANEL_WIDTH, yOff + PANEL_HEIGHT, 0x66546E7A);
            guiGraphics.drawCenteredString(font, title, width / 2, yOff + MARGIN, 0xFFFFFFFF);

            guiGraphics.fill(width / 2 - BTN_HEIGHT / 2, yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN,
                    width / 2 + BTN_HEIGHT / 2, yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + BTN_HEIGHT, 0xFF00492e);

            guiGraphics.drawCenteredString(font, getPlayerName(tableEntity, Direction.NORTH),
                    width / 2, yOff + MARGIN + 9 + MARGIN + MARGIN, 0xFFAAAAAA);
            guiGraphics.drawString(font, getPlayerName(tableEntity, Direction.WEST),
                    width / 2 - MARGIN - BTN_HEIGHT / 2 - font.width(getPlayerName(tableEntity, Direction.WEST)),
                    yOff + MARGIN + 9 + MARGIN  + MARGIN + 9 + MARGIN + BTN_HEIGHT / 2 - 9 / 2, 0xFFAAAAAA);
            guiGraphics.drawString(font, getPlayerName(tableEntity, Direction.EAST),
                    width / 2 + MARGIN + BTN_HEIGHT / 2,
                    yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + BTN_HEIGHT / 2 - 9 / 2, 0xFFAAAAAA);
            guiGraphics.drawCenteredString(font, getPlayerName(tableEntity, Direction.SOUTH),
                    width / 2, yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + BTN_HEIGHT + MARGIN, 0xFFAAAAAA);
        }
    }

    private static String getPlayerName(BlockEntityMinoTable tableEntity, Direction direction) {
        return tableEntity.players.get(direction) == null ? "-" : tableEntity.players.get(direction).name;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
