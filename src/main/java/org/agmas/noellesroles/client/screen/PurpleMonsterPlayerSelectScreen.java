package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.PurpleMonsterEventC2SPacket;

import java.util.List;
import java.util.UUID;

/** PNG-backed player selection page with custom hit regions and no code-drawn buttons. */
public final class PurpleMonsterPlayerSelectScreen extends Screen {
    private static final int TEXTURE_SIZE = 1536;
    private static final int GUI_SIZE = 500;
    private static final int COLUMNS = 4;
    private static final net.minecraft.resources.ResourceLocation BACKGROUND = Noellesroles.id(
            "textures/gui/purple_monster_event_background.png");
    private final UUID eventId;
    private final List<UUID> candidates;

    public PurpleMonsterPlayerSelectScreen(UUID eventId, List<UUID> candidates) {
        super(Component.literal("选择要摧毁的玩家"));
        this.eventId = eventId;
        this.candidates = candidates;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        int size = guiSize();
        int left = guiLeft(size);
        int top = guiTop(size);
        graphics.blit(BACKGROUND, left, top, 0, 0, size, size, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.drawCenteredString(this.font, Component.literal("选择要摧毁的玩家"), left + size * 72 / 100,
                top + size * 21 / 100, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("点击玩家头像"), left + size / 2,
                top + size * 42 / 100, 0xFFFFFFFF);

        int cellWidth = size / COLUMNS;
        int cellHeight = Math.max(32, size / 12);
        int listTop = top + size * 47 / 100;
        for (int i = 0; i < candidates.size(); i++) {
            int x = left + (i % COLUMNS) * cellWidth;
            int y = listTop + (i / COLUMNS) * cellHeight;
            PlayerInfo info = playerInfo(candidates.get(i));
            if (info != null) PlayerFaceRenderer.draw(graphics, info.getSkin(), x + 4, y + 4, 24);
            String name = info == null ? candidates.get(i).toString().substring(0, 8)
                    : info.getProfile().getName();
            graphics.drawString(this.font, name, x + 32, y + 10, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        int size = guiSize();
        int left = guiLeft(size);
        int listTop = guiTop(size) + size * 47 / 100;
        int cellWidth = size / COLUMNS;
        int cellHeight = Math.max(32, size / 12);
        if (mouseX < left || mouseX >= left + size || mouseY < listTop) return true;
        int column = (int) ((mouseX - left) / cellWidth);
        int row = (int) ((mouseY - listTop) / cellHeight);
        if (column < 0 || column >= COLUMNS || row < 0) return true;
        int index = row * COLUMNS + column;
        if (index < candidates.size()) select(candidates.get(index));
        return true;
    }

    private PlayerInfo playerInfo(UUID id) {
        return this.minecraft == null || this.minecraft.getConnection() == null ? null
                : this.minecraft.getConnection().getPlayerInfo(id);
    }

    private void select(UUID id) {
        ClientPlayNetworking.send(new PurpleMonsterEventC2SPacket(eventId,
                PurpleMonsterEventC2SPacket.Action.SELECT, id));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    private int guiSize() {
        return Math.min(GUI_SIZE, Math.min(this.width - 20, this.height - 20));
    }

    private int guiLeft(int size) { return (this.width - size) / 2; }

    private int guiTop(int size) { return (this.height - size) / 2; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
