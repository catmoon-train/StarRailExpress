package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.utils.MathProblemsManager;
import org.agmas.noellesroles.utils.MathProblemsManager.MathProblem;
import org.lwjgl.glfw.GLFW;

/**
 * 任务点数学题小游戏
 * 与习题集一致，算对5道题，算错跳过，错误超5次失败可重试
 */
public class MathMinigameScreen extends Screen {

    private static final int REQUIRED_CORRECT = 5;
    private static final int MAX_WRONG = 5;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;

    private final Runnable onSuccess;
    private final MathProblemsManager manager = new MathProblemsManager();

    private int correctCount = 0;
    private int wrongCount = 0;
    private boolean failed = false;
    private MathProblem currentProblem;

    public MathMinigameScreen(BlockPos questPos, Runnable onSuccess) {
        super(Component.translatable("screen.math_solver.title"));
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        super.init();
        correctCount = 0;
        wrongCount = 0;
        failed = false;
        nextProblem();
    }

    private void restart() {
        correctCount = 0;
        wrongCount = 0;
        failed = false;
        nextProblem();
    }

    private void nextProblem() {
        if (correctCount >= REQUIRED_CORRECT) {
            onSuccess.run();
            onClose();
            return;
        }
        currentProblem = manager.generateProblem(2);
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        if (currentProblem == null || failed) return;

        int buttonX = this.width / 2;
        int buttonY = this.height / 2 - 20;
        var opts = currentProblem.getOptions();

        for (int i = 0; i < opts.size(); i++) {
            final int idx = i + 1;
            Button btn = Button.builder(Component.literal(opts.get(i)), b -> onSelect(idx))
                    .pos(buttonX + (i % 2 == 1 ? 10 : -BUTTON_WIDTH - 10),
                            buttonY + (BUTTON_HEIGHT + 5) * (i / 2))
                    .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build();
            addRenderableWidget(btn);
        }
    }

    private void onSelect(int selectionIndex) {
        if (currentProblem == null || failed) return;
        if (currentProblem.getCorrectIndex() + 1 == selectionIndex) {
            correctCount++;
        } else {
            wrongCount++;
            if (wrongCount >= MAX_WRONG) {
                failed = true;
                showFailed();
                return;
            }
        }
        nextProblem();
    }

    private void showFailed() {
        clearWidgets();
        int cx = this.width / 2;
        int cy = this.height / 2;

        Button retryBtn = Button.builder(
                Component.translatable("screen.math_solver.try_again"),
                b -> restart())
                .pos(cx - BUTTON_WIDTH / 2, cy - BUTTON_HEIGHT - 5)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        addRenderableWidget(retryBtn);

        Button closeBtn = Button.builder(
                Component.translatable("screen.math_solver.failed_close"),
                b -> onClose())
                .pos(cx - BUTTON_WIDTH / 2, cy + 5)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        addRenderableWidget(closeBtn);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 居中圆角卡片作为题面背板
        int cardW = 260;
        int cardH = 150;
        int cardX = this.width / 2 - cardW / 2;
        int cardY = this.height / 2 - cardH / 2 - 10;
        MinigameUI.panel(guiGraphics, cardX, cardY, cardX + cardW, cardY + cardH, 0);

        if (failed) {
            Component failText = Component.translatable("screen.math_solver.failed")
                    .withStyle(ChatFormatting.RED);
            guiGraphics.drawCenteredString(this.font, failText, this.width / 2, this.height / 2 - 40, 0x888888);
        } else {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.math_progress", correctCount, REQUIRED_CORRECT),
                    this.width / 2, 30, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.starrailexpress.math_wrong", wrongCount, MAX_WRONG),
                    this.width / 2, 42, 0xFF8888);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (!failed && currentProblem != null) {
            guiGraphics.drawCenteredString(this.font,
                    Component.literal(currentProblem.getQuestion()),
                    this.width / 2, this.height / 2 - 60, 0xFFD700);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 透明背景
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (failed) return super.keyPressed(keyCode, scanCode, modifiers);
        return switch (keyCode) {
            case GLFW.GLFW_KEY_1 -> { onSelect(1); yield true; }
            case GLFW.GLFW_KEY_2 -> { onSelect(2); yield true; }
            case GLFW.GLFW_KEY_3 -> { onSelect(3); yield true; }
            case GLFW.GLFW_KEY_4 -> { onSelect(4); yield true; }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
