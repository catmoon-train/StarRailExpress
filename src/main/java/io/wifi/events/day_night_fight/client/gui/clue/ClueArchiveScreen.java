package io.wifi.events.day_night_fight.client.gui.clue;

import io.wifi.events.day_night_fight.cca.SREPlayerClueComponent;
import io.wifi.events.day_night_fight.clue.ClueSystem;
import io.wifi.starrailexpress.network.SendClueBookPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClueArchiveScreen extends Screen {
    private final Set<UUID> selected = new HashSet<>();
    private long openTime;
    private int scroll;
    private Button sendButton;

    public ClueArchiveScreen() {
        super(Component.translatable("screen.sre.clue_archive.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = Util.getMillis();
        this.sendButton = Button.builder(Component.translatable("screen.sre.clue_archive.send"),
                        button -> sendSelected())
                .bounds(this.width / 2 + 118, this.height - 54, 96, 20)
                .build();
        this.addRenderableWidget(this.sendButton);
        updateButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        SREPlayerClueComponent data = getData();
        float t = Mth.clamp((Util.getMillis() - openTime) / 300.0f, 0f, 1f);
        int panelW = Math.min(420, (int) (this.width * 0.82f));
        int panelH = Math.min(250, (int) (this.height * 0.78f));
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int alpha = (int) (230 * t);

        graphics.fill(x, y, x + panelW, y + panelH, (alpha << 24) | 0x0F1724);
        graphics.fill(x, y, x + panelW, y + 34, (alpha << 24) | 0x16253A);
        graphics.drawString(this.font, this.title, x + 16, y + 13, 0xDDF3FF, false);
        Component countText = Component.translatable("screen.sre.clue_archive.count",
                data.clues.size(), selected.size(), ClueSystem.maxSelectable());
        graphics.drawString(this.font, countText, x + panelW - 16 - this.font.width(countText), y + 13,
                0x9EC8DC, false);
        graphics.drawString(this.font, Component.translatable("screen.sre.clue_archive.remaining", data.sendTimesLeft),
                x + 16, y + panelH - 39, data.sendTimesLeft > 0 ? 0x8FFFD2 : 0xFF9D93, false);

        int listTop = y + 44;
        int rowH = 38;
        int maxRows = Math.max(1, (panelH - 96) / rowH);
        int maxScroll = Math.max(0, data.clues.size() - maxRows);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        for (int i = 0; i < maxRows; i++) {
            int index = i + scroll;
            if (index >= data.clues.size()) {
                break;
            }
            var clue = data.clues.get(index);
            boolean sent = data.sentClues.contains(clue.clueEntityUuid());
            boolean isSelected = selected.contains(clue.clueEntityUuid());
            int rowY = listTop + i * rowH;
            int bg = sent ? 0x5520272F : isSelected ? 0xAA24486B : 0x77182736;
            graphics.fill(x + 14, rowY, x + panelW - 14, rowY + rowH - 5, bg);
            graphics.drawString(this.font, clue.title(), x + 26, rowY + 7,
                    sent ? 0x778899 : 0xEAF7FF, false);
            String preview = this.font.plainSubstrByWidth(clue.content(), panelW - 92);
            graphics.drawString(this.font, preview, x + 26, rowY + 21,
                    sent ? 0x66717D : 0x9DB9CC, false);
            graphics.drawString(this.font, sent
                    ? Component.translatable("screen.sre.clue_archive.sent")
                    : Component.literal(isSelected ? "ON" : "OFF"), x + panelW - 54, rowY + 13,
                    sent ? 0x778899 : isSelected ? 0x8FFFD2 : 0x7894A8, false);
        }

        graphics.drawString(this.font, Component.translatable("screen.sre.clue_archive.hint"),
                x + 16, y + panelH - 24, 0x86A9BA, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && toggleAt(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) Math.signum(verticalAmount));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean toggleAt(double mouseX, double mouseY) {
        SREPlayerClueComponent data = getData();
        int panelW = Math.min(420, (int) (this.width * 0.82f));
        int panelH = Math.min(250, (int) (this.height * 0.78f));
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int rowH = 38;
        int listTop = y + 44;
        int maxRows = Math.max(1, (panelH - 96) / rowH);
        if (mouseX < x + 14 || mouseX > x + panelW - 14 || mouseY < listTop) {
            return false;
        }
        int row = ((int) mouseY - listTop) / rowH;
        if (row < 0 || row >= maxRows) {
            return false;
        }
        int index = row + scroll;
        if (index < 0 || index >= data.clues.size()) {
            return false;
        }
        var clue = data.clues.get(index);
        if (data.sentClues.contains(clue.clueEntityUuid())) {
            return true;
        }
        if (!selected.remove(clue.clueEntityUuid()) && selected.size() < ClueSystem.maxSelectable()) {
            selected.add(clue.clueEntityUuid());
        }
        updateButton();
        return true;
    }

    private void sendSelected() {
        if (selected.isEmpty()) {
            return;
        }
        String csv = selected.stream().map(UUID::toString).collect(Collectors.joining(","));
        ClientPlayNetworking.send(new SendClueBookPayload(csv));
        selected.clear();
        updateButton();
    }

    private void updateButton() {
        if (sendButton != null) {
            sendButton.active = !selected.isEmpty()
                    && selected.size() <= ClueSystem.maxSelectable()
                    && getData().sendTimesLeft > 0;
        }
    }

    private static SREPlayerClueComponent getData() {
        Minecraft client = Minecraft.getInstance();
        return SREPlayerClueComponent.KEY.get(client.player);
    }
}
