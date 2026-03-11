package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import org.agmas.noellesroles.client.animation.AbstractAnimation;
import org.agmas.noellesroles.client.animation.BezierAnimation;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.agmas.noellesroles.entity.LockEntity;
import org.agmas.noellesroles.packet.LockGameC2Packet;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

public class LockGameScreen extends Screen {
    private static class LockPickWidget extends TextureWidget {
        public LockPickWidget() {
            this(0, 0, 2, 0, 0);
        }

        public LockPickWidget(int x, int y, int length, int bodyLength, int pixelSize) {
            // 有length - 1段撬锁器体 + 1撬锁器锁头
            super(x, y,
                    ((length - 1) * bodyLength + textureWidth) * pixelSize, textureHeight * pixelSize,
                    textureWidth, textureHeight,
                    ResourceLocation.fromNamespaceAndPath(
                            "noellesroles", "textures/gui/lock_game_lock_pick.png"));
            this.pixelSize = pixelSize;
            this.length = length;
            this.bodyWidth = bodyLength;
        }

        public void initLockPick(int x, int y, int length, int bodyLength, int pixelSize) {
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
                if (i == length - 1)
                    curWidth = textureWidth;
                guiGraphics.blit(TEXTURE,
                        this.getX() + offsetX, this.getY(),
                        curWidth * pixelSize, this.height,
                        textureU, textureV,
                        curWidth, this.renderHeight,
                        textureWidth, textureHeight);
            }
        }

        public int getLockPickWidth() {
            return textureWidth;
        }

        @SuppressWarnings("unused")
        public int getLockPickBodyWidth() {
            return bodyWidth;
        }

        // 撬锁器的段数 = 撬锁器体 + 撬锁器头
        protected int length;
        // 撬锁器体的长度
        protected int bodyWidth;
        // 像素大小
        protected int pixelSize;
        // 撬锁器头的宽 = 原图宽
        protected final static int textureWidth = 16;
        protected final static int textureHeight = 2;
    }

    public LockGameScreen(Vec3i pos, LockEntity lockEntity) {
        super(Component.translatable("screen.noellesroles.lock_game"));
        this.lockPos = pos;
        this.lockEntity = lockEntity;
        this.lockCores = new ArrayList<>();
        this.animations = new ArrayDeque<>();
        this.lockPick = new LockPickWidget();
    }

    @Override
    protected void init() {
        super.init();

        // 撬锁小游戏页面布局
        final int lockWidth = 6;// 单锁宽度
        final int lockHeight = 16;
        final int lockCoreWidth = 2;
        final int lockCoreHeight = 11;
        if (this.lockEntity == null) {
            onClose();
            return;
        }
        int totalPixels = lockEntity.getLength() * 5 + 1;// 只有最后一个纹理多1像素
        // 根据屏幕大小重新规划像素缩放
        while ((width < totalPixels * pixelSize || height < lockHeight * pixelSize) && pixelSize > 1)
            --pixelSize;

        // 计算锁的位置
        int lockStartX = width / 2 - totalPixels * pixelSize / 2;
        int lockStartY = height / 2 - lockHeight * pixelSize / 2;
        int lockCoreStartX = lockStartX + 2 * pixelSize;
        int lockCoreStartY = lockStartY + 2 * pixelSize;

        // 绘制撬锁器，计算其相关位置
        // 初始撬锁器在第一个锁芯处 : 有length - 1段 + 锁头
        int lockPickX = lockStartX
                - (lockPick.getLockPickWidth() + (lockEntity.getLength() - 1) * (lockWidth - 1) - 4) * pixelSize;
        int lockPickY = lockStartY + 13 * pixelSize;
        lockPick.initLockPick(lockPickX, lockPickY, lockEntity.getLength(), lockInterval, pixelSize);
        addRenderableWidget(lockPick);

        // 绘制各个锁及锁芯
        for (int i = 0, curWidth = 0,
                offsetX = 0; i < lockEntity.getLength(); ++i, offsetX += (lockWidth - 1) * pixelSize) {
            // 拼接锁
            curWidth = i == lockEntity.getLength() - 1 ? 6 : 5;
            TextureWidget lockBody = new TextureWidget(
                    // 中间部分宽度为5、最后一个为6
                    lockStartX + offsetX, lockStartY,
                    curWidth * pixelSize, lockHeight * pixelSize,
                    curWidth, lockHeight,
                    lockWidth, lockHeight,
                    ResourceLocation.fromNamespaceAndPath(
                            "noellesroles", "textures/gui/lock_game_lock.png"));
            addRenderableWidget(lockBody);
            // 锁芯
            TextureWidget lockCore = new TextureWidget(
                    // 锁芯宽度为2
                    lockCoreStartX + offsetX, lockCoreStartY,
                    lockCoreWidth * pixelSize, lockCoreHeight * pixelSize,
                    lockCoreWidth, lockCoreHeight,
                    ResourceLocation.fromNamespaceAndPath(
                            "noellesroles", "textures/gui/lock_game_core.png"));
            addRenderableWidget(lockCore);
            this.lockCores.add(lockCore);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        animations.forEach(animation -> animation.renderUpdate(partialTick));
        animations.removeIf(AbstractAnimation::isFinished);
        guiGraphics.drawString(this.font, Component.translatable("screen.noellesroles.loot.lockGameTip"),
                width / 2 - font.width(Component.translatable("screen.noellesroles.loot.lockGameTip")) / 2,
                height - pixelSize * 2,
                0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 撬锁器向上移动距离
        int upMovement = 1;
        return switch (keyCode) {
            case GLFW.GLFW_KEY_W -> {
                for (int i = 0; i < unlockingIdx; ++i) {
                    if (curIdx == lockEntity.getSeriesUnlockIdx(i))
                        yield false;
                }
                animations.add(new BezierAnimation(
                        lockPick,
                        new Vec2(0, -upMovement * pixelSize),
                        new Vec2(0, -upMovement * pixelSize),
                        // 相对运动
                        new Vec2(0, 0),
                        5
                ));
                if(curIdx == lockEntity.getSeriesUnlockIdx(unlockingIdx)) {
                    animations.add(new BezierAnimation(
                            lockCores.get(curIdx),
                            new Vec2(0, - upMovement * pixelSize),
                            new Vec2(0, - upMovement * pixelSize),
                            new Vec2(0, - upMovement * pixelSize),
                            5
                            ));
                    // 成功解锁
                    ++unlockingIdx;
                    if (unlockingIdx == lockEntity.getLength()) {
                        // 发送完成请求
                        ClientPlayNetworking.send(new LockGameC2Packet(
                                new BlockPos(lockPos),
                                lockEntity.getId(),
                                true));
                        onClose();
                    }
                } else {
                    animations.add(new BezierAnimation(
                            lockCores.get(curIdx),
                            new Vec2(0, -upMovement * pixelSize),
                            new Vec2(0, -upMovement * pixelSize),
                            new Vec2(0, 0),
                            5
                    ));
                    // 尝试失败
                    RandomSource entityRandom = lockEntity.getRandom();
                    if (entityRandom.nextFloat() < lockEntity.getResistance()) {
                        // 发送失败请求
                        ClientPlayNetworking.send(new LockGameC2Packet(
                                new BlockPos(lockPos),
                                lockEntity.getId(),
                                false));
                        onClose();
                    }
                }
                yield true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (curIdx > 0) {
                    animations.add(new BezierAnimation(
                            lockPick,
                            new Vec2(-lockInterval * pixelSize, 0),
                            10
                    ));
                    --curIdx;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_D -> {
                if (curIdx < lockEntity.getLength() - 1) {
                    animations.add(new BezierAnimation(
                            lockPick,
                            new Vec2(lockInterval * pixelSize, 0),
                            10
                    ));
                    ++curIdx;
                }
                yield true;
            }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    public void addAnimation(BezierAnimation animation) {
        animations.offer(animation);
    }

    private static int pixelSize = 8;// 最大（默认）像素缩放的大小
    private final ArrayList<TextureWidget> lockCores;
    private final Queue<AbstractAnimation> animations;
    private final LockPickWidget lockPick;
    private final LockEntity lockEntity;
    private final Vec3i lockPos;
    private final int lockInterval = 5;// 锁芯间隔
    // 当前撬锁器位于的锁芯索引
    private int curIdx = 0;
    // 正在解锁的锁索引
    private int unlockingIdx = 0;
}
