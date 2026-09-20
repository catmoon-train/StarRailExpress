package org.agmas.noellesroles.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.agmas.noellesroles.packet.PurpleMonsterEventC2SPacket;

import java.util.List;
import java.util.UUID;

/** Dedicated, paged player selector. It intentionally does not render the event PNG. */
@Environment(EnvType.CLIENT)
public final class PurpleMonsterPlayerSelectScreen extends Screen {
    private static final int COLUMNS = 4;
    private static final int ROWS = 2;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int MAX_PANEL_WIDTH = 440;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int PANEL_MARGIN = 20;
    private static final int HEADER_HEIGHT = 42;
    private static final int FOOTER_HEIGHT = 34;
    private static final int SLOT_GAP = 5;
    private static final int PANEL_BACKGROUND = 0xF0181420;
    private static final int PANEL_BORDER = 0xFF8C57A8;
    private static final int SLOT_BACKGROUND = 0xE02A2034;
    private static final int SLOT_HOVER = 0xFF624070;
    private static final int BUTTON_BACKGROUND = 0xFF3A2948;
    private static final int BUTTON_DISABLED = 0xFF211A28;

    private final UUID eventId;
    private final List<UUID> candidates;
    private int page;

    public PurpleMonsterPlayerSelectScreen(UUID eventId, List<UUID> candidates) {
        super(Component.translatable("screen.noellesroles.purple_monster.select.title"));
        this.eventId = eventId;
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
        drawPanel(graphics, left, top, panelWidth, panelHeight);

        graphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.purple_monster.select.title"),
                left + panelWidth / 2, top + 14, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.purple_monster.select.page", page + 1, pageCount()),
                left + panelWidth / 2, top + 29, 0xFFBFA9C9);

        int slotWidth = (panelWidth - PANEL_MARGIN * 2 - SLOT_GAP * (COLUMNS - 1)) / COLUMNS;
        int slotHeight = (panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT - PANEL_MARGIN * 2
                - SLOT_GAP * (ROWS - 1)) / ROWS;
        int gridLeft = left + PANEL_MARGIN;
        int gridTop = top + HEADER_HEIGHT;
        int first = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = first + slot;
            if (index >= candidates.size()) break;
            int column = slot % COLUMNS;
            int row = slot / COLUMNS;
            int x = gridLeft + column * (slotWidth + SLOT_GAP);
            int y = gridTop + row * (slotHeight + SLOT_GAP);
            boolean hovered = mouseX >= x && mouseX < x + slotWidth
                    && mouseY >= y && mouseY < y + slotHeight;
            graphics.fill(x, y, x + slotWidth, y + slotHeight,
                    hovered ? SLOT_HOVER : SLOT_BACKGROUND);

            PlayerInfo info = playerInfo(candidates.get(index));
            if (info != null) {
                int faceSize = Math.min(32, Math.max(20, slotHeight - 20));
                PlayerFaceRenderer.draw(graphics, info.getSkin(), x + 7, y + (slotHeight - faceSize) / 2,
                        faceSize);
            }
            String name = info == null ? candidates.get(index).toString().substring(0, 8)
                    : info.getProfile().getName();
            int textX = x + 46;
            int maxTextWidth = Math.max(20, slotWidth - 53);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(name, maxTextWidth), textX,
                    y + slotHeight / 2 - this.font.lineHeight / 2, 0xFFFFFFFF);
        }

        int buttonY = top + panelHeight - FOOTER_HEIGHT + 5;
        drawButton(graphics, left + PANEL_MARGIN, buttonY, 72, 22,
                Component.translatable("screen.noellesroles.purple_monster.select.previous"),
                page > 0, mouseX, mouseY);
        drawButton(graphics, left + panelWidth - PANEL_MARGIN - 72, buttonY, 72, 22,
                Component.translatable("screen.noellesroles.purple_monster.select.next"),
                page + 1 < pageCount(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;

        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int buttonY = top + panelHeight - FOOTER_HEIGHT + 5;
        if (inside(mouseX, mouseY, left + PANEL_MARGIN, buttonY, 72, 22)) {
            if (page > 0) {
                playButtonClick();
                page--;
            }
            return true;
        }
        if (inside(mouseX, mouseY, left + panelWidth - PANEL_MARGIN - 72, buttonY, 72, 22)) {
            if (page + 1 < pageCount()) {
                playButtonClick();
                page++;
            }
            return true;
        }

        int slotWidth = (panelWidth - PANEL_MARGIN * 2 - SLOT_GAP * (COLUMNS - 1)) / COLUMNS;
        int slotHeight = (panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT - PANEL_MARGIN * 2
                - SLOT_GAP * (ROWS - 1)) / ROWS;
        int gridLeft = left + PANEL_MARGIN;
        int gridTop = top + HEADER_HEIGHT;
        if (mouseX < gridLeft || mouseY < gridTop) return true;
        int column = (int) ((mouseX - gridLeft) / (slotWidth + SLOT_GAP));
        int row = (int) ((mouseY - gridTop) / (slotHeight + SLOT_GAP));
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) return true;
        int localX = (int) (mouseX - gridLeft) % (slotWidth + SLOT_GAP);
        int localY = (int) (mouseY - gridTop) % (slotHeight + SLOT_GAP);
        if (localX >= slotWidth || localY >= slotHeight) return true;
        int index = page * PAGE_SIZE + row * COLUMNS + column;
        if (index >= 0 && index < candidates.size()) {
            playButtonClick();
            select(candidates.get(index));
        }
        return true;
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left - 2, top - 2, left + width + 2, top + height + 2, 0x99000000);
        graphics.fill(left, top, left + width, top + height, PANEL_BORDER);
        graphics.fill(left + 2, top + 2, left + width - 2, top + height - 2, PANEL_BACKGROUND);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int width, int height, Component label,
                            boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, enabled && hovered ? SLOT_HOVER
                : enabled ? BUTTON_BACKGROUND : BUTTON_DISABLED);
        graphics.drawCenteredString(this.font, label, x + width / 2,
                y + (height - this.font.lineHeight) / 2, enabled ? 0xFFFFFFFF : 0xFF777077);
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

    private void playButtonClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private int pageCount() { return Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE); }

    private int panelWidth() { return Math.max(1, Math.min(MAX_PANEL_WIDTH, this.width - 20)); }

    private int panelHeight() { return Math.max(1, Math.min(MAX_PANEL_HEIGHT, this.height - 20)); }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
