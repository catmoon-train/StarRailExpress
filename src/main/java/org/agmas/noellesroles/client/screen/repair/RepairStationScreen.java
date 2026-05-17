package org.agmas.noellesroles.client.screen.repair;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.content.block_entity.RepairStationBlockEntity;
import org.agmas.noellesroles.packet.RepairStationActionC2SPacket;

import java.util.Random;

public class RepairStationScreen extends Screen {
    private static final int BAR_W = 180;
    private static final int GREAT_W = 28;
    private static final int ACCIDENT_W = 22;
    private static final float ACCIDENT_CHANCE = 0.12F;

    private final BlockPos blockPos;
    private final Random random = new Random();
    private int ticks;
    private int greatZoneStart;
    private int accidentZoneStart;
    private boolean accidentMode;
    private Button actionButton;

    public RepairStationScreen(BlockPos blockPos) {
        super(Component.translatable("screen.noellesroles.repair_station.title"));
        this.blockPos = blockPos;
        rerollGreatZone();
    }

    @Override
    public void onClose() {
        // 如果处于事故模式且玩家直接关闭GUI，判定为失败
        if (accidentMode && minecraft != null && minecraft.player != null) {
            ClientPlayNetworking.send(new RepairStationActionC2SPacket(blockPos, false, true, false));
        }
        super.onClose();
    }

    @Override
    protected void init() {
        actionButton = addRenderableWidget(Button.builder(buttonText(), button -> handleAction())
                .bounds(width / 2 - 64, height / 2 + 42, 128, 20).build());
    }

    @Override
    public void tick() {
        ticks++;
        if (actionButton != null) {
            actionButton.setMessage(buttonText());
            actionButton.active = getBlockedSeconds() <= 0;
        }
        super.tick();
    }

    private void handleAction() {
        if (getBlockedSeconds() > 0) {
            return;
        }
        if (accidentMode) {
            boolean success = isAccidentMarkerInSafeZone();
            ClientPlayNetworking.send(new RepairStationActionC2SPacket(blockPos, success, true, success));
            accidentMode = false;
            if (success) {
                rerollGreatZone();
            } else if (minecraft != null) {
                minecraft.setScreen(null);
            }
            return;
        }
        boolean great = isMarkerInGreatZone();
        if (random.nextFloat() < ACCIDENT_CHANCE) {
            startAccidentMode();
            return;
        }
        ClientPlayNetworking.send(new RepairStationActionC2SPacket(blockPos, great, false, false));
        if (great) {
            rerollGreatZone();
        }
    }


    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        int panelX = width / 2 - 118;
        int panelY = height / 2 - 70;
        graphics.fill(panelX, panelY, panelX + 236, panelY + 128, 0xD0101018);
        graphics.fill(panelX + 4, panelY + 4, panelX + 232, panelY + 124, 0x90212A35);
        graphics.drawCenteredString(font, accidentMode
                ? Component.translatable("screen.noellesroles.repair_station.accident_title")
                : title, width / 2, panelY + 12, accidentMode ? 0xFFFFB36B : 0xFFE8F7FF);

        int progress = getProgress();
        int barX = width / 2 - 90;
        int barY = height / 2 - 24;
        graphics.fill(barX, barY, barX + 180, barY + 16, 0xFF111111);
        graphics.fill(barX + 2, barY + 2, barX + 2 + (int) (176 * (progress / 100.0F)), barY + 14, 0xFF48D17A);
        graphics.drawCenteredString(font, Component.literal(progress + "%"), width / 2, barY + 4, 0xFFFFFFFF);

        if (accidentMode) {
            renderAccidentGame(graphics, barX, height / 2 + 8);
        } else {
            renderNormalCalibration(graphics, barX, height / 2 + 10);
        }

        float pulse = 0.6F + 0.4F * Mth.sin((ticks + delta) * 0.25F);
        int glow = ((int) (pulse * 120.0F) << 24) | 0x00FFAA33;
        graphics.fill(panelX + 12, panelY + 90, panelX + 224, panelY + 92, glow);
        Component help = getBlockedSeconds() > 0
                ? Component.translatable("screen.noellesroles.repair_station.blocked", getBlockedSeconds())
                : accidentMode
                        ? Component.translatable("screen.noellesroles.repair_station.accident_help")
                        : Component.translatable("screen.noellesroles.repair_station.help").withStyle(ChatFormatting.GRAY);
        graphics.drawCenteredString(font, help, width / 2, panelY + 100,
                getBlockedSeconds() > 0 ? 0xFFFF7043 : 0xFFB0B8C0);

    }

    private void renderNormalCalibration(GuiGraphics graphics, int barX, int skillY) {
        graphics.fill(barX, skillY, barX + BAR_W, skillY + 12, 0xFF090909);
        int greatStart = barX + greatZoneStart;
        int greatEnd = greatStart + GREAT_W;
        graphics.fill(greatStart, skillY + 2, greatEnd, skillY + 10, 0xFF2BD66B);
        int markerX = barX + getMarkerOffset();
        int markerColor = isMarkerInGreatZone() ? 0xFFFFF176 : 0xFFFF7043;
        graphics.fill(markerX - 2, skillY - 4, markerX + 2, skillY + 16, markerColor);
    }

    private void renderAccidentGame(GuiGraphics graphics, int barX, int skillY) {
        graphics.fill(barX, skillY, barX + BAR_W, skillY + 18, 0xFF180508);
        graphics.fill(barX + accidentZoneStart, skillY + 3, barX + accidentZoneStart + ACCIDENT_W, skillY + 15,
                0xFFFFC857);
        int markerX = barX + getAccidentMarkerOffset();
        int markerColor = isAccidentMarkerInSafeZone() ? 0xFFB9F6CA : 0xFFFF5252;
        graphics.fill(markerX - 2, skillY - 5, markerX + 2, skillY + 23, markerColor);
        graphics.drawCenteredString(font, Component.translatable("screen.noellesroles.repair_station.accident_warning"),
                width / 2, skillY - 14, 0xFFFF8A80);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {

    }

    private int getProgress() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.getBlockEntity(blockPos) instanceof RepairStationBlockEntity station) {
            return station.getProgressPercent();
        }
        return 0;
    }

    private int getBlockedSeconds() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.getBlockEntity(blockPos) instanceof RepairStationBlockEntity station) {
            return station.getRepairBlockedTicks() <= 0 ? 0 : Math.max(1, (station.getRepairBlockedTicks() + 19) / 20);
        }
        return 0;
    }

    private int getMarkerOffset() {
        return (int) (90 + Mth.sin(ticks * 0.18F) * 86);
    }

    private boolean isMarkerInGreatZone() {
        int offset = getMarkerOffset();
        return offset >= greatZoneStart && offset <= greatZoneStart + GREAT_W;
    }

    private int getAccidentMarkerOffset() {
        return (int) (90 + Mth.sin(ticks * 0.32F) * 86);
    }

    private boolean isAccidentMarkerInSafeZone() {
        int offset = getAccidentMarkerOffset();
        return offset >= accidentZoneStart && offset <= accidentZoneStart + ACCIDENT_W;
    }

    private void rerollGreatZone() {
        greatZoneStart = 12 + random.nextInt(BAR_W - GREAT_W - 24);
    }

    private void startAccidentMode() {
        accidentMode = true;
        accidentZoneStart = 12 + random.nextInt(BAR_W - ACCIDENT_W - 24);
        ticks = 0;
    }

    private Component buttonText() {
        if (accidentMode) {
            return Component.translatable("screen.noellesroles.repair_station.stabilize");
        }
        return Component.translatable("screen.noellesroles.repair_station.repair");
    }
}
