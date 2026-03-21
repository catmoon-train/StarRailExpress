package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import org.agmas.noellesroles.client.animation.AbstractAnimation;
import org.agmas.noellesroles.client.animation.AnimationTimeLineManager;
import org.agmas.noellesroles.client.animation.BezierAnimation;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.agmas.noellesroles.packet.Loot.LootRequestC2SPacket;
import org.agmas.noellesroles.packet.Loot.LootMultiRequestC2SPacket;
import org.agmas.noellesroles.utils.Pair;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽奖信息页
 * - 用于显示卡池信息，以及启动抽奖
 * - 左侧选项卡常驻 + 右侧展示图 + 底部操作栏（抽奖、五连抽、预览）
 */
public class LootInfoScreen extends AbstractPixelScreen {
    private static final class LayoutMetrics {
        private final int leftX;
        private final int topY;
        private final int totalWidth;
        private final int totalHeight;
        private final int sidebarWidth;
        private final int sidebarPadding;
        private final int sidebarContentX;
        private final int sketchX;
        private final int sketchY;
        private final int sketchWidth;
        private final int sketchHeight;
        private final int sketchEdge;
        private final int actionBarHeight;
        private final int actionBarY;
        private final int actionBtnSpacing;
        private final int actionStartX;
        private final int actionButtonWidth;
        private final int actionButtonHeight;
        private final int sidebarViewportHeight;
        private final int poolButtonX;
        private final int poolButtonY;
        private final int poolButtonWidth;
        private final int poolButtonHeight;
        private final int poolButtonStep;

        private LayoutMetrics(int screenWidth, int screenHeight, int centerX, int centerY, int poolCount) {
            int availableWidth = Math.max(screenWidth - 32, 320);
            int availableHeight = Math.max(screenHeight - 32, 240);
            float widthScale = (float) availableWidth / (float) BASE_TOTAL_WIDTH;
            float heightScale = (float) availableHeight / (float) BASE_TOTAL_HEIGHT;
            float scale = Math.max(MIN_LAYOUT_SCALE, Math.min(MAX_LAYOUT_SCALE, Math.min(widthScale, heightScale)));

            int sidebarWidthValue = Math.max(76, Math.round(BASE_SIDEBAR_WIDTH * scale));
            int sidebarPaddingValue = Math.max(6, Math.round(BASE_SIDEBAR_PADDING * scale));
            int sketchEdgeValue = Math.max(14, Math.round(BASE_SKETCH_EDGE * scale));
            int actionBarHeightValue = Math.max(30, Math.round(BASE_ACTION_BAR_HEIGHT * scale));
            int actionBtnSpacingValue = Math.max(6, Math.round(BASE_ACTION_BTN_SPACING * scale));

            int maxTotalWidth = Math.max(screenWidth - 16, 260);
            int maxTotalHeight = Math.max(screenHeight - 16, 200);
            int availableSketchWidth = Math.max(160, maxTotalWidth - sidebarWidthValue - sketchEdgeValue);
            int availableSketchHeight = Math.max(96, maxTotalHeight - actionBarHeightValue - sketchEdgeValue);

            float sketchAspect = (float) BASE_SKETCH_WIDTH / (float) BASE_SKETCH_HEIGHT;
            int sketchWidthValue = availableSketchWidth;
            int sketchHeightValue = Math.round(sketchWidthValue / sketchAspect);
            if (sketchHeightValue > availableSketchHeight) {
                sketchHeightValue = availableSketchHeight;
                sketchWidthValue = Math.round(sketchHeightValue * sketchAspect);
            }

            sketchWidthValue = Mth.clamp(sketchWidthValue, 160, availableSketchWidth);
            sketchHeightValue = Mth.clamp(sketchHeightValue, 90, availableSketchHeight);

            int computedTotalWidth = sidebarWidthValue + sketchEdgeValue + sketchWidthValue;
            if (computedTotalWidth > maxTotalWidth) {
                int overflow = computedTotalWidth - maxTotalWidth;
                int shrinkSidebar = Math.min(overflow, Math.max(0, sidebarWidthValue - 72));
                sidebarWidthValue -= shrinkSidebar;
                overflow -= shrinkSidebar;
                if (overflow > 0)
                    sketchEdgeValue = Math.max(10, sketchEdgeValue - overflow);
            }

            sketchWidth = sketchWidthValue;
            sketchHeight = sketchHeightValue;
            sketchEdge = sketchEdgeValue;
            actionBarHeight = actionBarHeightValue;
            sidebarWidth = sidebarWidthValue;
            sidebarPadding = sidebarPaddingValue;
            actionBtnSpacing = actionBtnSpacingValue;

            totalWidth = sidebarWidth + sketchEdge + sketchWidth;
            totalHeight = sketchEdge + sketchHeight + actionBarHeight;
            leftX = centerX - totalWidth / 2;
            topY = centerY - totalHeight / 2;
            sidebarViewportHeight = totalHeight - actionBarHeight;

            sidebarContentX = leftX;
            sketchX = leftX + sidebarWidth + sketchEdge / 2;
            sketchY = topY + sketchEdge / 2;

            int visibleButtonCount = Math.max(1, Math.min(poolCount, MAX_VISIBLE_POOL_BUTTONS));
            poolButtonWidth = sidebarWidth;
            poolButtonHeight = Mth.clamp(Math.round(BASE_POOL_BUTTON_HEIGHT * scale * 0.9f), MIN_POOL_BUTTON_HEIGHT, MAX_POOL_BUTTON_HEIGHT);
            int remainingSpace = Math.max(0, sidebarViewportHeight - visibleButtonCount * poolButtonHeight);
            int buttonGap = Math.min(MAX_POOL_BUTTON_GAP, remainingSpace / Math.max(visibleButtonCount + 1, 1));
            poolButtonStep = poolButtonHeight + buttonGap;
            poolButtonX = sidebarContentX;
            poolButtonY = topY + buttonGap;

            actionButtonHeight = Math.max(20, Math.round(BASE_ACTION_BUTTON_HEIGHT * scale));
            int maxActionButtonWidth = sketchWidth / 3 - actionBtnSpacing;
            actionButtonWidth = Math.max(56, Math.min(poolButtonWidth, maxActionButtonWidth));
            int actionButtonsTotalWidth = actionButtonWidth * 3 + actionBtnSpacing * 2;
            actionBarY = topY + totalHeight - actionBarHeight + (actionBarHeight - actionButtonHeight) / 2;
            actionStartX = leftX + sidebarWidth + Math.max(0, (sketchEdge + sketchWidth - actionButtonsTotalWidth) / 2);
        }
    }

    public static class PoolButton extends AbstractButton {
        public interface OnRelease {
            void onRelease(PoolButton button);
        }
        private int poolID;
        private final OnRelease onRelease;
        private boolean isPressed = false;
        private boolean selected = false;

        public PoolButton(int poolId, int i, int j, int k, int l, Component component, OnRelease onRelease) {
            super(i, j, k, l, component);
            this.poolID = poolId;
            this.onRelease = onRelease;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float f) {
            Minecraft minecraft = Minecraft.getInstance();
            int fillColor = getFillColor();
            int borderColor = selected ? selectedBorderColor.getRGB() : buttonBorderColor.getRGB();
            int textColor = selected ? selectedTextColor.getRGB() : buttonTextColor.getRGB();

            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, applyAlpha(fillColor));
            guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, applyAlpha(borderColor));
            guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, applyAlpha(borderColor));
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, applyAlpha(borderColor));
            guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, applyAlpha(borderColor));

            String buttonText = minecraft.font.plainSubstrByWidth(getMessage().getString(), Math.max(8, width - 10));
            int textX = getX() + width / 2 - minecraft.font.width(buttonText) / 2;
            int textY = getY() + (height - 8) / 2;
            guiGraphics.drawString(minecraft.font, buttonText, textX, textY, applyAlpha(textColor), false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

        }

        @Override
        public void onPress() {
            isPressed = true;
        }

        @Override
        public void onRelease(double d, double e) {
            isPressed = false;
            if (isHovered) {
                onRelease.onRelease(this);
            }
        }

        public float getAlpha() {
            return this.alpha;
        }

        public void setPoolID(int poolID) {
            this.poolID = poolID;
        }

        public int getPoolID() {
            return poolID;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        private int getFillColor() {
            if (isPressed)
                return selected ? selectedPressedColor.getRGB() : buttonPressedColor.getRGB();
            if (isHovered)
                return selected ? selectedHoverColor.getRGB() : buttonHoverColor.getRGB();
            return selected ? selectedIdleColor.getRGB() : buttonIdleColor.getRGB();
        }

        private int applyAlpha(int color) {
            int alphaBits = Mth.clamp((int) (this.alpha * 255.0f), 0, 255) << 24;
            return alphaBits | (color & 0x00FFFFFF);
        }

        public boolean isOnButton(int mouseX, int mouseY) {
            return mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= getY()
                    && mouseY < getY() + getHeight();
        }
    }

    protected static class AnimationController extends AbstractWidget {
        public AnimationController() {
            super(0, 0, 0, 0, Component.empty());
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {

        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

        }

        private float curBgProcess = 0f;
    }

    public LootInfoScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
        // 重置成员状态
        initialized = false;
        poolButtons = new ArrayList<>();
        animationStack = new ArrayList<>();
        animationController = new AnimationController();

        List<LotteryManager.LotteryPool> lotteryPools = LotteryManager.getInstance().getLotteryPools();
        layoutMetrics = new LayoutMetrics(width, height, centerX, centerY, lotteryPools.size());

        List<Pair<Float, AbstractAnimation>> animations = new ArrayList<>();
        // 新布局计算：左侧选项卡 + 右侧展示区 + 底部操作栏
        int leftX = layoutMetrics.leftX;
        int topY = layoutMetrics.topY;
        int sketchX = layoutMetrics.sketchX;
        int sketchY = layoutMetrics.sketchY;
        // 无卡池信息时的处理
        try {
            curPool = lotteryPools.getFirst();
            // 设置预览按钮：隐形按钮点击立绘打开预览,
            viewPoolBtn = Button.builder(
                    Component.empty(),
                    buttonWidget -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        minecraft.setScreen(new ViewLotteryPoolScreen(curPool.getPoolID(), this));
                    })
                    .pos(sketchX, sketchY)
                    .size(layoutMetrics.sketchWidth, layoutMetrics.sketchHeight)
                    .build();
            viewPoolBtn.setAlpha(0f);
            viewPoolBtn.active = false;
            // 按钮处理顺序：先加入的优先处理
            addRenderableWidget(viewPoolBtn);
            // 设置卡池立绘
            poolSketch = new TextureWidget(
                    sketchX,
                    sketchY + layoutMetrics.sketchEdge / 2,
                    layoutMetrics.sketchWidth, layoutMetrics.sketchHeight,
                    BASE_SKETCH_WIDTH, BASE_SKETCH_HEIGHT,
                    ResourceLocation.fromNamespaceAndPath(
                            "noellesroles", "textures/gui/loot/pool_bg" +
                                    curPool.getPoolID()
                                    + ".png"));
            addRenderableWidget(poolSketch);
            poolSketch.setAlpha(0f);
            // 立绘动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    poolSketch,
                    new Vec2(0f, (float) -layoutMetrics.sketchEdge / 2),
                    (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .build()));
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    poolSketch,
                    new Vec2(1f, 0f),
                    (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        poolSketch.setAlpha(poolSketch.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));

            // 底部操作栏按钮
            int actionBarY = layoutMetrics.actionBarY;
            int actionStartX = layoutMetrics.actionStartX;

            // 添加开始抽奖按钮（单抽）
            startPoolBtn = new PoolButton(
                    curPool.getPoolID(),
                    actionStartX,
                    actionBarY,
                        layoutMetrics.actionButtonWidth,
                        layoutMetrics.actionButtonHeight,
                    Component.translatable("screen.noellesroles.loot.lootBtn"),
                    poolButton -> {
                        ClientPlayNetworking.send(new LootRequestC2SPacket(curPool.getPoolID()));
                    });
            addRenderableWidget(startPoolBtn);
            startPoolBtn.active = false;
            startPoolBtn.setAlpha(0f);

            // 添加五连抽按钮
            multiPoolBtn = new PoolButton(
                    curPool.getPoolID(),
                    actionStartX + layoutMetrics.actionButtonWidth + layoutMetrics.actionBtnSpacing,
                    actionBarY,
                    layoutMetrics.actionButtonWidth,
                    layoutMetrics.actionButtonHeight,
                    Component.translatable("screen.noellesroles.loot.multiLootBtn"),
                    poolButton -> {
                        ClientPlayNetworking.send(new LootMultiRequestC2SPacket(curPool.getPoolID(), 5));
                    });
            addRenderableWidget(multiPoolBtn);
            multiPoolBtn.active = false;
            multiPoolBtn.setAlpha(0f);

            // 添加预览按钮
            previewBtn = new PoolButton(
                    curPool.getPoolID(),
                    actionStartX + (layoutMetrics.actionButtonWidth + layoutMetrics.actionBtnSpacing) * 2,
                    actionBarY,
                    layoutMetrics.actionButtonWidth,
                    layoutMetrics.actionButtonHeight,
                    Component.translatable("screen.noellesroles.loot.previewBtn"),
                    poolButton -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        minecraft.setScreen(new ViewLotteryPoolScreen(curPool.getPoolID(), this));
                    });
            addRenderableWidget(previewBtn);
            previewBtn.active = false;
            previewBtn.setAlpha(0f);

            // 按钮动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    startPoolBtn,
                    new Vec2(1f, 0f),
                    (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        startPoolBtn.setAlpha(startPoolBtn.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    multiPoolBtn,
                    new Vec2(1f, 0f),
                    (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        multiPoolBtn.setAlpha(multiPoolBtn.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    previewBtn,
                    new Vec2(1f, 0f),
                    (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        previewBtn.setAlpha(previewBtn.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));
        } catch (Exception e) {
            curPool = null;
            poolSketch = null;
            viewPoolBtn = null;
        }

        // 为每个卡池添加卡池按钮
        for (int i = 0; i < lotteryPools.size(); ++i) {
            LotteryManager.LotteryPool curBtnPool = lotteryPools.get(i);
            PoolButton poolBtn = new PoolButton(
                    curBtnPool.getPoolID(),
                    layoutMetrics.poolButtonX,
                    layoutMetrics.poolButtonY,
                    layoutMetrics.poolButtonWidth, layoutMetrics.poolButtonHeight,
                    Component.literal(curBtnPool.getName()),
                    (buttonWidget) -> {
                        switchToPool(curBtnPool.getPoolID());
                    });
            poolBtn.setAlpha(0f);
            // 按钮排列动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    poolBtn,
                    new Vec2(0f, 0f),
                    (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .build()));
            // 透明度动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    poolBtn,
                    new Vec2(1f, 0f),
                    (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                    .setCallback((vec2) -> {
                        poolBtn.setAlpha(poolBtn.getAlpha() + vec2.x);
                    })
                    .setIntErrorFix(false)
                    .build()));
            poolButtons.add(poolBtn);
            poolBtn.active = false;
            addRenderableWidget(poolBtn);
        }
        if (curPool != null) {
            PoolButton curPoolBtn = poolButtons.get(curPool.getPoolID() - 1);
            if (curPoolBtn != null)
                curPoolBtn.setSelected(true);
        }

        setSidebarScroll(sidebarScrollOffset);

        animationTimeLineManager = AnimationTimeLineManager.builder()
                .addAnimation(0f, BezierAnimation.builder(
                        animationController,
                        new Vec2(1.0f, 0),
                        (int) (initBgTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            animationController.curBgProcess += vec2.x;
                        })
                        .setIntErrorFix(false)
                        .build())
                .addAnimations(animations)
                .build();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (!initialized) {
            if (animationTimeLineManager.isFinished()) {
                initialized = true;
                if (viewPoolBtn != null)
                   viewPoolBtn.active = true;
                if (startPoolBtn != null)
                    startPoolBtn.active = true;
                if (multiPoolBtn != null)
                    multiPoolBtn.active = true;
                if (previewBtn != null)
                    previewBtn.active = true;
                for (PoolButton poolButton : poolButtons)
                    poolButton.active = true;
            } else
                animationTimeLineManager.renderUpdate(delta);
        }
        animationStack.forEach(animation -> animation.renderUpdate(delta));
        animationStack.removeIf(AbstractAnimation::isFinished);

        int leftX = layoutMetrics.leftX;
        int topY = layoutMetrics.topY;
        int sidebarBottomY = topY + layoutMetrics.sidebarViewportHeight;
        int contentRightX = leftX + layoutMetrics.sidebarWidth
            + Math.round(animationController.curBgProcess * (layoutMetrics.sketchEdge + layoutMetrics.sketchWidth));

        // 绘制左侧选项卡背景（常驻矩形）
        guiGraphics.fill(
            (int) (leftX + (1.0f - animationController.curBgProcess) * layoutMetrics.sidebarWidth),
                topY,
            leftX + layoutMetrics.sidebarWidth,
            sidebarBottomY,
                poolBtnBgColor.getRGB());
        guiGraphics.fill(
            leftX + layoutMetrics.sidebarWidth - 1,
            topY,
            leftX + layoutMetrics.sidebarWidth,
            sidebarBottomY,
            panelDividerColor.getRGB());
        // 绘制右侧展示区背景
        guiGraphics.fill(
            leftX + layoutMetrics.sidebarWidth,
                topY,
            contentRightX,
            sidebarBottomY,
                sketchBgColor.getRGB());

        if (poolSketch != null)
            poolSketch.render(guiGraphics, mouseX, mouseY, delta);
        renderSidebarScrollbarBackground(guiGraphics);
        guiGraphics.enableScissor(leftX, topY, leftX + layoutMetrics.sidebarWidth, sidebarBottomY);
        for (PoolButton poolBtn : poolButtons)
            poolBtn.render(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.disableScissor();

        // 绘制底部操作栏背景，使其覆盖左下角侧栏区域
        guiGraphics.fill(
                leftX,
                sidebarBottomY,
                contentRightX,
                topY + layoutMetrics.totalHeight,
                actionBarBgColor.getRGB());
        guiGraphics.fill(
                leftX,
                sidebarBottomY,
                contentRightX,
                sidebarBottomY + 1,
                actionBarLineColor.getRGB());

        renderSidebarScrollbar(guiGraphics);
        if (startPoolBtn != null)
            startPoolBtn.render(guiGraphics, mouseX, mouseY, delta);
        if (multiPoolBtn != null)
            multiPoolBtn.render(guiGraphics, mouseX, mouseY, delta);
        if (previewBtn != null)
            previewBtn.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (layoutMetrics != null && isPointInSidebar(mouseX, mouseY) && getSidebarMaxScroll() > 0) {
            setSidebarScroll(sidebarScrollOffset - (float) verticalAmount * Math.max(16.0f, layoutMetrics.poolButtonHeight * 0.45f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isPointInSidebarScrollbar(mouseX, mouseY)) {
            draggingSidebarScrollbar = true;
            updateSidebarScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSidebarScrollbar && button == 0) {
            updateSidebarScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0)
            draggingSidebarScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void switchToPool(int poolD) {
        if (curPool != null && poolD == curPool.getPoolID())
            return;
        LotteryManager.LotteryPool nextPool = LotteryManager.getInstance().getLotteryPool(poolD);
        if (nextPool == null)
            return;
        if (poolSketch == null || startPoolBtn == null || multiPoolBtn == null || previewBtn == null)
            return;
        poolSketch.setTEXTURE(getPoolSketchTexture(nextPool.getPoolID()));
        // 添加位移和透明度动画
        poolSketch.setY(layoutMetrics.sketchY + layoutMetrics.sketchEdge / 2);
        animationStack.add(
                BezierAnimation.builder(
                        poolSketch,
                        new Vec2(0f, (float) -layoutMetrics.sketchEdge / 2),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .build());
        poolSketch.setAlpha(0f);
        animationStack.add(
                BezierAnimation.builder(
                        poolSketch,
                        new Vec2(1f, 0f),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            poolSketch.setAlpha(poolSketch.getAlpha() + vec2.x);
                        })
                        .setIntErrorFix(false)
                        .build());
        startPoolBtn.setPoolID(poolD);
        startPoolBtn.setAlpha(0f);
        animationStack.add(
                BezierAnimation.builder(
                        startPoolBtn,
                        new Vec2(1f, 0f),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            startPoolBtn.setAlpha(startPoolBtn.getAlpha() + vec2.x);
                        })
                        .setIntErrorFix(false)
                        .build());
        multiPoolBtn.setPoolID(poolD);
        multiPoolBtn.setAlpha(0f);
        animationStack.add(
                BezierAnimation.builder(
                        multiPoolBtn,
                        new Vec2(1f, 0f),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            multiPoolBtn.setAlpha(multiPoolBtn.getAlpha() + vec2.x);
                        })
                        .setIntErrorFix(false)
                        .build());
        previewBtn.setPoolID(poolD);
        previewBtn.setAlpha(0f);
        animationStack.add(
                BezierAnimation.builder(
                        previewBtn,
                        new Vec2(1f, 0f),
                        (int) (initWidgetTime / AbstractAnimation.secondPerTick))
                        .setCallback((vec2) -> {
                            previewBtn.setAlpha(previewBtn.getAlpha() + vec2.x);
                        })
                        .setIntErrorFix(false)
                        .build());
        switchToPoolBtn(poolD);
        curPool = nextPool;
    }

    public void switchToPoolBtn(int poolD) {
        if (curPool == null || poolD == curPool.getPoolID())
            return;
        for (PoolButton poolButton : poolButtons) {
            boolean isCurrent = curPool != null && poolButton.getPoolID() == curPool.getPoolID();
            boolean isNext = poolButton.getPoolID() == poolD;
            poolButton.setSelected(isNext && !isCurrent);
        }
    }

    private void setSidebarScroll(float nextScrollOffset) {
        sidebarScrollOffset = Mth.clamp(nextScrollOffset, 0.0f, (float) getSidebarMaxScroll());
        updatePoolButtonPositions();
    }

    private void updatePoolButtonPositions() {
        if (layoutMetrics == null)
            return;
        int baseX = layoutMetrics.poolButtonX;
        int baseY = layoutMetrics.poolButtonY - Math.round(sidebarScrollOffset);
        for (int i = 0; i < poolButtons.size(); ++i) {
            PoolButton poolButton = poolButtons.get(i);
            poolButton.setX(baseX);
            poolButton.setY(baseY + i * layoutMetrics.poolButtonStep);
            poolButton.setWidth(layoutMetrics.poolButtonWidth);
            poolButton.setHeight(layoutMetrics.poolButtonHeight);
        }
    }

    private int getSidebarContentHeight() {
        if (layoutMetrics == null)
            return 0;
        return Math.max(0, poolButtons.size() * layoutMetrics.poolButtonStep + layoutMetrics.poolButtonHeight - layoutMetrics.poolButtonStep + layoutMetrics.poolButtonY - layoutMetrics.topY);
    }

    private int getSidebarMaxScroll() {
        if (layoutMetrics == null)
            return 0;
        return Math.max(0, getSidebarContentHeight() - layoutMetrics.sidebarViewportHeight);
    }

    private boolean isPointInSidebar(double mouseX, double mouseY) {
        if (layoutMetrics == null)
            return false;
        return mouseX >= layoutMetrics.leftX
                && mouseX < layoutMetrics.leftX + layoutMetrics.sidebarWidth
                && mouseY >= layoutMetrics.topY
            && mouseY < layoutMetrics.topY + layoutMetrics.sidebarViewportHeight;
    }

    private int getScrollbarX() {
        return layoutMetrics.leftX + layoutMetrics.sidebarWidth - SIDEBAR_SCROLLBAR_WIDTH;
    }

    private int getScrollbarThumbHeight() {
        int contentHeight = Math.max(1, getSidebarContentHeight());
        int viewportHeight = Math.max(1, layoutMetrics.sidebarViewportHeight);
        return Math.max(18, viewportHeight * viewportHeight / contentHeight);
    }

    private int getScrollbarThumbY() {
        int maxScroll = getSidebarMaxScroll();
        if (maxScroll <= 0)
            return layoutMetrics.topY;
        int travel = Math.max(0, layoutMetrics.sidebarViewportHeight - getScrollbarThumbHeight());
        return layoutMetrics.topY + Math.round((sidebarScrollOffset / maxScroll) * travel);
    }

    private boolean isPointInSidebarScrollbar(double mouseX, double mouseY) {
        if (layoutMetrics == null || getSidebarMaxScroll() <= 0)
            return false;
        int scrollbarX = getScrollbarX();
        return mouseX >= scrollbarX
                && mouseX < scrollbarX + SIDEBAR_SCROLLBAR_WIDTH
                && mouseY >= layoutMetrics.topY
            && mouseY < layoutMetrics.topY + layoutMetrics.sidebarViewportHeight;
    }

    private void updateSidebarScrollFromMouse(double mouseY) {
        int maxScroll = getSidebarMaxScroll();
        if (maxScroll <= 0)
            return;
        int thumbHeight = getScrollbarThumbHeight();
        int travel = Math.max(1, layoutMetrics.sidebarViewportHeight - thumbHeight);
        float normalized = (float) ((mouseY - layoutMetrics.topY - thumbHeight / 2.0f) / travel);
        setSidebarScroll(Mth.clamp(normalized, 0.0f, 1.0f) * maxScroll);
    }

    private void renderSidebarScrollbarBackground(GuiGraphics guiGraphics) {
        if (layoutMetrics == null)
            return;
        int scrollbarBgX = layoutMetrics.leftX + layoutMetrics.sidebarWidth - SIDEBAR_SCROLLBAR_BG_WIDTH;
        guiGraphics.fill(scrollbarBgX, layoutMetrics.topY,
                layoutMetrics.leftX + layoutMetrics.sidebarWidth,
                layoutMetrics.topY + layoutMetrics.sidebarViewportHeight,
                sidebarScrollAreaColor.getRGB());
    }

    private void renderSidebarScrollbar(GuiGraphics guiGraphics) {
        if (layoutMetrics == null || getSidebarMaxScroll() <= 0)
            return;
        int scrollbarX = getScrollbarX();
        int thumbY = getScrollbarThumbY();
        int thumbHeight = getScrollbarThumbHeight();
        guiGraphics.fill(scrollbarX, layoutMetrics.topY, scrollbarX + SIDEBAR_SCROLLBAR_WIDTH,
                layoutMetrics.topY + layoutMetrics.sidebarViewportHeight, sidebarScrollTrackColor.getRGB());
        guiGraphics.fill(scrollbarX, thumbY, scrollbarX + SIDEBAR_SCROLLBAR_WIDTH,
                thumbY + thumbHeight, sidebarScrollThumbColor.getRGB());
    }

    private ResourceLocation getPoolSketchTexture(int poolID) {
        return ResourceLocation.fromNamespaceAndPath(
                "noellesroles", "textures/gui/loot/pool_bg" + poolID + ".png");
    }

    private static final Color poolBtnBgColor = new Color(0xFF202B39, true);
    private static final Color sketchBgColor = new Color(0xFFF3EAD9, true);
    private static final Color actionBarBgColor = new Color(0xFF2C3646, true);
    private static final Color actionBarLineColor = new Color(0xFFD0B27A, true);
    private static final Color panelDividerColor = new Color(0xFFB69A68, true);
    private static final Color buttonIdleColor = new Color(0xFF243547, true);
    private static final Color buttonHoverColor = new Color(0xFF31465E, true);
    private static final Color buttonPressedColor = new Color(0xFF172433, true);
    private static final Color buttonBorderColor = new Color(0xFF8D734A, true);
    private static final Color selectedIdleColor = new Color(0xFFD7B47A, true);
    private static final Color selectedHoverColor = new Color(0xFFE3C48C, true);
    private static final Color selectedPressedColor = new Color(0xFFC29C63, true);
    private static final Color selectedBorderColor = new Color(0xFFF7E7BE, true);
    private static final Color buttonTextColor = new Color(0xFFF0E8D7, true);
    private static final Color selectedTextColor = new Color(0xFF1D2734, true);
    private static final Color sidebarScrollAreaColor = new Color(0x88304052, true);
    private static final Color sidebarScrollTrackColor = new Color(0x55263645, true);
    private static final Color sidebarScrollThumbColor = new Color(0xCCDEC08A, true);
    private static final int BASE_SKETCH_WIDTH = 320;
    private static final int BASE_SKETCH_HEIGHT = 180;
    private static final int BASE_POOL_BUTTON_HEIGHT = (int) (32);
    private static final int BASE_SIDEBAR_WIDTH = 108;
    private static final int BASE_SIDEBAR_PADDING = 12;
    private static final int BASE_SKETCH_EDGE = 36;
    private static final int BASE_ACTION_BAR_HEIGHT = 40;
    private static final int BASE_ACTION_BUTTON_HEIGHT = (int) (18 * 1.5f);
    private static final int BASE_ACTION_BTN_SPACING = 12;
    private static final int BASE_TOTAL_WIDTH = BASE_SKETCH_EDGE + BASE_SKETCH_WIDTH + BASE_SIDEBAR_WIDTH;
    private static final int BASE_TOTAL_HEIGHT = BASE_SKETCH_EDGE + BASE_SKETCH_HEIGHT + BASE_ACTION_BAR_HEIGHT;
    private static final int MAX_VISIBLE_POOL_BUTTONS = 6;
    private static final int MIN_POOL_BUTTON_HEIGHT = 18;
    private static final int MAX_POOL_BUTTON_HEIGHT = 28;
    private static final int MAX_POOL_BUTTON_GAP = 6;
    private static final int SIDEBAR_SCROLLBAR_BG_WIDTH = 10;
    private static final int SIDEBAR_SCROLLBAR_WIDTH = 5;
    private static final float MIN_LAYOUT_SCALE = 0.72f;
    private static final float MAX_LAYOUT_SCALE = 1.28f;
    /** 背景初始化时间 */
    private static final float initBgTime = 0.5f;
    private static final float initWidgetTime = 1.0f;
    private List<PoolButton> poolButtons = null;
    private List<AbstractAnimation> animationStack = null;
    private AnimationController animationController = null;
    private Button viewPoolBtn = null;
    private PoolButton startPoolBtn = null;
    private PoolButton multiPoolBtn = null;
    private PoolButton previewBtn = null;
    private AnimationTimeLineManager animationTimeLineManager = null;
    private LotteryManager.LotteryPool curPool = null;
    private TextureWidget poolSketch = null;
    private LayoutMetrics layoutMetrics = null;
    private float sidebarScrollOffset = 0.0f;
    private boolean draggingSidebarScrollbar = false;
    private boolean initialized;
}
