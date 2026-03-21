package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    public static class PoolButton extends AbstractButton {
        public interface OnRelease {
            void onRelease(PoolButton button);
        }

        public static final ResourceLocation[] poolBtnTextures = {
                ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/pool_btn_idle.png"),
                ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/pool_btn_hover.png"),
                ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/pool_btn_pressed.png"),
                ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/pool_btn_idle_selected.png"),
                ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/pool_btn_hover_selected.png"),
                ResourceLocation.fromNamespaceAndPath("noellesroles","textures/gui/loot/pool_btn_pressed_selected.png"),
        };
        private static final int poolBtnWidth = 32;
        private static final int poolBtnHeight = 18;
        /**
         * 卡池按钮的贴图
         * <p>
         * 0: idle
         * 1: hover
         * 2: pressed
         * </p>
         */
        private final List<TextureWidget> poolBtnTextureWidgets;
        private int poolID;
        private OnRelease onRelease;
        private boolean isPressed = false;
        private int curTexIdx = 0;

        public PoolButton(int poolId, int i, int j, int k, int l, Component component, OnRelease onRelease) {
            super(i, j, k, l, component);
            this.poolID = poolId;
            poolBtnTextureWidgets = new ArrayList<>();
            poolBtnTextureWidgets.add(new TextureWidget(
                    i, j, k, l,
                    poolBtnWidth, poolBtnHeight,
                    poolBtnTextures[0]));
            poolBtnTextureWidgets.add(new TextureWidget(
                    i, j, k, l,
                    poolBtnWidth, poolBtnHeight,
                    poolBtnTextures[1]));
            poolBtnTextureWidgets.add(new TextureWidget(
                    i, j, k, l,
                    poolBtnWidth, poolBtnHeight,
                    poolBtnTextures[2]));
            for (TextureWidget textureWidget : poolBtnTextureWidgets)
                textureWidget.visible = false;
            this.onRelease = onRelease;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float f) {
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            // 根据按钮状态选择贴图
            poolBtnTextureWidgets.get(curTexIdx).visible = false;
            if (isPressed)
                curTexIdx = 2;
            else if (isHovered)
                curTexIdx = 1;
            else
                curTexIdx = 0;
            poolBtnTextureWidgets.get(curTexIdx).visible = true;
            poolBtnTextureWidgets.get(curTexIdx).render(guiGraphics, mouseX, mouseY, f);

            // 渲染黑色文本
            Minecraft minecraft = Minecraft.getInstance();
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            this.renderString(guiGraphics, minecraft.font, (int) (this.alpha * 255) << 24);
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
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

        @Override
        public void setX(int x) {
            super.setX(x);
            for (TextureWidget textureWidget : poolBtnTextureWidgets)
                textureWidget.setX(x);
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            for (TextureWidget textureWidget : poolBtnTextureWidgets)
                textureWidget.setY(y);
        }

        @Override
        public void setAlpha(float alpha) {
            super.setAlpha(alpha);
            for (TextureWidget textureWidget : poolBtnTextureWidgets)
                textureWidget.setAlpha(alpha);
        }

        public float getAlpha() {
            return this.alpha;
        }

        public void setPoolID(int poolID) {
            this.poolID = poolID;
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

        List<Pair<Float, AbstractAnimation>> animations = new ArrayList<>();
        // 新布局计算：左侧选项卡 + 右侧展示区 + 底部操作栏
        int leftX = centerX - totalWidth / 2;
        int topY = centerY - totalHeight / 2;
        int sketchX = leftX + poolBtnWidth + poolBtnEdgeWidth + sketchEdge / 2;
        int sketchY = topY + sketchEdge / 2;
        // 无卡池信息时的处理
        try {
            curPool = LotteryManager.getInstance().getLotteryPools().getFirst();
            // 设置预览按钮：隐形按钮点击立绘打开预览,
            viewPoolBtn = Button.builder(
                    Component.empty(),
                    buttonWidget -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        minecraft.setScreen(new ViewLotteryPoolScreen(curPool.getPoolID(), this));
                    })
                    .pos(sketchX, sketchY)
                    .size(sketchWidth, sketchHeight)
                    .build();
            viewPoolBtn.setAlpha(0f);
            viewPoolBtn.active = false;
            // 按钮处理顺序：先加入的优先处理
            addRenderableWidget(viewPoolBtn);
            // 设置卡池立绘
            poolSketch = new TextureWidget(
                    sketchX,
                    sketchY,
                    sketchWidth, sketchHeight,
                    sketchWidth, sketchHeight,
                    ResourceLocation.fromNamespaceAndPath(
                            "noellesroles", "textures/gui/loot/pool_bg" +
                                    curPool.getPoolID()
                                    + ".png"));
            addRenderableWidget(poolSketch);
            poolSketch.setAlpha(0f);
            // 立绘动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    poolSketch,
                    new Vec2(0f, (float) -sketchEdge / 2),
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
            int actionBarY = topY + totalHeight - actionBarHeight + (actionBarHeight - poolBtnHeight) / 2;
            int actionBtnTotalWidth = poolBtnWidth * 3 + actionBtnSpacing * 2;
            int actionStartX = leftX + (poolBtnWidth + poolBtnEdgeWidth) + (totalWidth - (poolBtnWidth + poolBtnEdgeWidth) - actionBtnTotalWidth) / 2;

            // 添加开始抽奖按钮（单抽）
            startPoolBtn = new PoolButton(
                    curPool.getPoolID(),
                    actionStartX,
                    actionBarY,
                    poolBtnWidth,
                    poolBtnHeight,
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
                    actionStartX + poolBtnWidth + actionBtnSpacing,
                    actionBarY,
                    poolBtnWidth,
                    poolBtnHeight,
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
                    actionStartX + (poolBtnWidth + actionBtnSpacing) * 2,
                    actionBarY,
                    poolBtnWidth,
                    poolBtnHeight,
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

        int poolBtnX = centerX - totalWidth / 2 + ((poolBtnWidth + poolBtnEdgeWidth) - poolBtnWidth) / 2;
        int poolBtnY = centerY - totalHeight / 2;
        // 为每个卡池添加卡池按钮
        List<LotteryManager.LotteryPool> lotteryPools = LotteryManager.getInstance().getLotteryPools();
        for (int i = 0; i < lotteryPools.size(); ++i) {
            LotteryManager.LotteryPool curBtnPool = lotteryPools.get(i);
            PoolButton poolBtn = new PoolButton(
                    curBtnPool.getPoolID(),
                    poolBtnX,
                    poolBtnY,
                    poolBtnWidth, poolBtnHeight,
                    Component.literal(curBtnPool.getName()),
                    (buttonWidget) -> {
                        switchToPool(curBtnPool.getPoolID());
                    });
            poolBtn.setAlpha(0f);
            // 按钮排列动画
            animations.add(new Pair<>(initBgTime, BezierAnimation.builder(
                    poolBtn,
                    new Vec2(0f, i * ((float) poolBtnInterval / 2 + poolBtnHeight) + (float) poolBtnInterval / 2),
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
            for (int i = 0; i < 3 && curPoolBtn != null; ++i)
                curPoolBtn.poolBtnTextureWidgets.get(i).setTEXTURE(PoolButton.poolBtnTextures[i + 3]);
        }

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

        int leftX = centerX - totalWidth / 2;
        int topY = centerY - totalHeight / 2;

        // 绘制左侧选项卡背景（常驻矩形）
        guiGraphics.fill(
                (int) (leftX + (1.0f - animationController.curBgProcess) * poolBtnWidth),
                topY,
                leftX + (poolBtnWidth + poolBtnEdgeWidth),
                topY + totalHeight,
                poolBtnBgColor.getRGB());
        // 绘制右侧展示区背景
        guiGraphics.fill(
                leftX + (poolBtnWidth + poolBtnEdgeWidth),
                topY,
                (int) (leftX + totalWidth - (1.0f - animationController.curBgProcess) * sketchWidth),
                topY + totalHeight - actionBarHeight,
                sketchBgColor.getRGB());
        // 绘制底部操作栏背景
        guiGraphics.fill(
                leftX,
                topY + totalHeight - actionBarHeight,
                (int) (leftX + totalWidth - (1.0f - animationController.curBgProcess) * sketchWidth),
                topY + totalHeight,
                actionBarBgColor.getRGB());
        // 绘制操作栏顶部分隔线
        guiGraphics.fill(
                leftX,
                topY + totalHeight - actionBarHeight,
                (int) (leftX + totalWidth - (1.0f - animationController.curBgProcess) * sketchWidth),
                topY + totalHeight - actionBarHeight + 1,
                actionBarLineColor.getRGB());

        if (poolSketch != null)
            poolSketch.render(guiGraphics, mouseX, mouseY, delta);
        for (PoolButton poolBtn : poolButtons) {
            poolBtn.render(guiGraphics, mouseX, mouseY, delta);
        }
        if (startPoolBtn != null)
            startPoolBtn.render(guiGraphics, mouseX, mouseY, delta);
        if (multiPoolBtn != null)
            multiPoolBtn.render(guiGraphics, mouseX, mouseY, delta);
        if (previewBtn != null)
            previewBtn.render(guiGraphics, mouseX, mouseY, delta);
    }

    public void switchToPool(int poolD) {
        if (curPool != null && poolD == curPool.getPoolID())
            return;
        LotteryManager.LotteryPool nextPool = LotteryManager.getInstance().getLotteryPool(poolD);
        if (nextPool == null)
            return;
        poolSketch.setTEXTURE(getPoolSketchTexture(nextPool.getPoolID()));
        // 添加位移和透明度动画
        poolSketch.setY(centerY - totalHeight / 2 + sketchEdge / 2);
        animationStack.add(
                BezierAnimation.builder(
                        poolSketch,
                        new Vec2(0f, (float) -sketchEdge / 2),
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
        PoolButton curPoolBtn = null;
        PoolButton nextPoolBtn = null;
        for (PoolButton poolButton : poolButtons)
            if (curPool != null && poolButton.poolID == curPool.getPoolID())
                curPoolBtn = poolButton;
            else if (poolButton.poolID == poolD)
                nextPoolBtn = poolButton;
        for (int i = 0; i < 3; ++i) {
            // 构造函数中必定构造了三个
            if (curPoolBtn != null)
                curPoolBtn.poolBtnTextureWidgets.get(i).setTEXTURE(PoolButton.poolBtnTextures[i]);
            if (nextPoolBtn != null)
                nextPoolBtn.poolBtnTextureWidgets.get(i).setTEXTURE(PoolButton.poolBtnTextures[i + 3]);
        }
    }

    private ResourceLocation getPoolSketchTexture(int poolID) {
        return ResourceLocation.fromNamespaceAndPath(
                "noellesroles", "textures/gui/loot/pool_bg" + poolID + ".png");
    }

    private static final Color poolBtnBgColor = new Color(0xFF555555, true);
    private static final Color sketchBgColor = new Color(0xFFEEEEEE, true);
    private static final Color actionBarBgColor = new Color(0xFF3A3A4A, true);
    private static final Color actionBarLineColor = new Color(0xFF666688, true);
    private static final int sketchWidth = 320;
    private static final int sketchHeight = 180;
    private static final int poolBtnWidth = (int) (32 * 1.5f);
    private static final int poolBtnHeight = (int) (18 * 1.5f);
    /** 立绘边距：用于确定背景大小 */
    private static final int sketchEdge = 36;
    private static final int poolBtnEdgeWidth = 8;
    private static final int poolBtnInterval = 6;
    /** 底部操作栏高度 */
    private static final int actionBarHeight = 40;
    /** 操作栏按钮间距 */
    private static final int actionBtnSpacing = 12;
    private static final int totalWidth = sketchEdge + sketchWidth + poolBtnWidth + poolBtnEdgeWidth;
    private static final int totalHeight = sketchEdge + sketchHeight + actionBarHeight;
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
    private boolean initialized;
}
