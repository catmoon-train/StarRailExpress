package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import org.agmas.noellesroles.client.animation.*;
import org.agmas.noellesroles.client.widget.TextureWidget;
import org.agmas.noellesroles.client.widget.TickTimerWidget;
import org.agmas.noellesroles.client.widget.TimerWidget;
import org.agmas.noellesroles.packet.ChefCookC2SPacket;
import org.agmas.noellesroles.utils.Pair;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.*;

import static java.lang.Math.abs;

public class CookingGameScreen extends AbstractPixelScreen {
    public static class GameItem {
        public static boolean isOverlap(GameItem item1, GameItem item2) {
            return item1.imgWidget.getY() < item2.imgWidget.getBottom()
                    && item1.imgWidget.getBottom() > item2.imgWidget.getY() &&
                    item1.imgWidget.getX() < item2.imgWidget.getRight()
                    && item1.imgWidget.getRight() > item2.imgWidget.getX();
        }

        public GameItem(int i, int j, int k, int l, int textureWidth, int textureHeight, ResourceLocation texture) {
            imgWidget = new TextureWidget(i, j, k, l, textureWidth, textureHeight, texture);
        }

        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            imgWidget.render(guiGraphics, mouseX, mouseY, delta);
        }

        public void tick() {
        }

        protected TextureWidget imgWidget;
        protected boolean isRemovable = false;
    }

    public static class FoodCard extends GameItem {
        public FoodCard(int i, int j, int k, int l, int buffID, float buffSecond) {
            super(i, j, k, l, BASE_FOOD_SIZE, BASE_FOOD_SIZE,
                    ResourceLocation.fromNamespaceAndPath(
                            "noellesroles", "textures/gui/cooking/buff" + buffID + ".png"));
            this.buffSecond = buffSecond;
            this.buffID = buffID;
        }

        @Override
        public void tick() {
            velocity += gravity;
            imgWidget.setY((int) (imgWidget.getY() + velocity));
        }

        /** 屏幕重力可变，使用static重力需要由屏幕修改 */
        private static float gravity = 0f;
        private float buffSecond = 0;
        private float velocity = 0;
        private int buffID = 0;
    }

    public static class Pan extends GameItem {
        public Pan(int boundMinX, int boundMaxX, int y, int imgW, int imgH, int velocity) {
            super(boundMinX, y, imgW, imgH, PAN_WIDTH, PAN_HEIGHT,
                    ResourceLocation.fromNamespaceAndPath(
                            "noellesroles", "textures/gui/cooking/pan.png"));
            this.minX = boundMinX;
            this.maxX = boundMaxX;
            this.velocity = velocity;
        }

        @Override
        public void tick() {
            float deltaX = 0;
            if (isLeft)
                deltaX -= velocity * (isShiftKeyDown ? velocityScale : 1);
            if (isRight)
                deltaX += velocity * (isShiftKeyDown ? velocityScale : 1);
            if ((deltaX < 0 && imgWidget.getX() > minX) || (deltaX > 0 && imgWidget.getX() + PAN_WIDTH < maxX)) {
                imgWidget.setX((int) (imgWidget.getX() + deltaX));
            }
            if (imgWidget.getX() < minX) {
                imgWidget.setX(minX);
            }
            if (imgWidget.getRight() > maxX) {
                imgWidget.setX(maxX - imgWidget.getWidth());
            }
        }

        private static final int PAN_WIDTH = 23;
        private static final int PAN_HEIGHT = 7;
        private static final float velocityScale = 2.f;
        private final int minX, maxX;
        private boolean isLeft = false;
        private boolean isRight = false;
        /** shift键是否按下，用于是否启用加速以及快速结算 */
        private boolean isShiftKeyDown = false;
        private int velocity = 0;
    }

    public static class InfoCard extends AbstractWidget {
        public InfoCard(int i, int j, int k, int l, int buffID) {
            this(i, j, k, l, buffID,
                    BASE_FOOD_SIZE, BASE_FOOD_SIZE,
                    ResourceLocation.fromNamespaceAndPath(
                            "noellesroles", "textures/gui/cooking/buff" + buffID + ".png"));
        }

        public InfoCard(int i, int j, int k, int l, int buffID, int textureWidth, int textureHeight,
                ResourceLocation texture) {
            super(i, j, k, l, Component.empty());
            infoImg = new TextureWidget(i, j, l, l, textureWidth, textureHeight, texture);
            this.buffID = buffID;
        }

        public float getBuffSecond() {
            return buffSecond;
        }

        public void addBuffSecond(float buffSecond) {
            this.buffSecond += buffSecond;
            if (this.buffSecond < 0)
                this.buffSecond = 0;
            if (this.buffSecond > MAX_BUFF_TIME)
                this.buffSecond = MAX_BUFF_TIME;
            percentage = this.buffSecond / MAX_BUFF_TIME;
        }

        public void setBuffSecond(float buffSecond) {
            this.buffSecond = buffSecond;
            if (this.buffSecond < 0)
                this.buffSecond = 0;
            if (this.buffSecond > MAX_BUFF_TIME)
                this.buffSecond = MAX_BUFF_TIME;
            percentage = this.buffSecond / MAX_BUFF_TIME;
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            infoImg.setX(x);
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            infoImg.setY(y);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            infoImg.render(guiGraphics, mouseX, mouseY, delta);
            int barX = getX() + infoImg.getWidth();
            int barY = getY() + infoImg.getHeight() / 2 - infoImg.getHeight() / 4;
            int barWidth = width - INFO_BOUND * 2 - infoImg.getWidth();
            int barHeight = infoImg.getHeight() / 4;
            // 绘制进度条：高度为图片的1/4
            guiGraphics.fill(
                    barX, barY, barX + barWidth, barY + barHeight,
                    PROCESS_BAR_COLOR.first.getRGB());
            guiGraphics.fill(
                    barX, barY, barX + (int) (barWidth * percentage), barY + barHeight,
                    PROCESS_BAR_COLOR.second.getRGB());
            guiGraphics.setColor(1, 1, 1, 1f);
        }

        public float getAlpha() {
            return alpha;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

        }

        /** 进度条颜色:first-BG, second-bar */
        public static final Pair<Color, Color> PROCESS_BAR_COLOR = new Pair<>(new Color(0xFF000000, true),
                new Color(0xFF77CCFF, true));
        private TextureWidget infoImg;
        private float buffSecond = 0;
        private float percentage = 0;
        private int buffID = 0;
    }

    /** 显示分数的 Item, x的值为图片大小, y位图片h+时间文本显示高 */
    public static class ScoreCard extends GameItem {
        public ScoreCard(int i, int j, int k, int l, int buffID, float time, Font font) {
            super(i, j, k, k, BASE_FOOD_SIZE, BASE_FOOD_SIZE,
                    ResourceLocation.fromNamespaceAndPath(
                            "noellesroles", "textures/gui/cooking/buff" + buffID + ".png"));
            scoreText = new AlphaStringWidget(i, j + k, k, l - k,
                    Component.translatable("screen.noellesroles.chef.result.seconds", String.format("%.1f", time)),
                    font);
        }

        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            super.render(guiGraphics, mouseX, mouseY, delta);
            scoreText.render(guiGraphics, mouseX, mouseY, delta);
        }

        AlphaStringWidget scoreText;
    }

    /** 可以获取 alpha的StringWidget */
    public static class AlphaStringWidget extends StringWidget {
        public AlphaStringWidget(int i, int j, int k, int l, Component component, Font font) {
            super(i, j, k, l, component, font);
        }

        public float getAlpha() {
            return alpha;
        }
    }

    public CookingGameScreen() {
        super(Component.empty());
        randomSource = RandomSource.create();
        infoCards = new HashMap<>();
    }

    @Override
    protected void init() {
        super.init();
        // 重置列表
        foods.clear();
        catchFeedBackStrings.clear();
        animations.clear();
        infoCards.clear();
        tickTimers.clear();
        timerWidgets.clear();
        buffTimers.clear();
        scoreCards.clear();
        lastInfoCards.clear();

        // 设置初值
        isInitialized = false;
        isTimeout = false;
        isInitScore = false;
        gravity = (float) (2 * height) / (FALL_TICKS * FALL_TICKS);
        FoodCard.gravity = this.gravity;
        nextTick = 0;
        curTime = DURATION / 20;
        gameStartX = (int) (width * (1f - GAME_BOUND));
        curScoreBarHeight = 0;
        maxScoreBarHeight = 0;
        while (BASE_FOOD_SIZE * ROW_FOOD_NUM * (pixelSize + 1) < (width - gameStartX))
            ++pixelSize;
        foodScaleBounds.first = FOOD_SCALE_BOUNDS.first;
        foodScaleBounds.second = FOOD_SCALE_BOUNDS.second;
        foodScaleBounds.first *= pixelSize;
        foodScaleBounds.second *= pixelSize;

        // 成员构造
        pan = new Pan(
                gameStartX,
                width,
                height - (BASE_FOOD_SIZE * pixelSize) / 2,
                Pan.PAN_WIDTH * pixelSize,
                Pan.PAN_HEIGHT * pixelSize,
                (int) (width * (1f - GAME_BOUND) / 10));
        int strWidgetSize = BASE_FOOD_SIZE * pixelSize * 2;
        float scaleX = centerX - strWidgetSize * START_STRING_SCALE / 2;
        float scaleY = centerY + strWidgetSize * START_STRING_SCALE / 2;
        int originalX = (int) (scaleX / START_STRING_SCALE);
        int originalY = (int) (scaleY / START_STRING_SCALE);
        timeLineStringWidget = new AlphaStringWidget(
                originalX, originalY,
                strWidgetSize, strWidgetSize,
                Component.literal("3"),
                font);
        timeLineStringWidget.active = true;
        pausedStringWidget = new AlphaStringWidget(
                originalX, (int) (centerY / START_STRING_SCALE - (float) strWidgetSize / 2),
                strWidgetSize, strWidgetSize,
                Component.literal("Paused"),
                font);

        // 时序控制
        // 倒计时10S
        TickTimerWidget gameTimer = new TickTimerWidget(
                20,
                false,
                (timerWidget) -> {
                    --curTime;
                    if (curTime >= 0)
                        timeLineStringWidget.setMessage(Component.literal(curTime + ""));
                });
        tickTimers.add(gameTimer);
        tickTimers.add(new TickTimerWidget(
                DURATION,
                true,
                (TickTimerWidget) -> {
                    isTimeout = true;
                    timeLineStringWidget.setMessage(Component.literal("Time Out"));
                    timeLineStringWidget.setPosition(originalX,
                            (int) (centerY / START_STRING_SCALE - (float) strWidgetSize / 2));
                    // 播放停止音效
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(
                                    SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                                    1.0F
                            )
                    );
                    // 停止倒计时
                    gameTimer.setOneShoot(false);
                    // 设置倒计时结束后的停顿再进行下一步处理
                    buffTimers.add(new TickTimerWidget(
                            10,
                            true,
                            (timerWidget) -> {
                                int textHeight = 20;
                                int scoreCardSize = BASE_FOOD_SIZE * pixelSize * 2;
                                int rowNum = (infoCards.size() + ROW_BUFF_NUM - 1) / ROW_BUFF_NUM;
                                scoreBarWidth = INFO_BOUND * 2 + (scoreCardSize + INFO_INTERVAL) * ROW_BUFF_NUM
                                        - INFO_INTERVAL;
                                maxScoreBarHeight = INFO_BOUND * 2
                                        + (scoreCardSize + textHeight + INFO_INTERVAL) * rowNum - INFO_INTERVAL;
                                int aniTick = 10;
                                animations.add(BezierAnimation.builder(
                                        // 无作用只是用来辅助动画绘图
                                                timeLineStringWidget,
                                        new Vec2(0, maxScoreBarHeight),
                                        aniTick)
                                        .setCallback(vec -> {
                                            curScoreBarHeight += (int) vec.y;
                                        })
                                        .build());
                                isInitScore = true;
                                timeLineStringWidget.active = false;
                                buffTimers.add(new TickTimerWidget(
                                        aniTick,
                                        true,
                                        (tickTimer) -> {
                                            // 添加信息卡
                                            int curX = INFO_BOUND + centerX - scoreBarWidth / 2;
                                            int curY = INFO_BOUND + centerY - maxScoreBarHeight / 2;
                                            int curNum = 1;
                                            for (Map.Entry<Integer, InfoCard> entry : infoCards.entrySet()) {
                                                if (curNum > ROW_BUFF_NUM) {
                                                    curX = INFO_BOUND + centerX - scoreBarWidth / 2;
                                                    curY += scoreCardSize + INFO_INTERVAL + textHeight;
                                                    curNum = 1;
                                                }
                                                scoreCards.add(new ScoreCard(
                                                        curX,
                                                        curY,
                                                        scoreCardSize,
                                                        scoreCardSize + textHeight,
                                                        entry.getValue().buffID,
                                                        entry.getValue().buffSecond,
                                                        font));
                                                ++curNum;
                                                curX += scoreCardSize + INFO_INTERVAL;
                                            }
                                            sendResult();
                                            Button closeBtn = Button.builder(
                                                    Component.translatable("screen.noellesroles.chef.result.close"),
                                                    (btn) -> {
                                                        this.onClose();
                                                    }).bounds(this.centerX - 50, this.centerY + 60, 100, 20)
                                                    .build();
                                            addRenderableWidget(closeBtn);
                                        }));
                            }));
                }));

        // 控制开场动画时间线
        animationTimeLineManager = AnimationTimeLineManager.builder()
                .addAnimation(0f, new BezierAnimation(
                        timeLineStringWidget,
                        new Vec2(0, (float) -strWidgetSize * 1.5f),
                        20))
                .addAnimation(1f, new BezierAnimation(
                        timeLineStringWidget,
                        new Vec2(0, (float) -strWidgetSize * 1.5f),
                        20))
                .addAnimation(2f, new BezierAnimation(
                        timeLineStringWidget,
                        new Vec2(0, (float) -strWidgetSize * 1.5f),
                        20))
                .addAnimation(3f, new BezierAnimation(
                        timeLineStringWidget,
                        new Vec2(0, (float) -strWidgetSize * 1.5f),
                        20))
                .build();
        // 控制开场动画文本及音效
        timerWidgets.add(new TimerWidget(
                0,
                true,
                (timerWidget) -> {
                    // 播放默认按钮音效
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(
                                    SoundEvents.UI_BUTTON_CLICK,
                                    1.0F
                            )
                    );
                }));
        timerWidgets.add(new TimerWidget(
                1,
                true,
                (timerWidget) -> {
                    timeLineStringWidget.setMessage(Component.literal("2"));
                    timeLineStringWidget.setY(originalY);
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(
                                    SoundEvents.UI_BUTTON_CLICK,
                                    1.0F
                            )
                    );
                }));
        timerWidgets.add(new TimerWidget(
                2,
                true,
                (timerWidget) -> {
                    timeLineStringWidget.setMessage(Component.literal("1"));
                    timeLineStringWidget.setY(originalY);
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(
                                    SoundEvents.UI_BUTTON_CLICK,
                                    1.0F
                            )
                    );
                }));
        timerWidgets.add(new TimerWidget(
                3,
                true,
                (timerWidget) -> {
                    timeLineStringWidget.setMessage(Component.literal("GO"));
                    timeLineStringWidget.setY(originalY);
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(
                                    SoundEvents.END_PORTAL_SPAWN,
                                    1.0F
                            )
                    );
                }));
        timerWidgets.add(new TimerWidget(
                4,
                true,
                (timerWidget) -> {
                    isInitialized = true;
                    timeLineStringWidget.setMessage(Component.literal(curTime + ""));
                    timeLineStringWidget.setPosition(centerX - strWidgetSize / 2, 0);
                }));
    }

    @SuppressWarnings("unused")
    @Override
    public void tick() {
        super.tick();
        if (!isInitialized || isPaused)
            return;
        tickTimers.forEach(TickTimerWidget::tick);
        tickTimers.removeIf(TickTimerWidget::isFinished);
        if (!tickTimers.isEmpty()) {
            tickTimers.addAll(buffTimers);
            buffTimers.clear();
        }
        if (isTimeout)
            return;
        // 到时则检查并随机生成食材
        if (foods.size() < MAX_FOOD_COUNT && nextTick <= 0) {
            int buffID = 0;
            while (buffID == 0)
                buffID = randomSource.nextInt(BUFF_BOUNDS.first, BUFF_BOUNDS.second);

            // 最小初速度，最大速度
            float v_min = 50f * height / FALL_TICKS;
            float v_max = 100f * height / FALL_TICKS;
            // 使用正太分布进行速度生成
            float v = v_min + Math.min((float) abs(randomSource.nextGaussian()), 3);
            nextTick = randomSource.nextInt(FOOD_GENERATE_TICKS);
            // 创建时长百分比：根据时长边界计算具体时长，并利用此百分比作为缩放
            float buffTimePercentage = randomSource.nextFloat();
            float buffTime = (BUFF_SECONDS.second - BUFF_SECONDS.first) * buffTimePercentage + BUFF_SECONDS.first;
            int scale = (int) ((foodScaleBounds.second - foodScaleBounds.first) * buffTimePercentage
                    + foodScaleBounds.first);
            foods.add(new FoodCard(
                    randomSource.nextInt(gameStartX, width - BASE_FOOD_SIZE * pixelSize), 0,
                    BASE_FOOD_SIZE * pixelSize + scale,
                    BASE_FOOD_SIZE * pixelSize + scale,
                    buffID,
                    buffTime));
        }
        pan.tick();
        foods.forEach(food -> {
            if (!food.isRemovable) {
                // 食材 tick更新
                food.tick();
                if (GameItem.isOverlap(food, pan) && food.imgWidget.getBottom() >= pan.imgWidget.getBottom()) {
                    food.isRemovable = true;

                    // 当接到一种新食材时，创建该食材的进度条，并添加进进度条列表、播放动画
                    if (!infoCards.containsKey(food.buffID)) {
                        infoCards.put(food.buffID, new InfoCard(
                                INFO_BOUND,
                                height,
                                gameStartX - INFO_BOUND * 2,
                                BASE_FOOD_SIZE * pixelSize,
                                food.buffID));
                        animations.add(BezierAnimation.builder(
                                infoCards.get(food.buffID),
                                new Vec2(0,
                                        INFO_BOUND
                                                + (BASE_FOOD_SIZE * pixelSize + INFO_INTERVAL) * (infoCards.size() - 1)
                                                - height),
                                STRING_DURATION_TICKS).build());
                        lastInfoCards.add(infoCards.get(food.buffID));
                    }
                    infoCards.get(food.buffID).addBuffSecond(food.buffSecond);

                    // 进行实时排序并设置动画
                    List<Pair<Integer, InfoCard>> curInfoCardsList = new ArrayList<>();
                    for (int i = 0; i < lastInfoCards.size(); ++i)
                        curInfoCardsList.add(new Pair<>(i, lastInfoCards.get(i)));
                    // 降序排序
                    curInfoCardsList.sort((o1, o2) ->
                        Float.compare(o2.second.buffSecond, o1.second.buffSecond));
                    for (int i = 0; i < lastInfoCards.size(); ++i) {
                        if (i == curInfoCardsList.get(i).first)
                            continue;
                        animations.add(BezierAnimation.builder(
                                lastInfoCards.get(curInfoCardsList.get(i).first),
                                new Vec2(0,
                                        (BASE_FOOD_SIZE * pixelSize + INFO_INTERVAL) * (i - curInfoCardsList.get(i).first)),
                                STRING_DURATION_TICKS)
                                .build());
                    }
                    for (int i = 0; i < lastInfoCards.size(); ++i)
                        if (i != curInfoCardsList.get(i).first)
                            lastInfoCards.set(i, curInfoCardsList.get(i).second);

                    // 播放接到食物的音效
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(
                                    SoundEvents.GENERIC_SPLASH, // 落水音效
                                    1.0F, // 音量
                                    1.0F // 音高
                    ));
                    String symbol = food.buffSecond > 0 ? "+" : "";
                    // 创建一个显示获取分数的控件并附件动画
                    AlphaStringWidget addSecStr = new AlphaStringWidget(
                            food.imgWidget.getX() - food.imgWidget.getWidth() / 2,
                            food.imgWidget.getY() - food.imgWidget.getHeight() / 2,
                            food.imgWidget.getWidth() * 2,
                            food.imgWidget.getHeight(),
                            Component.literal(symbol + String.format("%.1f", food.buffSecond) + " Second"),
                            font);
                    catchFeedBackStrings.add(addSecStr);
                    animations.add(new ConstantSpeedAnimation(
                            addSecStr,
                            new Vec2(0, (float) -food.imgWidget.getHeight() / 2),
                            STRING_DURATION_TICKS));
                    animations.add(BezierAnimation.builder(
                            addSecStr,
                            new Vec2(-1f, 0),
                            STRING_DURATION_TICKS)
                            .setControl(new Vec2(-0.1f, 0), new Vec2(-0.5f, 0))
                            .setCallback(
                                    vec -> {
                                        addSecStr.setAlpha(addSecStr.getAlpha() + vec.x);
                                    })
                            .build());
                } else if (food.imgWidget.getY() > height)
                    food.isRemovable = true;
            }
        });
        while (!foods.isEmpty() && foods.peek().isRemovable)
            foods.removeFirst();
        --nextTick;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        if (!isPaused) {
            animations.forEach(animation -> animation.renderUpdate(f));
            animationTimeLineManager.renderUpdate(f);
            timerWidgets.forEach(timerWidget -> timerWidget.onRenderUpdate(f));
            timerWidgets.removeIf(TimerWidget::isFinished);
        }

        // 绘制信息栏
        guiGraphics.fill(0, 0, gameStartX, height, INFO_BG_COLOR.first.getRGB());
        infoCards.forEach((integer, infoCard) -> infoCard.render(guiGraphics, i, j, f));
        guiGraphics.renderOutline(0, 0, gameStartX, height, INFO_BG_COLOR.second.getRGB());

        foods.forEach(food -> food.render(guiGraphics, i, j, f));
        pan.render(guiGraphics, i, j, f);
        catchFeedBackStrings.forEach(stringWidget -> {
            // 设置颜色及透明度，实现文本渐变
            guiGraphics.setColor(1f, 1f, 1f, stringWidget.getAlpha());
            stringWidget.render(guiGraphics, i, j, f);
        });

        // 恢复 RGB
        guiGraphics.setColor(1f, 1f, 1f, 1f);
        // 渲染文本并缩放
        if (timeLineStringWidget.active) {
            if (!isInitialized || isTimeout) {
                PoseStack startStringPoseStack = guiGraphics.pose();
                startStringPoseStack.pushPose();
                startStringPoseStack.scale(START_STRING_SCALE, START_STRING_SCALE, 1.0f);
                timeLineStringWidget.render(guiGraphics, i, j, f);
                startStringPoseStack.popPose();
            } else
                timeLineStringWidget.render(guiGraphics, i, j, f);
        }
        if (isPaused && pausedStringWidget.active) {
            PoseStack startStringPoseStack = guiGraphics.pose();
            startStringPoseStack.pushPose();
            startStringPoseStack.scale(START_STRING_SCALE, START_STRING_SCALE, 1.0f);
            pausedStringWidget.render(guiGraphics, i, j, f);
            startStringPoseStack.popPose();
        }
        guiGraphics.drawString(this.font, Component.translatable("screen.noellesroles.cook.cookgameTip"),
                width - font.width(Component.translatable("screen.noellesroles.cook.cookgameTip")),
                0,
                0xFFFFFFFF);

        // 游戏结束，绘制计分板
        if (isTimeout && isInitScore) {
            // 居中绘制计分板
            guiGraphics.fill(centerX - scoreBarWidth / 2, centerY - maxScoreBarHeight / 2,
                    centerX + scoreBarWidth / 2, centerY - maxScoreBarHeight / 2 + curScoreBarHeight, 0xFF111111);
            guiGraphics.renderOutline(centerX - scoreBarWidth / 2, centerY - maxScoreBarHeight / 2,
                    scoreBarWidth, curScoreBarHeight, INFO_BG_COLOR.second.getRGB());
            scoreCards.forEach(scoreCard -> scoreCard.render(guiGraphics, i, j, f));
        }

        while (!animations.isEmpty() && animations.peek().isFinished())
            animations.removeFirst();
        while (!catchFeedBackStrings.isEmpty() && catchFeedBackStrings.peek().getAlpha() <= 0.01f)
            catchFeedBackStrings.removeFirst();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_A -> {
                pan.isLeft = true;
                yield true;
            }
            case GLFW.GLFW_KEY_D -> {
                pan.isRight = true;
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT_SHIFT -> {
                pan.isShiftKeyDown = true;
                yield true;
            }
            // 截断 esc键，暂停游戏，再次esc继续游戏，shift esc立即退出，并直接返回结果
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (isTimeout || pan.isShiftKeyDown) {
                    sendResult();
                    super.keyPressed(keyCode, scanCode, modifiers);
                }
                isPaused = !isPaused;
                pausedStringWidget.active = isPaused;
                yield true;
            }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_A -> {
                pan.isLeft = false;
                yield true;
            }
            case GLFW.GLFW_KEY_D -> {
                pan.isRight = false;
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT_SHIFT -> {
                pan.isShiftKeyDown = false;
                yield true;
            }
            default -> super.keyReleased(keyCode, scanCode, modifiers);
        };
    }
    /** 结算发布结果 */
    private void sendResult() {
        Map<Integer, Float> resultBuffTime = new HashMap<>();
        for (Map.Entry<Integer, InfoCard> entry : infoCards.entrySet())
            resultBuffTime.put(entry.getKey(), entry.getValue().buffSecond);
        // 向服务器发结果包
        ClientPlayNetworking.send(new ChefCookC2SPacket(resultBuffTime));
    }

    /**
     * buffID 边界:
     * <p>
     * - 负值绝对值代表 debuff ID; 正值代表 buff ID（nextInt右不取因此要+1)
     * - 返回时仅返回id值用于发包，服务器进行解析自行给予buff
     * - 根据buffID进行图像读取，只需要以buff + "ID"命名即可
     * </p>
     */
    private static final Pair<Integer, Integer> BUFF_BOUNDS = new Pair<>(-3, 8);
    /** 食材大小基础范围：与时长有关，此处填写的单位为材质像素单位，在初始化中会被转换为屏幕像素单位（*pixelSize) */
    private static final Pair<Integer, Integer> FOOD_SCALE_BOUNDS = new Pair<>(-5, 5);
    /** buff持续时间范围，可以为负数 */
    private static final Pair<Float, Float> BUFF_SECONDS = new Pair<>(0.5f, 15f);
    /** 屏幕占比(x,y) */
    @SuppressWarnings("unused")
    private static final Pair<Float, Float> INTRODUCTION_SIZE = new Pair<>(0.5f, 0.7f);
    /** 信息框背景颜色和线框颜色 */
    private static final Pair<Color, Color> INFO_BG_COLOR = new Pair<>(new Color(0x4F555555, true),
            new Color(0xFF77CCFF, true));
    /** 游戏部分屏幕占比(x) */
    private static final float GAME_BOUND = 0.8f;
    private static final float MAX_BUFF_TIME = 60f;
    private static final float START_STRING_SCALE = 2f;
    private static final int BASE_FOOD_SIZE = 16;
    /** 游戏区域内可以排列的食材数量：影响pixelSize */
    private static final int ROW_FOOD_NUM = 15;
    /** 同时存在的食材上限 */
    private static final int MAX_FOOD_COUNT = 15;
    /** 自由落体时食物掉落的时间：由于屏幕分辨率不同，以最终时间控制重力保持掉落时间统一 */
    private static final int FALL_TICKS = 40;
    private static final int FOOD_GENERATE_TICKS = 15;
    private static final int STRING_DURATION_TICKS = 30;
    /** 信息框中信息卡距离边界距离 */
    private static final int INFO_BOUND = 5;
    private static final int INFO_INTERVAL = 5;
    /** 游戏持续时间 */
    private static final int DURATION = 200;
    /** 结算时每行 buff的数量 */
    private static final int ROW_BUFF_NUM = 4;
    /** 动画列表 */
    private final Deque<AbstractAnimation> animations = new ArrayDeque<>();
    /** 接取成功的文字反馈 */
    private final Deque<AlphaStringWidget> catchFeedBackStrings = new ArrayDeque<>();
    /** 游戏中的食物对象 */
    private final Deque<FoodCard> foods = new ArrayDeque<>();
    /** tick 定时器列表 */
    private final List<TickTimerWidget> tickTimers = new ArrayList<>();
    /** 有时需要嵌套定时器，如果直接添加会和遍历冲突，使用列表缓存，遍历后添加 */
    private final List<TickTimerWidget> buffTimers = new ArrayList<>();
    /** 基于时间的渲染计时器列表 */
    private final List<TimerWidget> timerWidgets = new ArrayList<>();
    /** 结算信息卡列表 */
    private final List<ScoreCard> scoreCards = new ArrayList<>();
    /** 上一次排序后的卡片顺序，用于实时排序 */
    private final List<InfoCard> lastInfoCards = new ArrayList<>();
    /** 信息记录卡：存储对应 id的 buff时长 */
    private final Map<Integer, InfoCard> infoCards;
    /** 下落的食材的实际缩放范围 */
    private final Pair<Integer, Integer> foodScaleBounds = new Pair<>(FOOD_SCALE_BOUNDS.first,
            FOOD_SCALE_BOUNDS.second);
    private final RandomSource randomSource;
    private AnimationTimeLineManager animationTimeLineManager = null;
    /** 开始文本、时间结束文本等各个时间线的公共文本 */
    private AlphaStringWidget timeLineStringWidget = null;
    /** 暂停文本 */
    private AlphaStringWidget pausedStringWidget = null;
    /** 锅 */
    private Pan pan;
    /** 是否完成初始化动画 */
    private boolean isInitialized = false;
    /** 游戏时间是否到了 */
    private boolean isTimeout = false;
    /** 是否初始化完结算信息 */
    private boolean isInitScore = false;
    /** 是否暂停 */
    private boolean isPaused = false;
    /** 游戏重力 */
    private float gravity;
    /** 结算界面宽度 */
    private int scoreBarWidth;
    /** 结算界面高度 */
    private int maxScoreBarHeight;
    /** 当前结算界面高度:用于控制动画 */
    private int curScoreBarHeight;
    /** 游戏界面起始 X坐标 */
    private int gameStartX;
    /** 下次生成物品的时间 */
    private int nextTick;
    /** 当前游戏倒计时时间 */
    private int curTime;
}
