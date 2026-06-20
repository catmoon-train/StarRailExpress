package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * 任务点判定
 * 参考锁匠的配钥匙游戏，仅保留游戏内容
 * 判定成功所需次数固定为5，失败后无惩罚
 */
public class KeyMakingMinigameScreen extends Screen {

    private static final int REQUIRED_HITS = 5;
    private static final float ZONE_HALF_WIDTH = 0.14f;
    private static final float MARKER_SPEED = 0.045f;

    private final Runnable onSuccess;
    private final Random rng = new Random();

    private static final int INTRO_TICKS = 7;

    private int hitCount = 0;
    private float markerPos = 0f;
    private float markerVelocity = MARKER_SPEED;
    private float targetCenter = 0.5f;
    private int uiTicks = 0;
    private int introTicks = 0;
    private int flashTicks = 0;

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
        introTicks = 0;
        flashTicks = 0;
        randomizeTarget();
    }

    @Override
    public void tick() {
        super.tick();
        uiTicks++;
        if (introTicks < INTRO_TICKS) introTicks++;
        if (flashTicks > 0) flashTicks--;
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
                flashTicks = 6;
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
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 透明背景
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelLeft = centerX - 165;
        int panelRight = centerX + 165;
        int panelTop = centerY - 85;
        int panelBottom = centerY + 85;

        // 入场弹出动画
        float intro = MinigameUI.easeOut((introTicks + partialTick) / INTRO_TICKS);
        float scale = 0.82f + 0.18f * intro;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0);
        guiGraphics.pose().scale(scale, scale, 1f);
        guiGraphics.pose().translate(-centerX, -centerY, 0);

        MinigameUI.panel(guiGraphics, panelLeft, panelTop, panelRight, panelBottom, 22);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, panelTop + 7, MinigameUI.WHITE);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.key_making_progress", hitCount, REQUIRED_HITS),
                centerX, centerY - 36, MinigameUI.WHITE);

        int barWidth = 230;
        int barHeight = 12;
        int barX = centerX - barWidth / 2;
        int barY = centerY - 4;

        MinigameUI.roundRect(guiGraphics, barX - 2, barY - 2, barX + barWidth + 2, barY + barHeight + 2, 8, 0xFF3A4A60);
        MinigameUI.roundRect(guiGraphics, barX, barY, barX + barWidth, barY + barHeight, 6, MinigameUI.PANEL_DARK);

        // 命中区：成功瞬间高亮闪一下，平时轻微呼吸
        float zoneGlow = flashTicks > 0 ? 1f : 0.6f + 0.25f * MinigameUI.pulse(uiTicks, 0.2f);
        int zoneStart = barX + Math.round((targetCenter - ZONE_HALF_WIDTH) * barWidth);
        int zoneEnd = barX + Math.round((targetCenter + ZONE_HALF_WIDTH) * barWidth);
        MinigameUI.roundRect(guiGraphics, zoneStart, barY, zoneEnd, barY + barHeight, 6, MinigameUI.withAlpha(MinigameUI.GREEN, zoneGlow));

        // 指针 + 发光头
        int markerX = barX + Math.round(markerPos * barWidth);
        MinigameUI.filledCircle(guiGraphics, markerX, barY - 9, 4, MinigameUI.withAlpha(MinigameUI.RED, 0.35f));
        guiGraphics.fill(markerX - 1, barY - 7, markerX + 1, barY + barHeight + 6, MinigameUI.RED);
        MinigameUI.filledCircle(guiGraphics, markerX, barY - 8, 3, MinigameUI.RED);

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.key_making_hint"),
                centerX, centerY + 34, MinigameUI.MUTED);
        guiGraphics.pose().popPose();

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
