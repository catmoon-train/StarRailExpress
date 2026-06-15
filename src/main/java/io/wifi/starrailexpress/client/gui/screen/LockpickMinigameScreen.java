package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import org.agmas.noellesroles.client.animation.AbstractAnimation;
import org.agmas.noellesroles.client.animation.BezierAnimation;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

/**
 * 任务点撬锁小游戏（改进版）
 * 不消耗撬锁器，失败时不会退出页面，直到成功为止
 */
public class LockpickMinigameScreen extends Screen {

    private static class SimulatedLock {
        private final ArrayList<Integer> series;
        private final int length;
        private final float resistance;
        private final RandomSource random;

        SimulatedLock(int length, float resistance) {
            this.length = length;
            this.resistance = resistance;
            this.series = new ArrayList<>(length);
            this.random = RandomSource.create();
            resetSeries();
        }

        void resetSeries() {
            series.clear();
            for (int i = 0; i < length; ++i)
                series.add(-1);
            for (int i = 0, randomIdx = random.nextInt(length); i < length; ++i) {
                while (series.get(randomIdx) != -1)
                    randomIdx = random.nextInt(length);
                series.set(randomIdx, i);
            }
        }

        int getSeriesUnlockIdx(int idx) { return series.get(idx); }
        float getResistance() { return resistance; }
        RandomSource getRandom() { return random; }
    }

    private static class LockPickWidget extends TextureWidget {
        private int length;
        private int bodyWidth;
        private int pixelSize;
        private static final int textureWidth = 16;
        private static final int textureHeight = 2;

        LockPickWidget() {
            super(0, 0, 0, 0, 16, 2,
                    ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/lock_game_lock_pick.png"));
        }

        void initLockPick(int x, int y, int length, int bodyLength, int pixelSize) {
            this.setX(x);
            this.setY(y);
            this.length = length;
            this.bodyWidth = bodyLength;
            this.pixelSize = pixelSize;
            this.width = ((length - 1) * bodyLength + textureWidth) * pixelSize;
            this.height = textureHeight * pixelSize;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float f) {
            for (int i = 0, offsetX = 0, curWidth = bodyWidth; i < length; ++i, offsetX += bodyWidth * pixelSize) {
                if (i == length - 1) curWidth = textureWidth;
                guiGraphics.blit(TEXTURE,
                        this.getX() + offsetX, this.getY(),
                        curWidth * pixelSize, this.height,
                        0, 0,
                        curWidth, textureHeight,
                        textureWidth, textureHeight);
            }
        }

        int getLockPickWidth() { return textureWidth; }
    }

    private static final int LOCK_LENGTH = 6;
    private static final float RESISTANCE = 0.08f;
    private static int pixelSize = 8;
    private static final int lockWidth = 6;
    private static final int lockHeight = 16;
    private static final int lockCoreWidth = 2;
    private static final int lockCoreHeight = 11;
    private static final int lockInterval = 5;

    private final Runnable onSuccess;
    private final SimulatedLock simulatedLock;
    private final ArrayList<TextureWidget> lockCores = new ArrayList<>();
    private final Queue<AbstractAnimation> animations = new ArrayDeque<>();
    private LockPickWidget lockPick;
    private int curIdx = 0;
    private int unlockingIdx = 0;

    public LockpickMinigameScreen(BlockPos questPos, Runnable onSuccess) {
        super(Component.translatable("screen.starrailexpress.lockpick_minigame"));
        this.onSuccess = onSuccess;
        this.simulatedLock = new SimulatedLock(LOCK_LENGTH, RESISTANCE);
    }

    @Override
    protected void init() {
        super.init();
        lockCores.clear();
        animations.clear();
        curIdx = 0;
        unlockingIdx = 0;

        int totalPixels = LOCK_LENGTH * 5 + 1;
        while ((width < totalPixels * pixelSize || height < lockHeight * pixelSize) && pixelSize > 1)
            --pixelSize;

        int lockStartX = width / 2 - totalPixels * pixelSize / 2;
        int lockStartY = height / 2 - lockHeight * pixelSize / 2;
        int lockCoreStartX = lockStartX + 2 * pixelSize;
        int lockCoreStartY = lockStartY + 2 * pixelSize;

        lockPick = new LockPickWidget();
        int lockPickX = lockStartX - (lockPick.getLockPickWidth() + (LOCK_LENGTH - 1) * (lockWidth - 1) - 4) * pixelSize;
        int lockPickY = lockStartY + 13 * pixelSize;
        lockPick.initLockPick(lockPickX, lockPickY, LOCK_LENGTH, lockInterval, pixelSize);
        addRenderableWidget(lockPick);

        for (int i = 0, offsetX = 0; i < LOCK_LENGTH; ++i, offsetX += (lockWidth - 1) * pixelSize) {
            int curWidth = i == LOCK_LENGTH - 1 ? 6 : 5;
            TextureWidget lockBody = new TextureWidget(
                    lockStartX + offsetX, lockStartY,
                    curWidth * pixelSize, lockHeight * pixelSize,
                    curWidth, lockHeight,
                    lockWidth, lockHeight,
                    ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/lock_game_lock.png"));
            addRenderableWidget(lockBody);
            TextureWidget lockCore = new TextureWidget(
                    lockCoreStartX + offsetX, lockCoreStartY,
                    lockCoreWidth * pixelSize, lockCoreHeight * pixelSize,
                    lockCoreWidth, lockCoreHeight,
                    ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/lock_game_core.png"));
            addRenderableWidget(lockCore);
            lockCores.add(lockCore);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        animations.forEach(animation -> animation.renderUpdate(partialTick));
        animations.removeIf(AbstractAnimation::isFinished);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.lockpick_hint"),
                width / 2, height - pixelSize * 2, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int upMovement = 1;
        return switch (keyCode) {
            case GLFW.GLFW_KEY_W -> {
                boolean alreadyUnlocked = false;
                for (int i = 0; i < unlockingIdx; ++i) {
                    if (curIdx == simulatedLock.getSeriesUnlockIdx(i)) {
                        alreadyUnlocked = true;
                        break;
                    }
                }
                if (alreadyUnlocked) yield true;

                animations.add(new BezierAnimation(lockPick,
                        new Vec2(0, -upMovement * pixelSize), new Vec2(0, -upMovement * pixelSize),
                        new Vec2(0, 0), 5));

                if (curIdx == simulatedLock.getSeriesUnlockIdx(unlockingIdx)) {
                    animations.add(new BezierAnimation(lockCores.get(curIdx),
                            new Vec2(0, -upMovement * pixelSize), new Vec2(0, -upMovement * pixelSize),
                            new Vec2(0, -upMovement * pixelSize), 5));
                    ++unlockingIdx;
                    if (unlockingIdx == LOCK_LENGTH) {
                        onSuccess.run();
                        onClose();
                    }
                } else {
                    animations.add(new BezierAnimation(lockCores.get(curIdx),
                            new Vec2(0, -upMovement * pixelSize), new Vec2(0, -upMovement * pixelSize),
                            new Vec2(0, 0), 5));
                    if (simulatedLock.getRandom().nextFloat() < simulatedLock.getResistance()) {
                        simulatedLock.resetSeries();
                        unlockingIdx = 0;
                        for (TextureWidget core : lockCores) {
                            core.setY(height / 2 - lockHeight * pixelSize / 2 + 2 * pixelSize);
                        }
                    }
                }
                yield true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (curIdx > 0) {
                    animations.add(new BezierAnimation(lockPick,
                            new Vec2(-lockInterval * pixelSize, 0), 10));
                    --curIdx;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_D -> {
                if (curIdx < LOCK_LENGTH - 1) {
                    animations.add(new BezierAnimation(lockPick,
                            new Vec2(lockInterval * pixelSize, 0), 10));
                    ++curIdx;
                }
                yield true;
            }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
