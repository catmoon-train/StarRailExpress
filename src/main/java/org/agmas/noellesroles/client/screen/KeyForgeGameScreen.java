package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.KeyForgeGameC2Packet;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class KeyForgeGameScreen extends Screen {
    private static final int MAX_DIFFICULTY = 6;
    private static final float[] ZONE_HALF_WIDTH = {0.28f, 0.22f, 0.18f, 0.14f, 0.11f, 0.09f};
    private static final float[] MARKER_SPEED = {0.030f, 0.038f, 0.045f, 0.053f, 0.060f, 0.068f};
    private static final int MAX_TOLERANCE_MISS = 5;

    private int selectedDifficulty = 1;
    private boolean playing = false;
    private int hitCount = 0;
    private int missCount = 0;
    private int currentInspiration;
    private float markerPos = 0f;
    private float markerVelocity = 0.03f;
    private float targetCenter = 0.5f;
    private final Random rng = new Random();

    public KeyForgeGameScreen(int currentInspiration) {
        super(Component.translatable("screen.noellesroles.key_forge.title"));
        this.currentInspiration = Math.max(0, currentInspiration);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        this.playing = false;
        this.hitCount = 0;
        this.missCount = 0;
        this.markerPos = 0f;
        this.markerVelocity = MARKER_SPEED[this.selectedDifficulty - 1];
        randomizeTarget();
    }

    private int getInspirationCost() {
        switch (this.selectedDifficulty) {
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 4;
            case 4:
                return 7;
            case 5:
                return 8;
            case 6:
                return 9;
            default:
                return 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.playing) {
            return;
        }
        this.markerPos += this.markerVelocity;
        if (this.markerPos >= 1f) {
            this.markerPos = 1f;
            this.markerVelocity = -Math.abs(this.markerVelocity);
        } else if (this.markerPos <= 0f) {
            this.markerPos = 0f;
            this.markerVelocity = Math.abs(this.markerVelocity);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.playing) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                this.selectedDifficulty = Math.max(1, this.selectedDifficulty - 1);
                this.markerVelocity = MARKER_SPEED[this.selectedDifficulty - 1];
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                this.selectedDifficulty = Math.min(MAX_DIFFICULTY, this.selectedDifficulty + 1);
                this.markerVelocity = MARKER_SPEED[this.selectedDifficulty - 1];
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                int required = getInspirationCost();
                if (this.currentInspiration < required) {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.displayClientMessage(
                                Component.translatable("message.noellesroles.locksmith.inspiration_insufficient",
                                        required, this.currentInspiration),
                                true);
                    }
                    return true;
                }
                this.playing = true;
                this.hitCount = 0;
                this.missCount = 0;
                this.markerPos = 0f;
                this.markerVelocity = MARKER_SPEED[this.selectedDifficulty - 1];
                randomizeTarget();
                return true;
            }
        } else if (keyCode == GLFW.GLFW_KEY_SPACE) {
            float distance = Math.abs(this.markerPos - this.targetCenter);
            if (distance <= ZONE_HALF_WIDTH[this.selectedDifficulty - 1]) {
                this.hitCount++;
                if (this.hitCount >= getInspirationCost()) {
                    submitResult(true);
                    return true;
                }
                randomizeTarget();
            } else {
                this.missCount++;
                if (this.missCount >= MAX_TOLERANCE_MISS) {
                    submitResult(false);
                    return true;
                }
                randomizeTarget();
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void submitResult(boolean success) {
        ClientPlayNetworking.send(new KeyForgeGameC2Packet(this.selectedDifficulty, success));
        this.onClose();
    }

    private void randomizeTarget() {
        this.targetCenter = 0.15f + this.rng.nextFloat() * 0.70f;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 使用纯色与面板背景，避免世界背景模糊导致文本难读。
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF10131A);
        guiGraphics.fill(10, 10, this.width - 10, this.height - 10, 0xDD1C2330);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - 160;
        int panelRight = centerX + 160;
        int panelTop = centerY - 92;
        int panelBottom = centerY + 88;
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE263246);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 18, 0xFF3E5D83);

        // 左右“钥匙槽”图形装饰
        guiGraphics.fill(panelLeft + 12, centerY - 20, panelLeft + 40, centerY + 20, 0xFF5D7BA3);
        guiGraphics.fill(panelRight - 40, centerY - 20, panelRight - 12, centerY + 20, 0xFF5D7BA3);
        guiGraphics.fill(panelLeft + 20, centerY - 12, panelLeft + 32, centerY + 12, 0xFFB8D7FF);
        guiGraphics.fill(panelRight - 32, centerY - 12, panelRight - 20, centerY + 12, 0xFFB8D7FF);

        guiGraphics.drawCenteredString(this.font, this.title, centerX, panelTop + 5, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.key_forge.difficulty", this.selectedDifficulty),
            centerX, centerY - 60, 0xD6EBFF);

        Component rewardText = Component.translatable("screen.noellesroles.key_forge.reward." + this.selectedDifficulty);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.key_forge.current_reward", rewardText),
            centerX, centerY - 46, 0xF9E79F);

        guiGraphics.drawCenteredString(this.font,
            Component.literal("所需灵感：" + getInspirationCost() + " | 当前灵感：" + this.currentInspiration),
            centerX, centerY - 34, 0xFFD787);

        if (!this.playing) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.key_forge.tips.select"),
                    centerX, centerY + 36, 0xE3E7EE);
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.noellesroles.key_forge.tips.start"),
                    centerX, centerY + 48, 0xE3E7EE);
            return;
        }

        int barWidth = 220;
        int barHeight = 12;
        int barX = centerX - barWidth / 2;
        int barY = centerY - 4;

        guiGraphics.fill(barX - 2, barY - 2, barX + barWidth + 2, barY + barHeight + 2, 0xFF5A6B82);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF162130);

        int zoneStart = barX + Math.round((this.targetCenter - ZONE_HALF_WIDTH[this.selectedDifficulty - 1]) * barWidth);
        int zoneEnd = barX + Math.round((this.targetCenter + ZONE_HALF_WIDTH[this.selectedDifficulty - 1]) * barWidth);
        guiGraphics.fill(zoneStart, barY, zoneEnd, barY + barHeight, 0xCC4ACB73);

        int markerX = barX + Math.round(this.markerPos * barWidth);
        guiGraphics.fill(markerX - 1, barY - 6, markerX + 1, barY + barHeight + 6, 0xFFFF6B6B);
        guiGraphics.fill(markerX - 4, barY - 8, markerX + 4, barY - 6, 0xFFFF6B6B);

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.key_forge.progress", this.hitCount,
                        getInspirationCost()),
            centerX, centerY + 24, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.literal("容错：" + this.missCount + "/" + MAX_TOLERANCE_MISS),
                centerX, centerY + 32, 0xFFBBD0FF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.key_forge.tips.hit"),
            centerX, centerY + 44, 0xE3E7EE);
    }
}
