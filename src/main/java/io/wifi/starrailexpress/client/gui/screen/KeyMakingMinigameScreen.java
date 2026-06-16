package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * 任务点配钥匙小游戏
 * 参考锁匠的配钥匙游戏，仅保留游戏内容
 * 判定成功所需次数固定为5，失败后无惩罚
 */
public class KeyMakingMinigameScreen extends Screen {

    private static final int REQUIRED_HITS = 5;
    private static final float ZONE_HALF_WIDTH = 0.14f;
    private static final float MARKER_SPEED = 0.045f;

    private final Runnable onSuccess;
    private final Random rng = new Random();

    private int hitCount = 0;
    private float markerPos = 0f;
    private float markerVelocity = MARKER_SPEED;
    private float targetCenter = 0.5f;

    public KeyMakingMinigameScreen(BlockPos questPos, Runnable onSuccess) {
        super(Component.translatable("screen.starrailexpress.key_making_minigame"));
        this.onSuccess = onSuccess;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
        hitCount = 0;
        markerPos = 0f;
        markerVelocity = MARKER_SPEED;
        randomizeTarget();
    }

    @Override
    public void tick() {
        super.tick();
        markerPos += markerVelocity;
        if (markerPos >= 1f) {
            markerPos = 1f;
            markerVelocity = -Math.abs(markerVelocity);
        } else if (markerPos <= 0f) {
            markerPos = 0f;
            markerVelocity = Math.abs(markerVelocity);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            float distance = Math.abs(markerPos - targetCenter);
            if (distance <= ZONE_HALF_WIDTH) {
                hitCount++;
                if (hitCount >= REQUIRED_HITS) {
                    onSuccess.run();
                    onClose();
                    return true;
                }
            }
            randomizeTarget();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void randomizeTarget() {
        targetCenter = 0.15f + rng.nextFloat() * 0.70f;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        guiGraphics.fill(0, 0, this.width, this.height, 0xFF10131A);
        int panelLeft = centerX - 160;
        int panelRight = centerX + 160;
        int panelTop = centerY - 80;
        int panelBottom = centerY + 80;
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE263246);
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelTop + 18, 0xFF3E5D83);

        guiGraphics.drawCenteredString(this.font, this.title, centerX, panelTop + 5, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.key_making_progress", hitCount, REQUIRED_HITS),
                centerX, centerY - 36, 0xFFFFFF);

        int barWidth = 220;
        int barHeight = 12;
        int barX = centerX - barWidth / 2;
        int barY = centerY - 4;

        guiGraphics.fill(barX - 2, barY - 2, barX + barWidth + 2, barY + barHeight + 2, 0xFF5A6B82);
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF162130);

        int zoneStart = barX + Math.round((targetCenter - ZONE_HALF_WIDTH) * barWidth);
        int zoneEnd = barX + Math.round((targetCenter + ZONE_HALF_WIDTH) * barWidth);
        guiGraphics.fill(zoneStart, barY, zoneEnd, barY + barHeight, 0xCC4ACB73);

        int markerX = barX + Math.round(markerPos * barWidth);
        guiGraphics.fill(markerX - 1, barY - 6, markerX + 1, barY + barHeight + 6, 0xFFFF6B6B);
        guiGraphics.fill(markerX - 4, barY - 8, markerX + 4, barY - 6, 0xFFFF6B6B);

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.key_making_hint"),
                centerX, centerY + 32, 0xE3E7EE);
    }
}
