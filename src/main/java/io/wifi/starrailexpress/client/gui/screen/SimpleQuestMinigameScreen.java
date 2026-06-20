package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 任务点小游戏的共用界面，承载所有无需自定义贴图的小游戏。
 * 所有界面文字均走语言键（minigame.starrailexpress.*），并统一了面板/动画风格。
 */
public class SimpleQuestMinigameScreen extends Screen {

    public enum Mode {
        WATER_VALVE("water_valve"),
        TYPING("typing"),
        PIPE_BIRD("pipe_bird"),
        FRUIT_NINJA("fruit_ninja"),
        MOUSE_WHACK("mouse_whack"),
        BRICK_BREAKER("brick_breaker"),
        MAKE_CHANGE("make_change"),
        SNAKE("snake"),
        MINESWEEPER("minesweeper"),
        SIMON_SAYS("simon_says"),
        GAME_2048("game_2048"),
        CATCH_EGGS("catch_eggs"),
        COLOR_SORT("color_sort"),
        GUESS_NUMBER("guess_number"),
        REACTION_TEST("reaction_test"),
        LINK_MATCH("link_match"),
        TETRIS("tetris"),
        MEMORY_MATCH("memory_match"),
        PIPE_CONNECT("pipe_connect"),
        LIGHTS_OUT("lights_out"),
        GAME_24("game_24"),
        MAZE("maze"),
        BALANCE_SCALE("balance_scale"),
        REACTOR_TEMPERATURE("reactor_temperature"),
        BOX_SORT("box_sort"),
        WIRE_CONNECT("wire_connect"),
        HOLD_BUTTON("hold_button"),
        CLEAN_DEBRIS("clean_debris"),
        SHOOTING("shooting"),
        WIRE_TUNING("wire_tuning"),
        SIGNAL_CALIBRATION("signal_calibration"),
        SHAPE_MATCH("shape_match"),
        SEQUENCE_BUTTONS("sequence_buttons"),
        CLEAN_STAINS("clean_stains"),
        PULL_LEVER("pull_lever"),
        TRASH_RECYCLE("trash_recycle"),
        ITEM_CHECKLIST("item_checklist"),
        SWIPE_CARD("swipe_card"),
        PLAY_MUSIC("play_music"),
        RHYTHM("rhythm"),
        LIGHT_CANDLES("light_candles"),
        WHACK_MOLE("whack_mole"),
        PUZZLE("puzzle"),
        STORAGE("storage"),
        SLICE_FOOD("slice_food"),
        THREE_CARDS("three_cards"),
        BREAK_JAR("break_jar"),
        ZONE_CALIBRATION("zone_calibration"),
        KLOTSKI("klotski");

        private final String id;

        Mode(String id) {
            this.id = id;
        }
    }

    private static final int PANEL_W = 430;
    private static final int PANEL_H = 270;
    private static final int HEADER_H = 24;

    private static final int WHITE = MinigameUI.WHITE;
    private static final int MUTED = MinigameUI.MUTED;
    private static final int GREEN = MinigameUI.GREEN;
    private static final int RED = MinigameUI.RED;
    private static final int YELLOW = MinigameUI.YELLOW;
    private static final int BLUE = MinigameUI.BLUE;
    private static final int PANEL = MinigameUI.PANEL;
    private static final int PANEL_DARK = MinigameUI.PANEL_DARK;

    private static final int INTRO_TICKS = 7;

    // 原版材质 ResourceLocation
    private static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath("starrailexpress", "textures/gui/background.png");

    // 原版物品栈缓存
    private static final ItemStack SPONGE = new ItemStack(Items.SPONGE);
    private static final ItemStack GLASS_BOTTLE = new ItemStack(Items.GLASS_BOTTLE);
    private static final ItemStack PAPER = new ItemStack(Items.PAPER);
    private static final ItemStack IRON_INGOT = new ItemStack(Items.IRON_INGOT);
    private static final ItemStack APPLE = new ItemStack(Items.APPLE);
    private static final ItemStack POISONOUS_POTATO = new ItemStack(Items.POISONOUS_POTATO);
    private static final ItemStack GUNPOWDER = new ItemStack(Items.GUNPOWDER);
    private static final ItemStack[] MUSIC_DISCS = {
        new ItemStack(Items.MUSIC_DISC_13),
        new ItemStack(Items.MUSIC_DISC_CAT),
        new ItemStack(Items.MUSIC_DISC_BLOCKS),
        new ItemStack(Items.MUSIC_DISC_CHIRP)
    };
    private static final ItemStack FLINT_AND_STEEL = new ItemStack(Items.FLINT_AND_STEEL);
    private static final ItemStack CANDLE = new ItemStack(Items.CANDLE);
    private static final ItemStack DECORATED_POT = new ItemStack(Items.DECORATED_POT);
    private static final ItemStack CARROT = new ItemStack(Items.CARROT);
    private static final ItemStack STONE = new ItemStack(Blocks.STONE);
    private static final ItemStack GRASS = new ItemStack(Blocks.GRASS_BLOCK);
    private static final ItemStack DIRT = new ItemStack(Blocks.DIRT);

    // 水果忍者：水果材质
    private static final ItemStack[] FRUITS = {
        new ItemStack(Items.APPLE),        // 苹果
        new ItemStack(Items.CHORUS_FRUIT), // 紫颂果
        new ItemStack(Items.BEETROOT),     // 甜菜根
        new ItemStack(Items.GOLDEN_APPLE), // 金苹果
        new ItemStack(Items.POTATO),       // 马铃薯
        new ItemStack(Items.CARROT),       // 胡萝卜
        new ItemStack(Items.MELON_SLICE),  // 西瓜
        new ItemStack(Items.PUMPKIN),      // 南瓜
        new ItemStack(Items.SWEET_BERRIES),// 甜浆果
        new ItemStack(Items.GLOW_BERRIES), // 发光浆果
    };

    private final Runnable onSuccess;
    private final Mode mode;
    private final Random rng = new Random();

    private final List<Piece> pieces = new ArrayList<>();
    private final List<Dot> dots = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private final List<Integer> connections = new ArrayList<>();
    private final List<Integer> selectedIndices = new ArrayList<>();

    private Piece draggedPiece;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean mouseHeld;
    private double lastMouseX;
    private double lastMouseY;
    private int hoverX;
    private int hoverY;

    private int targetTemp;
    private int currentTemp;
    private int progress;
    private int successCount;
    private int nextIndex;
    private int selectedWire = -1;
    private int selectedKnob = -1;
    private int selectedTopWire = -1;
    private float valueA;
    private float valueB;
    /** 拨电线：随机目标位置 */
    private float wireTargetA;
    private float wireTargetB;
    private float velocity;
    private float bulletX;
    private float bulletY;
    private boolean bulletActive;
    private int spawnTimer;
    private int holdTicks;
    private int clearTicks;
    private int animationTicks;
    private int highlightedCard;
    private int correctRecord;
    /** 三张牌：当前交换的源位置A */
    private int swapFromA = -1;
    /** 三张牌：当前交换的源位置B */
    private int swapFromB = -1;
    /** 三张牌：交换开始的 tick */
    private int swapStartTick;
    private boolean falling;
    private boolean armed;
    private float fallingY;

    /** 入场/呼吸动画的连续计时（每 tick +1）。 */
    private int uiTicks;
    private int introTicks;

    /** 水阀：随机旋转方向 true=顺时针 false=逆时针 */
    private boolean valveClockwise;
    /** 水阀：累计旋转进度（弧度） */
    private double valveTotalRotation;
    /** 水阀：当前阀轮角度（弧度），用于渲染旋转 */
    private double valveAngle;
    /** 水阀：上一次鼠标角度（弧度），用于计算 delta */
    private double valveLastMouseAngle;
    /** 水阀：是否正在拖拽阀轮 */
    private boolean valveDragging;
    /** 水阀：完成所需的总旋转量（弧度），约 1.5 圈 */
    private static final double VALVE_REQUIRED = Math.PI * 3.0;

    /** 打字：目标字符串 */
    private String typingTarget;
    /** 打字：玩家输入的字符串 */
    private final StringBuilder typingInput = new StringBuilder();
    /** 打字：是否输入错误（用于显示红色提示） */
    private boolean typingError;

    /** 管道小鸟：鸟的 Y 坐标 */
    private float birdY;
    /** 管道小鸟：鸟的垂直速度 */
    private float birdVelocity;
    /** 管道小鸟：管道列表 */
    private final List<PipePair> pipePairs = new ArrayList<>();
    /** 管道小鸟：已通过的管道数 */
    private int pipesPassed;
    /** 管道小鸟：需要通过的管道数 */
    private static final int PIPES_REQUIRED = 3;
    /** 管道小鸟：游戏是否运行中 */
    private boolean birdAlive;

    /** 水果忍者：水果列表 */
    private final List<Fruit> fruitList = new ArrayList<>();
    /** 水果忍者：切中计数 */
    private int fruitSliced;
    /** 水果忍者：生成计时器 */
    private int fruitSpawnTimer;
    /** 水果忍者：炸弹 ItemStack */
    private ItemStack bombItem;
    /** 水果忍者：鼠标上一点 */
    private double prevMouseX, prevMouseY;
    /** 水果忍者：目标切中数 */
    private static final int FRUIT_TARGET = 8;

    /** 打老鼠：老鼠列表 */
    private final List<RunningMouse> mice = new ArrayList<>();
    /** 打老鼠：抓到计数 */
    private int miceCaught;
    /** 打老鼠：生成计时器 */
    private int mouseSpawnTimer;
    /** 打老鼠：刷子 ItemStack */
    private static final ItemStack BRUSH = new ItemStack(Items.BRUSH);
    /** 打老鼠：目标抓到数 */
    private static final int MICE_TARGET = 5;

    /** 打砖块：砖块列表 */
    private final List<Brick> bricks = new ArrayList<>();
    /** 打砖块：球位置/速度 */
    private float ballX, ballY, ballVX, ballVY;
    /** 打砖块：球是否已发射 */
    private boolean ballLaunched;
    /** 打砖块：炮台X */
    private float paddleX;
    /** 打砖块：剩余砖块数 */
    private int bricksRemaining;

    /** 找零钱：目标金额 */
    private int changeTarget;
    /** 找零钱：贪心最优解 */
    private int[] greedyCounts;
    /** 找零钱：玩家已放入的纸币数 */
    private int[] playerCounts;
    /** 找零钱：纸币面额 */
    private static final int[] BILL_VALUES = {1, 2, 5, 10};
    /** 找零钱：纸币颜色 */
    private static final int[] BILL_COLORS = {0xFF88AA66, 0xFF6688CC, 0xFFCC8844, 0xFFCC6644};

    // ══════════════════════════════════════════════ 新18个小游戏字段
    // 贪吃蛇
    private final List<int[]> snakeBody = new ArrayList<>(); private int snakeDir, snakeNextDir, snakeFoodX, snakeFoodY, snakeTimer, snakeEaten;
    private final List<int[]> snakeObs = new ArrayList<>(); // 贪吃蛇障碍
    // 扫雷
    private int[][] mineGrid, mineRevealed, mineFlags; private int mineW = 5, mineH = 5, mineCount, minesLeft;
    // Simon Says
    private final List<Integer> simonSeq = new ArrayList<>(); private int simonStep, simonPlayerIdx, simonFlash, simonTimer;
    // 2048
    private int[][] grid2048; private boolean moved2048;
    // 接蛋
    private final List<Egg> eggs = new ArrayList<>(); private float basketX; private int eggsCaught, eggsMissed, eggSpawnT;
    // 颜色分类
    private int[][] colorTubes; private int colorSelected;
    // 猜数字
    private int guessSecret, guessLow, guessHigh, guessCount; private String guessInput = "";
    // 反应测试
    private int reactState, reactTimer, reactSuccess; private long reactStart, reactElapsed; private boolean reactFailed;
    // 连连看
    private int[][] linkGrid; private int linkSelX = -1, linkSelY = -1;
    // 俄罗斯方块
    private int[][] tetrisBoard; private int tetrisPiece, tetrisRot, tetrisPX, tetrisPY, tetrisTimer, tetrisLines;
    // 翻牌配对
    private int[] memCards, memRevealed; private int memSel1 = -1, memSel2 = -1, memFound, memWait;
    // 接管道
    private int[][] pipeGrid; private int pipeW = 5, pipeH = 4;
    // 点灯
    private boolean[][] lightGrid; private int lightsSize = 5;
    // 24点
    private int[] game24Nums; private int game24Sel1 = -1, game24Sel2 = -1;
    // 迷宫
    private int[][] mazeGrid; private int mazePX, mazePY, mazeEX, mazeEY;
    // 天平
    private int scaleLeft, scaleRight, scaleWeight, scaleTarget;
    // 华容道 (4列×5行, 0=空 1=曹操2×2 2=竖将 3=横将 4=兵)
    private int[][] klotskiGrid; private int klotskiSelR=-1,klotskiSelC=-1; private double klotskiStartX,klotskiStartY;

    /** 成功动画：>=0 表示已完成，正在播放成功反馈，到达时长后关闭。 */
    private int successTicks = -1;
    private static final int SUCCESS_ANIM_TICKS = 16;

    public SimpleQuestMinigameScreen(BlockPos questPos, Runnable onSuccess, Mode mode) {
        super(Component.translatable("minigame.starrailexpress." + mode.id));
        this.onSuccess = onSuccess;
        this.mode = mode;
    }

    private Component tr(String key) {
        return Component.translatable("minigame.starrailexpress." + key);
    }

    private Component tr(String key, Object... args) {
        return Component.translatable("minigame.starrailexpress." + key, args);
    }

    private Component modeText(String suffix) {
        return Component.translatable("minigame.starrailexpress." + mode.id + "." + suffix);
    }

    private Component modeText(String suffix, Object... args) {
        return Component.translatable("minigame.starrailexpress." + mode.id + "." + suffix, args);
    }

    @Override
    protected void init() {
        super.init();
        pieces.clear();
        dots.clear();
        targets.clear();
        notes.clear();
        connections.clear();
        selectedIndices.clear();
        draggedPiece = null;
        mouseHeld = false;
        selectedWire = -1;
        selectedKnob = -1;
        selectedTopWire = -1;
        progress = 0;
        successCount = 0;
        nextIndex = 0;
        holdTicks = 0;
        clearTicks = 0;
        animationTicks = 0;
        bulletActive = false;
        falling = false;
        armed = false;
        valueA = 0.5f;
        valueB = 0.5f;
        velocity = 0.035f;
        spawnTimer = 20;
        introTicks = 0;
        valveTotalRotation = 0;
        valveAngle = 0;
        valveDragging = false;
        sliceStartY = -1;
        sliceStartY = -1;
        pipePairs.clear();
        pipesPassed = 0;
        birdAlive = true;
        birdY = panelTop() + 80;
        birdVelocity = 0;
        fruitList.clear();
        fruitSliced = 0;
        fruitSpawnTimer = 30;
        mice.clear();
        miceCaught = 0;
        mouseSpawnTimer = 30;
        bricks.clear();
        ballLaunched = false;
        paddleX = width / 2f;
        snakeBody.clear(); snakeObs.clear(); snakeDir = 0; snakeNextDir = 0;
        simonSeq.clear(); simonStep = 0; simonPlayerIdx = 0; simonFlash = -1; simonClicked = null;
        grid2048 = null; eggs.clear(); basketX = width / 2f; eggsCaught = 0; eggsMissed = 0;
        colorTubes = null; colorSelected = -1;
        guessSecret = 0; guessLow = 1; guessHigh = 100; guessCount = 0; guessInput = "";
        reactState = 0; reactTimer = 0; reactStart = 0;
        linkGrid = null; linkSelX = -1; linkSelY = -1;
        tetrisBoard = null; tetrisTimer = 0; tetrisLines = 0;
        memCards = null; memRevealed = null; memSel1 = -1; memSel2 = -1; memFound = 0; memWait = 0;
        pipeGrid = null; lightGrid = null; game24Nums = null; game24Sel1 = -1; game24Sel2 = -1;
        mazeGrid = null; scaleTarget = 0; scaleWeight = 0; scaleLeft = 0; scaleRight = 0;
        klotskiGrid = null; klotskiSelR = -1; klotskiSelC = -1;
        mineGrid = null; mineRevealed = null; mineFlags = null; minesLeft = 0;
        prevMouseX = 0;
        prevMouseY = 0;
        typingInput.setLength(0);
        typingError = false;
        setupMode();
    }

    private void setupMode() {
        int left = panelLeft();
        int top = panelTop();
        switch (mode) {
            case REACTOR_TEMPERATURE -> {
                targetTemp = 110 + rng.nextInt(11);
                currentTemp = 140 + rng.nextInt(11);
            }
            case BOX_SORT -> {
                int y = top + 200;
                for (int shape = 0; shape < 3; shape++) {
                    int count = 1 + rng.nextInt(2);
                    for (int i = 0; i < count; i++) {
                        pieces.add(new Piece(shapeLabel(shape), shape, shapeColor(shape),
                                left + 90 + shape * 90 + i * 30, y, shape));
                    }
                }
                Collections.shuffle(pieces, rng);
            }
            case WIRE_CONNECT -> connections.addAll(List.of(-1, -1, -1));
            case CLEAN_DEBRIS -> {
                for (int i = 0; i < 12; i++) {
                    dots.add(new Dot(left + 70 + rng.nextInt(290), top + 55 + rng.nextInt(145), 12, i % 5));
                }
            }
            case SHOOTING -> {
                bulletX = width / 2f;
                bulletY = top + 224;
            }
            case WIRE_TUNING -> {
                valueA = rng.nextFloat();
                valueB = rng.nextFloat();
                wireTargetA = rng.nextFloat();
                wireTargetB = rng.nextFloat();
            }
            case SIGNAL_CALIBRATION -> {
                valueA = 0.15f;
                valueB = 0.85f;
            }
            case SHAPE_MATCH -> {
                for (int shape = 0; shape < 4; shape++) {
                    targets.add(new Target(left + 75 + shape * 90, top + 75 + (shape % 2) * 45, shape));
                    pieces.add(new Piece(shapeLabel(shape), shape, shapeColor(shape),
                            left + 80 + shape * 78, top + 205, shape));
                }
                Collections.shuffle(pieces, rng);
            }
            case CLEAN_STAINS -> {
                for (int i = 0; i < 8; i++) {
                    Dot d = new Dot(left + 70 + rng.nextInt(290), top + 55 + rng.nextInt(145), 15, 4);
                    d.life = 20;
                    dots.add(d);
                }
                pieces.add(new Piece(label("sponge"), 1, 0xFFFFFF88, left + 185, top + 215, -1));
            }
            case TRASH_RECYCLE -> {
                // 可回收: 玻璃瓶、纸张、铁锭。 其他垃圾: 苹果、毒马铃薯、火药
                ItemStack[] items = {GLASS_BOTTLE, IRON_INGOT, PAPER, APPLE, POISONOUS_POTATO, GUNPOWDER};
                String[] names = {"label.bottle", "label.iron", "label.paper", "label.apple", "label.potato", "label.dust"};
                for (int i = 0; i < items.length; i++) {
                    Piece p = new Piece(Component.translatable("minigame.starrailexpress." + names[i]),
                            i % 3, i < 3 ? BLUE : YELLOW,
                            left + 45 + i * 60, top + 200, i < 3 ? 0 : 1);
                    p.item = items[i];
                    pieces.add(p);
                }
            }
            case ITEM_CHECKLIST -> {
                List<String> ids = new ArrayList<>(List.of("key", "wire", "cup", "chip", "tape", "gear", "pen", "hammer"));
                Collections.shuffle(ids, rng);
                // 随机选取3个为所需物品
                selectedIndices.clear();
                List<Integer> pool = new ArrayList<>();
                for (int i = 0; i < ids.size(); i++) pool.add(i);
                Collections.shuffle(pool, rng);
                for (int i = 0; i < 3; i++) selectedIndices.add(pool.get(i));
                // 所有物品随机打乱，不标注颜色
                for (int i = 0; i < ids.size(); i++) {
                    pieces.add(new Piece(label(ids.get(i)), 1, 0xFF6B7890,
                            left + 240 + (i % 2) * 80, top + 55 + (i / 2) * 42, selectedIndices.contains(i) ? 1 : 0));
                }
            }
            case SWIPE_CARD -> pieces.add(new Piece(label("card"), 1, 0xFF66BBFF, left + 65, top + 110, 1));
            case PLAY_MUSIC -> {
                correctRecord = rng.nextInt(4);
                for (int i = 0; i < 4; i++) {
                    Piece p = new Piece(tr("label.disc", i + 1), 0, i == correctRecord ? GREEN : 0xFF7E6BEF,
                            left + 80 + i * 70, top + 205, i == correctRecord ? 1 : 0);
                    p.item = MUSIC_DISCS[i];
                    pieces.add(p);
                }
            }
            case RHYTHM -> spawnTimer = 20;
            case LIGHT_CANDLES -> {
                for (int i = 0; i < 3; i++) {
                    Dot candle = new Dot(left + 120 + i * 90, top + 120, 0, 0);
                    candle.life = 0;
                    dots.add(candle);
                }
                pieces.add(new Piece(label("flint"), 2, 0xFFAAAAAA, left + 190, top + 215, -1));
            }
            case WHACK_MOLE -> spawnMole();
            case PUZZLE -> {
                // 随机打乱图案映射
                List<Integer> targets = new ArrayList<>();
                for (int i = 0; i < 9; i++) targets.add(i);
                Collections.shuffle(targets, rng);
                // 拼图板在右侧，散落拼图在左侧
                for (int i = 0; i < 9; i++) {
                    float sx = left + 30 + (i % 3) * 58 + rng.nextInt(8);
                    float sy = top + 80 + (i / 3) * 58 + rng.nextInt(8);
                    pieces.add(new Piece(Component.literal(""), 1, 0xFF6AA6FF, sx, sy, targets.get(i)));
                }
                Collections.shuffle(pieces, rng);
            }
            case STORAGE -> {
                ItemStack[] items = {new ItemStack(Items.BOOK), new ItemStack(Items.GLASS_BOTTLE),
                        new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.LEATHER_BOOTS),
                        new ItemStack(Items.MAP), new ItemStack(Items.OAK_PLANKS)};
                for (int i = 0; i < items.length; i++) {
                    Piece p = new Piece(Component.literal(""), i % 4, shapeColor(i % 4),
                            left + 55 + i * 58, top + 80 + rng.nextInt(80), 1);
                    p.item = items[i];
                    pieces.add(p);
                }
            }
            case THREE_CARDS -> {
                highlightedCard = rng.nextInt(3);
                for (int i = 0; i < 3; i++) selectedIndices.add(i);
            }
            case BREAK_JAR -> pieces.add(new Piece(label("jar"), 0, 0xFFC98C58, width / 2f - 18, top + 205, 1));
            case ZONE_CALIBRATION -> {
                progress = 35;
                targetTemp = 46 + rng.nextInt(12);
            }
            case WATER_VALVE -> {
                valveClockwise = rng.nextBoolean();
                valveTotalRotation = 0;
                valveAngle = 0;
            }
            case TYPING -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 10; i++) {
                    sb.append((char) ('A' + rng.nextInt(26)));
                }
                typingTarget = sb.toString();
                // EditBox 在 init() 中通过 addRenderableWidget 添加
            }
            case PIPE_BIRD -> {
                birdY = panelTop() + 80;
                birdVelocity = 0;
                pipePairs.clear();
                pipesPassed = 0;
                birdAlive = true;
            }
            case MAKE_CHANGE -> {
                pieces.clear();
                changeTarget = 9 + rng.nextInt(32); // 9~40
                greedyCounts = new int[4];
                playerCounts = new int[4];
                int remaining = changeTarget;
                for (int i = 3; i >= 0; i--) {
                    greedyCounts[i] = remaining / BILL_VALUES[i];
                    remaining %= BILL_VALUES[i];
                }
                // 4张纸币可拖动，位于面板中央
                for (int i = 0; i < 4; i++) {
                    Component label = Component.literal("¥" + BILL_VALUES[i]);
                    pieces.add(new Piece(label, 1, BILL_COLORS[i],
                            left + 60 + i * 82, top + 160, i));
                }
            }
            case BRICK_BREAKER -> {
                bricks.clear();
                int count = 2 + rng.nextInt(4); // 2~5 (减少10个)
                bricksRemaining = count;
                int[] colors = {RED, 0xFF4ACB73, BLUE, YELLOW, 0xFFFF8C42, 0xFFAA66FF};
                for (int i = 0; i < count; i++) {
                    int bx = left + 20 + rng.nextInt(PANEL_W - 80);
                    int by = top + HEADER_H + 15 + rng.nextInt(70);
                    bricks.add(new Brick(bx, by, 42 + rng.nextInt(16), 14, colors[rng.nextInt(colors.length)]));
                }
                ballX = width / 2f;
                ballY = top + PANEL_H - 30;
                ballVX = 0;
                ballVY = 0;
                ballLaunched = false;
                paddleX = width / 2f;
            }
            case MOUSE_WHACK -> {
                mice.clear();
                miceCaught = 0;
                mouseSpawnTimer = 30;
            }
            case FRUIT_NINJA -> {
                fruitList.clear();
                fruitSliced = 0;
                fruitSpawnTimer = 20;
                prevMouseX = 0;
                prevMouseY = 0;
                try {
                    bombItem = new ItemStack(org.agmas.noellesroles.init.ModItems.BOMB);
                } catch (Exception e) {
                    bombItem = new ItemStack(Items.TNT);
                }
            }
            case SNAKE -> setupSnake();
            case MINESWEEPER -> setupMinesweeper();
            case SIMON_SAYS -> setupSimon();
            case GAME_2048 -> setup2048();
            case CATCH_EGGS -> { eggs.clear(); eggsCaught=0; eggsMissed=0; eggSpawnT=30; basketX=width/2f; }
            case COLOR_SORT -> setupColorSort();
            case GUESS_NUMBER -> setupGuessNumber();
            case REACTION_TEST -> { reactState=0; reactTimer=0; reactElapsed=0; reactFailed=false; }
            case LINK_MATCH -> setupLinkMatch();
            case TETRIS -> setupTetris();
            case MEMORY_MATCH -> setupMemMatch();
            case PIPE_CONNECT -> setupPipeConnect();
            case LIGHTS_OUT -> setupLightsOut();
            case GAME_24 -> setupGame24();
            case MAZE -> setupMaze();
            case BALANCE_SCALE -> setupBalanceScale();
            case KLOTSKI -> setupKlotski();
            default -> {
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        uiTicks++;
        if (introTicks < INTRO_TICKS) introTicks++;
        if (successTicks >= 0) {
            successTicks++;
            if (successTicks >= SUCCESS_ANIM_TICKS) {
                onClose();
            }
            return;
        }
        switch (mode) {
            case HOLD_BUTTON -> {
                if (mouseHeld && inCircle(lastMouseX, lastMouseY, width / 2.0, panelTop() + 130.0, 38)) {
                    holdTicks++;
                    progress = Math.min(100, holdTicks);
                    if (holdTicks >= 100) complete();
                }
            }
            case SHOOTING -> tickShooting();
            case WIRE_TUNING -> {
                float clarity = 1f - (Math.abs(valueA - wireTargetA) + Math.abs(valueB - wireTargetB));
                if (clarity > 0.94f) complete();
            }
            case SIGNAL_CALIBRATION -> {
                if (Math.abs(valueA - 0.30f) < 0.025f && Math.abs(valueB - 0.70f) < 0.025f) complete();
            }
            case CLEAN_STAINS -> {
                if (draggedPiece != null) {
                    for (Dot dot : dots) {
                        if (dot.life > 0 && distance(draggedPiece.x + 18, draggedPiece.y + 18, dot.x, dot.y) < 32) {
                            dot.life -= 3;
                        }
                    }
                    if (dots.stream().allMatch(dot -> dot.life <= 0)) complete();
                }
            }
            case RHYTHM -> tickRhythm();
            case LIGHT_CANDLES -> tickCandles();
            case WHACK_MOLE -> {
                for (Dot d : dots) {
                    if (d.life > 0) d.life--;
                    if (d.life <= 0) d.active = false;
                }
                if (--spawnTimer <= 0) spawnMole();
            }
            case THREE_CARDS -> {
                animationTicks++;
                if (animationTicks > 50 && animationTicks % 22 == 0 && animationTicks < 150) {
                    // 随机选两个不同位置交换
                    int a = rng.nextInt(3);
                    int b;
                    do { b = rng.nextInt(3); } while (b == a);
                    Collections.swap(selectedIndices, a, b);
                    swapFromA = a;
                    swapFromB = b;
                    swapStartTick = animationTicks;
                }
            }
            case BREAK_JAR -> {
                if (falling && !pieces.isEmpty()) {
                    Piece jar = pieces.get(0);
                    fallingY += 1.8f;
                    jar.y += fallingY;
                    if (jar.y >= panelTop() + 210) complete();
                }
            }
            case ZONE_CALIBRATION -> {
                progress += mouseHeld && inRect(lastMouseX, lastMouseY, width / 2 - 42, panelTop() + 185, 84, 28) ? 2 : -1;
                progress = Mth.clamp(progress, 0, 100);
                if (Math.abs(progress - targetTemp) <= 7) {
                    clearTicks++;
                    if (clearTicks >= 100) complete();
                } else {
                    clearTicks = 0;
                }
            }
            case WATER_VALVE -> tickWaterValve();
            case PIPE_BIRD -> tickPipeBird();
            case FRUIT_NINJA -> tickFruitNinja();
            case MOUSE_WHACK -> tickMouseWhack();
            case BRICK_BREAKER -> tickBrickBreaker();
            case SNAKE -> tickSnake();
            case SIMON_SAYS -> tickSimon();
            case CATCH_EGGS -> tickCatchEggs();
            case COLOR_SORT -> tickColorSort();
            case REACTION_TEST -> tickReactionTest();
            case TETRIS -> tickTetris();
            case MEMORY_MATCH -> tickMemMatch();
            default -> {
            }
        }
    }

    private void tickShooting() {
        int left = panelLeft();
        int top = panelTop();
        if (--spawnTimer <= 0) {
            spawnTimer = 35 + rng.nextInt(35);
            targets.add(new Target(left - 30, top + 55 + rng.nextInt(55), rng.nextBoolean() ? 2.2f : 3.2f));
        }
        targets.forEach(target -> target.x += target.speed);
        targets.removeIf(target -> target.x > left + PANEL_W + 30);
        if (bulletActive) {
            bulletY -= 8;
            for (Target target : targets) {
                if (!target.hit && distance(bulletX, bulletY, target.x, target.y) < 18) {
                    target.hit = true;
                    bulletActive = false;
                    successCount++;
                    if (successCount >= 5) complete();
                    break;
                }
            }
            targets.removeIf(target -> target.hit);
            if (bulletY < top + 30) bulletActive = false;
        }
    }

    private void tickRhythm() {
        int top = panelTop();
        if (--spawnTimer <= 0) {
            spawnTimer = 34;
            notes.add(new Note(width / 2f - 90 + rng.nextInt(181), top + 36));
        }
        for (Note note : notes) note.y += 3.2f;
        notes.removeIf(note -> note.y > top + 230);
    }

    private void tickCandles() {
        if (draggedPiece == null) return;
        for (Dot candle : dots) {
            if (candle.life < 45 && distance(draggedPiece.x + 16, draggedPiece.y + 16, candle.x, candle.y) < 28) {
                candle.life++;
            }
        }
        if (dots.stream().allMatch(dot -> dot.life >= 45)) complete();
    }

    private void spawnMole() {
        int left = panelLeft();
        int top = panelTop();
        if (dots.isEmpty()) {
            for (int i = 0; i < 9; i++) {
                Dot dot = new Dot(left + 115 + (i % 3) * 90, top + 70 + (i / 3) * 50, 0, 0);
                dot.active = false;
                dot.life = 0;
                dots.add(dot);
            }
        }
        int count = 1 + rng.nextInt(2);
        int spawned = 0;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 9; i++) indices.add(i);
        Collections.shuffle(indices, rng);
        for (int idx : indices) {
            if (spawned >= count) break;
            Dot d = dots.get(idx);
            if (d.life <= 0) {
                d.active = true;
                d.life = 40;
                spawned++;
            }
        }
        spawnTimer = 40;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        hoverX = mouseX;
        hoverY = mouseY;
        int left = panelLeft();
        int top = panelTop();

        // 入场弹出动画：从面板中心轻微放大淡入
        float intro = MinigameUI.easeOut((introTicks + partialTick) / INTRO_TICKS);
        float scale = 0.82f + 0.18f * intro;
        float cx = width / 2f;
        float cy = top + PANEL_H / 2f;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-cx, -cy, 0);

        MinigameUI.panel(g, left, top, left + PANEL_W, top + PANEL_H, HEADER_H);
        g.drawCenteredString(font, title, width / 2, top + 8, WHITE);

        switch (mode) {
            case REACTOR_TEMPERATURE -> renderTemperature(g, left, top);
            case BOX_SORT -> renderBoxSort(g, left, top);
            case WIRE_CONNECT -> renderWireConnect(g, left, top);
            case HOLD_BUTTON -> renderHoldButton(g, left, top);
            case CLEAN_DEBRIS -> renderDebris(g, left, top);
            case SHOOTING -> renderShooting(g, left, top);
            case WIRE_TUNING -> renderWireTuning(g, left, top);
            case SIGNAL_CALIBRATION -> renderSignal(g, left, top);
            case SHAPE_MATCH -> renderShapeMatch(g, left, top);
            case SEQUENCE_BUTTONS -> renderSequence(g, left, top);
            case CLEAN_STAINS -> renderStains(g, left, top);
            case PULL_LEVER -> renderLever(g, left, top);
            case TRASH_RECYCLE -> renderTrash(g, left, top);
            case ITEM_CHECKLIST -> renderChecklist(g, left, top);
            case SWIPE_CARD -> renderSwipe(g, left, top);
            case PLAY_MUSIC -> renderMusic(g, left, top);
            case RHYTHM -> renderRhythm(g, left, top);
            case LIGHT_CANDLES -> renderCandles(g, left, top);
            case WHACK_MOLE -> renderMole(g, left, top);
            case PUZZLE -> renderPuzzle(g, left, top);
            case STORAGE -> renderStorage(g, left, top);
            case SLICE_FOOD -> renderSlice(g, left, top);
            case THREE_CARDS -> renderCards(g, left, top);
            case BREAK_JAR -> renderJar(g, left, top);
            case ZONE_CALIBRATION -> renderZone(g, left, top);
            case WATER_VALVE -> renderWaterValve(g, left, top);
            case TYPING -> renderTyping(g, left, top);
            case PIPE_BIRD -> renderPipeBird(g, left, top);
            case FRUIT_NINJA -> renderFruitNinja(g, left, top);
            case MOUSE_WHACK -> renderMouseWhack(g, left, top);
            case BRICK_BREAKER -> renderBrickBreaker(g, left, top);
            case MAKE_CHANGE -> renderMakeChange(g, left, top);
            case SNAKE -> renderSnake(g, left, top);
            case MINESWEEPER -> renderMinesweeper(g, left, top);
            case SIMON_SAYS -> renderSimon(g, left, top);
            case GAME_2048 -> render2048(g, left, top);
            case CATCH_EGGS -> renderCatchEggs(g, left, top);
            case COLOR_SORT -> renderColorSort(g, left, top);
            case GUESS_NUMBER -> { g.drawCenteredString(font,Component.literal(guessLow+" - "+guessHigh),width/2,top+50,WHITE); g.drawCenteredString(font,tr("guess_number.hint"),width/2,top+75,MUTED); int ix=left+120; MinigameUI.roundRect(g,ix,top+95,ix+190,top+120,4,0xFF334455); g.drawString(font,guessInput,ix+6,top+101,WHITE); int bx=left+(PANEL_W-60)/2; MinigameUI.roundRect(g,bx,top+125,bx+60,top+148,4,GREEN); g.drawCenteredString(font,Component.literal("确定"),bx+30,top+129,0xFF101010); g.drawCenteredString(font,tr("guess_number.count",guessCount),width/2,top+155,MUTED); }
            case REACTION_TEST -> { int col=reactState==0?MUTED:reactState==1?YELLOW:reactFailed?RED:GREEN; String txt=reactState==0?"等待触发...":reactState==1?"现在点击!":reactFailed?"失败! "+reactElapsed+"ms":reactElapsed+"ms"; g.drawCenteredString(font,Component.literal(txt),width/2,top+100,col); if(reactState==1){int bx=width/2-40,by=top+120;MinigameUI.roundRect(g,bx,by,bx+80,by+30,6,GREEN);g.drawCenteredString(font,Component.literal("点我!"),width/2,by+8,0xFF101010);} g.drawCenteredString(font,tr("common.hits",reactSuccess,3),width/2,top+6,WHITE); }
            case LINK_MATCH -> renderLinkMatch(g, left, top);
            case TETRIS -> renderTetris(g, left, top);
            case MEMORY_MATCH -> renderMemMatch(g, left, top);
            case PIPE_CONNECT -> renderPipeConnect(g, left, top);
            case LIGHTS_OUT -> renderLightsOut(g, left, top);
            case GAME_24 -> renderGame24(g, left, top);
            case MAZE -> renderMaze(g, left, top);
            case BALANCE_SCALE -> renderBalanceScale(g, left, top);
            case KLOTSKI -> renderKlotski(g, left, top);
        }
        g.pose().popPose();

        // 打老鼠：刷子光标
        if (mode == Mode.MOUSE_WHACK) {
            g.renderItem(BRUSH, mouseX - 12, mouseY - 12);
        }

        super.render(g, mouseX, mouseY, partialTick);
        if (successTicks >= 0) {
            renderSuccessOverlay(g, partialTick);
        }
    }

    /** 通用完成反馈：绿色闪光 + 扩散光环 + 居中“完成”。 */
    private void renderSuccessOverlay(GuiGraphics g, float partialTick) {
        float t = Math.min(1.0F, (successTicks + partialTick) / SUCCESS_ANIM_TICKS);
        int cx = width / 2;
        int cy = height / 2;
        int flashAlpha = (int) (110 * (1.0F - t));
        g.fill(0, 0, width, height, (flashAlpha << 24) | 0x40FF60);
        int ringR = (int) (10 + t * 70);
        int ringAlpha = (int) (220 * (1.0F - t));
        MinigameUI.ring(g, cx, cy, ringR, 2, (ringAlpha << 24) | 0x7CFCA0);
        int textY = cy - 6 - (int) (t * 10);
        int textAlpha = (int) (255 * (1.0F - t * 0.4F));
        g.drawCenteredString(font, Component.literal("✔ ").append(tr("common.done")), cx, textY,
                (textAlpha << 24) | 0xFFFFFF);
    }

    private void renderTemperature(GuiGraphics g, int left, int top) {
        int barX = left + 135;
        int barY = top + 58;
        int barH = 145;
        g.drawCenteredString(font, tr("reactor_temperature.target", targetTemp), barX + 18, barY - 18, YELLOW);
        g.drawCenteredString(font, tr("reactor_temperature.current", currentTemp), barX + 18, barY + barH + 10, WHITE);
        MinigameUI.roundRect(g, barX, barY, barX + 36, barY + barH, 6, PANEL_DARK);
        int fillH = Mth.clamp((currentTemp - 90) * barH / 70, 0, barH);
        int tempColor = MinigameUI.lerpColor(GREEN, RED, Mth.clamp(Math.abs(currentTemp - targetTemp) / 35f, 0f, 1f));
        if (fillH > 4) MinigameUI.roundRect(g, barX + 4, barY + barH - fillH, barX + 32, barY + barH - 4, 4, tempColor);
        int targetY = barY + barH - Mth.clamp((targetTemp - 90) * barH / 70, 0, barH);
        g.fill(barX - 8, targetY - 1, barX + 44, targetY + 1, YELLOW);
        drawButton(g, left + 260, top + 72, 72, 44, Component.literal("▲"));
        drawButton(g, left + 260, top + 142, 72, 44, Component.literal("▼"));
    }

    private void renderBoxSort(GuiGraphics g, int left, int top) {
        for (int i = 0; i < 3; i++) {
            int y = top + 48 + i * 45;
            MinigameUI.roundRect(g, left + 70, y, left + 350, y + 34, 6, PANEL_DARK);
            drawShape(g, i, left + 92, y + 17, 13, 0x55FFFFFF, true);
            g.drawString(font, shapeLabel(i), left + 118, y + 13, WHITE);
        }
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderWireConnect(GuiGraphics g, int left, int top) {
        int[] topColors = {RED, YELLOW, BLUE};
        int[] bottomColors = {BLUE, RED, YELLOW};
        float glow = MinigameUI.pulse(uiTicks, 0.25f);
        for (int i = 0; i < 3; i++) {
            int tx = left + 130 + i * 85;
            int bx = left + 130 + i * 85;
            if (selectedTopWire == i) drawCircle(g, tx, top + 72, 16, MinigameUI.withAlpha(topColors[i], 0.3f + 0.3f * glow));
            drawCircle(g, tx, top + 72, 12, topColors[i]);
            drawCircle(g, bx, top + 192, 12, bottomColors[i]);
            int b = connections.get(i);
            if (b >= 0) drawSteppedLine(g, tx, top + 84, left + 130 + b * 85, top + 180, topColors[i]);
        }
        g.drawCenteredString(font, modeText("hint"), width / 2, top + 34, MUTED);
    }

    private void renderHoldButton(GuiGraphics g, int left, int top) {
        float glow = MinigameUI.pulse(uiTicks, 0.3f);
        int ringColor = MinigameUI.withAlpha(RED, 0.25f + 0.25f * glow);
        drawCircle(g, width / 2, top + 130, 46, ringColor);
        drawCircle(g, width / 2, top + 130, 40, 0xFFB72E3A);
        drawCircle(g, width / 2, top + 130, 26, mouseHeld ? 0xFFFF5A5A : RED);
        MinigameUI.progressBar(g, left + 100, top + 200, 230, 14, progress / 100f, GREEN);
    }

    private void renderDebris(GuiGraphics g, int left, int top) {
        MinigameUI.roundRect(g, left + 58, top + 42, left + 372, top + 215, 8, 0xFF3C4658);
        for (Dot dot : dots) {
            ItemStack item = switch (dot.kind % 5) {
                case 0 -> DIRT;
                case 1 -> STONE;
                case 2 -> new ItemStack(Items.STICK);
                case 3 -> new ItemStack(Items.STRING);
                default -> new ItemStack(Items.BONE);
            };
            g.renderItem(item, dot.x, dot.y);
        }
        g.drawCenteredString(font, modeText("hint"), width / 2, top + 226, MUTED);
    }

    private void renderShooting(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, tr("common.hits", successCount, 5), width / 2, top + 34, WHITE);
        targets.forEach(target -> {
            drawCircle(g, Math.round(target.x), Math.round(target.y), 14, RED);
            drawCircle(g, Math.round(target.x), Math.round(target.y), 7, WHITE);
        });
        g.fill(width / 2 - 24, top + 220, width / 2 + 24, top + 240, 0xFF7D8797);
        g.fill(width / 2 - 5, top + 192, width / 2 + 5, top + 220, 0xFFB8C0CC);
        if (bulletActive) drawCircle(g, Math.round(bulletX), Math.round(bulletY), 5, YELLOW);
    }

    private void renderWireTuning(GuiGraphics g, int left, int top) {
        float clarity = Mth.clamp(1f - (Math.abs(valueA - wireTargetA) + Math.abs(valueB - wireTargetB)), 0f, 1f);
        int tvTop = top + 135;
        // 电视机壳
        MinigameUI.roundRect(g, left + 120, tvTop, left + 310, tvTop + 90, 6, 0xFF04070C);
        // 电视画面：background.png，清晰度控制透明度
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, clarity);
        g.blit(BG_TEXTURE, left + 130, tvTop + 8, 0, 0, 170, 74, 170, 74);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        g.drawCenteredString(font, modeText("hint"), width / 2, top + 55, MUTED);
        // 两个电线枢轴点（电视机上边）
        int[] pivotX = {left + 180, left + 250};
        int pivotY = tvTop;
        for (int i = 0; i < 2; i++) {
            drawCircle(g, pivotX[i], pivotY, 10, i == 0 ? 0xFF553333 : 0xFF333355);
            drawCircle(g, pivotX[i], pivotY, 5, i == 0 ? RED : BLUE);
        }
        int wireLen = 90;
        for (int i = 0; i < 2; i++) {
            float value = i == 0 ? valueA : valueB;
            // value 0→1 映射到角度 PI→0（顺时针从左到右，范围0°~180°）
            double angle = Math.PI * (1 - value);
            int px = pivotX[i];
            int py = pivotY;
            int ex = px - (int) (Math.cos(angle) * wireLen);
            int ey = py - (int) (Math.sin(angle) * wireLen);
            // 直线电线
            int c = i == 0 ? RED : BLUE;
            int steps = Math.max(1, (int) Math.sqrt((ex - px) * (ex - px) + (ey - py) * (ey - py)));
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                int lx = px + (int) ((ex - px) * t);
                int ly = py + (int) ((ey - py) * t);
                g.fill(lx - 1, ly - 1, lx + 2, ly + 2, c);
            }
            // 目标线（灰色虚线提示）
            double targetAngle = Math.PI * (1 - (i == 0 ? wireTargetA : wireTargetB));
            int tx = px - (int) (Math.cos(targetAngle) * wireLen);
            int ty = py - (int) (Math.sin(targetAngle) * wireLen);
            int tsteps = Math.max(1, (int) Math.sqrt((tx - px) * (tx - px) + (ty - py) * (ty - py)));
            for (int s = 0; s <= tsteps; s += 4) {
                float t = (float) s / tsteps;
                int lx = px + (int) ((tx - px) * t);
                int ly = py + (int) ((ty - py) * t);
                g.fill(lx - 1, ly - 1, lx + 1, ly + 1, 0x66FFFFFF);
            }
            // 端点旋钮
            drawCircle(g, ex, ey, 8, i == 0 ? RED : BLUE);
        }
    }

    private void renderSignal(GuiGraphics g, int left, int top) {
        int cx = width / 2;
        int cy = top + 155;
        drawArcTicks(g, cx, cy, 115);
        drawStraightRadial(g, cx, cy, 100, 0.30f, 0x88FFFFFF);
        drawStraightRadial(g, cx, cy, 100, 0.70f, 0x88FFFFFF);
        drawStraightRadial(g, cx, cy, 100, valueA, RED);
        drawStraightRadial(g, cx, cy, 100, valueB, RED);
        drawKnob(g, left + 160, top + 218, valueA);
        drawKnob(g, left + 270, top + 218, valueB);
    }

    private void drawStraightRadial(GuiGraphics g, int cx, int cy, int length, float value, int color) {
        double angle = Math.PI * (1f - value);
        int ex = cx - (int) (Math.cos(angle) * length);
        int ey = cy - (int) (Math.sin(angle) * length);
        int steps = Math.max(1, (int) Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy)));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            int px = cx + (int) ((ex - cx) * t);
            int py = cy + (int) ((ey - cy) * t);
            g.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
    }

    private void renderShapeMatch(GuiGraphics g, int left, int top) {
        float glow = MinigameUI.pulse(uiTicks, 0.18f);
        for (Target target : targets) {
            drawShape(g, target.shape, Math.round(target.x), Math.round(target.y), 21,
                    MinigameUI.withAlpha(0xFFFFFFFF, 0.18f + 0.14f * glow), true);
        }
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderSequence(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, modeText("hint"), width / 2, top + 38, MUTED);
        for (int i = 0; i < 6; i++) {
            int x = left + 90 + (i % 3) * 100;
            int y = top + 75 + (i / 3) * 75;
            int color = i < nextIndex ? GREEN
                    : i == nextIndex ? YELLOW
                    : 0xFF596579;
            MinigameUI.roundRect(g, x, y, x + 56, y + 42, 6, color);
            MinigameUI.roundBorder(g, x, y, x + 56, y + 42, 6, 2, i == nextIndex ? 0xFFCCAA44 : 0xFF445566);
            g.drawCenteredString(font, Component.literal(String.valueOf(i + 1)), x + 28, y + 17, 0xFF10131A);
        }
    }

    private void renderStains(GuiGraphics g, int left, int top) {
        MinigameUI.roundRect(g, left + 58, top + 42, left + 372, top + 205, 8, 0xFFB8B0A0);
        for (Dot dot : dots) {
            if (dot.life > 0) drawCircle(g, dot.x, dot.y, 5 + dot.life / 5, 0x663A2A20);
        }
        // 使用原版海绵材质渲染海绵 Piece
        for (Piece piece : pieces) {
            if (piece == draggedPiece) {
                g.pose().pushPose();
                g.pose().translate((int) piece.x + 16, (int) piece.y + 16, 0);
                g.pose().scale(2f, 2f, 1f);
                g.renderItem(SPONGE, -8, -8);
                g.pose().popPose();
            } else {
                g.pose().pushPose();
                g.pose().translate((int) piece.x + 16, (int) piece.y + 16, 0);
                g.pose().scale(2f, 2f, 1f);
                g.renderItem(SPONGE, -8, -8);
                g.pose().popPose();
            }
        }
    }

    private void renderLever(GuiGraphics g, int left, int top) {
        int railX = width / 2;
        int minY = top + 68;
        int maxY = top + 205;
        MinigameUI.roundRect(g, railX - 12, minY, railX + 12, maxY, 6, 0xFF1E2530);
        MinigameUI.roundBorder(g, railX - 12, minY, railX + 12, maxY, 6, 2, 0xFF445566);
        for (int y = minY + 10; y < maxY; y += 15) {
            g.fill(railX - 4, y, railX + 4, y + 3, 0xFF38485A);
        }
        int knobY = Math.round(Mth.lerp(valueA, minY, maxY));
        drawCircle(g, railX, knobY, 22, 0xFF556A80);
        drawCircle(g, railX, knobY, 18, RED);
        drawCircle(g, railX, knobY, 8, 0xFF882222);
        g.drawCenteredString(font, modeText("hint"), width / 2, top + 35, MUTED);
    }

    private void renderTrash(GuiGraphics g, int left, int top) {
        // 可回收垃圾桶
        MinigameUI.roundRect(g, left + 68, top + 55, left + 188, top + 155, 8, 0xFF2E5C91);
        drawCircle(g, left + 128, top + 105, 28, 0x2266AA66);
        g.drawString(font, "♻", left + 112, top + 148, GREEN);
        g.drawCenteredString(font, modeText("recycle"), left + 128, top + 80, 0x88CCCCFF);
        // 其他垃圾桶
        MinigameUI.roundRect(g, left + 242, top + 55, left + 362, top + 155, 8, 0xFF5E4242);
        drawCircle(g, left + 302, top + 105, 28, 0x22CC6666);
        g.drawString(font, "🗑", left + 286, top + 148, RED);
        g.drawCenteredString(font, modeText("other"), left + 302, top + 80, 0x88FFCCCC);
        for (Piece piece : pieces) {
            g.renderItem(piece.item, (int) piece.x, (int) piece.y);
            if (piece.placed) continue;
            String name = piece.label.getString();
            g.drawString(font, name, (int) piece.x, (int) piece.y + 20, 0xAAFFFFFF);
        }
    }

    private void renderChecklist(GuiGraphics g, int left, int top) {
        g.drawString(font, modeText("needed"), left + 65, top + 50, YELLOW);
        for (int idx : selectedIndices) {
            if (idx < pieces.size()) {
                Piece p = pieces.get(idx);
                g.drawString(font, tr("item_checklist.entry", p.label), left + 65, top + 73 + selectedIndices.indexOf(idx) * 24, WHITE);
            }
        }
        g.drawString(font, modeText("items"), left + 230, top + 50, MUTED);
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            int x = left + 230 + (i % 2) * 80;
            int y = top + 73 + (i / 2) * 34;
            p.x = x;
            p.y = y;
            int color = p.placed ? 0xFF444444 : 0xFF445566;
            MinigameUI.roundRect(g, x, y, x + 64, y + 28, 5, color);
            MinigameUI.roundBorder(g, x, y, x + 64, y + 28, 5, 1, p.placed ? GREEN : 0xFF667788);
            g.drawString(font, p.label, x + 8, y + 10, p.placed ? GREEN : WHITE);
        }
    }

    private void renderSwipe(GuiGraphics g, int left, int top) {
        MinigameUI.roundRect(g, left + 260, top + 62, left + 340, top + 205, 6, 0xFF1A2535);
        MinigameUI.roundBorder(g, left + 260, top + 62, left + 340, top + 205, 6, 2, 0xFF334455);
        g.fill(left + 270, top + 82, left + 330, top + 92, armed ? GREEN : 0xFF0D1118);
        g.drawCenteredString(font, modeText("hint"), left + 300, top + 215, MUTED);
        for (Piece p : pieces) {
            MinigameUI.roundRect(g, (int) p.x, (int) p.y, (int) p.x + 48, (int) p.y + 28, 5, 0xFF4488CC);
            g.drawCenteredString(font, p.label, (int) p.x + 24, (int) p.y + 10, WHITE);
        }
    }

    private void renderMusic(GuiGraphics g, int left, int top) {
        float glow = MinigameUI.pulse(uiTicks, 0.12f);
        int cx = width / 2;
        int cy = top + 105;
        // 留声机外圈发光
        drawCircle(g, cx, cy, 58, MinigameUI.withAlpha(0xFF7E6BEF, 0.2f + 0.2f * glow));
        // 唱盘
        drawCircle(g, cx, cy, 55, 0xFF38445A);
        // 中心轴
        drawCircle(g, cx, cy, 18, 0xFF0F1520);
        // 唱臂
        g.fill(cx + 36, top + 72, cx + 95, top + 82, 0xFFB9C2D1);
        g.drawCenteredString(font, modeText("hint", correctRecord + 1), width / 2, top + 34, YELLOW);
        for (Piece piece : pieces) {
            if (piece.placed) {
                drawCircle(g, (int) piece.x + 20, (int) piece.y + 20, 10, GREEN);
            }
            g.renderItem(piece.item != null ? piece.item : MUSIC_DISCS[0], (int) piece.x, (int) piece.y);
            g.drawString(font, piece.label, (int) piece.x, (int) piece.y + 20, WHITE);
        }
    }

    private void renderRhythm(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, tr("common.hits", successCount, 5), width / 2, top + 35, WHITE);
        int lineY = top + 195;
        MinigameUI.roundRect(g, left + 70, lineY, left + 360, lineY + 3, 1, YELLOW);
        for (Note note : notes) {
            boolean near = Math.abs(note.y - lineY) < 17;
            drawCircle(g, Math.round(note.x), Math.round(note.y), near ? 12 : 10, near ? GREEN : BLUE);
        }
    }

    private void renderCandles(GuiGraphics g, int left, int top) {
        for (Dot candle : dots) {
            g.pose().pushPose();
            g.pose().translate(candle.x, candle.y - 16, 0);
            g.pose().scale(1.8f, 1.8f, 1f);
            g.renderItem(CANDLE, -8, -8);
            g.pose().popPose();
            if (candle.life >= 45) {
                float glow = MinigameUI.pulse(uiTicks + candle.x, 0.4f);
                drawCircle(g, candle.x, candle.y - 48, 11, MinigameUI.withAlpha(YELLOW, 0.5f + 0.4f * glow));
                drawCircle(g, candle.x, candle.y - 48, 7, 0xFFFFE08A);
            } else {
                MinigameUI.progressBar(g, candle.x - 18, candle.y + 16, 36, 4, candle.life / 45f, YELLOW);
            }
        }
        for (Piece piece : pieces) {
            g.renderItem(FLINT_AND_STEEL, (int) piece.x, (int) piece.y + 4);
        }
    }

    private void renderMole(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, tr("common.hits", successCount, 5), width / 2, top + 36, WHITE);
        for (Dot dot : dots) {
            drawCircle(g, dot.x, dot.y, 24, 0xFF3A2B1F);
            if (dot.active && dot.life > 0) {
                int rise = Math.min(16, (40 - dot.life) * 2 / 5);
                drawCircle(g, dot.x, dot.y - rise, 16, 0xFFB77B46);
                drawCircle(g, dot.x + 5, dot.y - rise - 4, 4, WHITE);
                drawCircle(g, dot.x + 6, dot.y - rise - 4, 2, 0xFF111111);
            }
        }
    }

    private void renderPuzzle(GuiGraphics g, int left, int top) {
        int boardX = left + 245;
        int boardY = top + 80;
        int pieceSize = 36;
        // 右侧拼图板：3x3 目标格
        for (int i = 0; i < 9; i++) {
            int bx = boardX + (i % 3) * 42;
            int by = boardY + (i / 3) * 42;
            MinigameUI.roundRect(g, bx, by, bx + 38, by + 38, 4, 0x553F5574);
        }
        // 渲染已放置的拼图（在正确位置）
        for (Piece piece : pieces) {
            if (piece.placed) {
                int bx = boardX + (piece.target % 3) * 42;
                int by = boardY + (piece.target / 3) * 42;
                int srcU = (piece.target % 3) * 40;
                int srcV = (piece.target / 3) * 40;
                g.blit(BG_TEXTURE, bx + 1, by + 1, pieceSize, pieceSize, srcU, srcV, 40, 40, 120, 120);
            }
        }
        // 渲染散落拼图（在左侧）
        for (Piece piece : pieces) {
            if (piece.placed) continue;
            int px = Math.round(piece.x);
            int py = Math.round(piece.y);
            int srcU = (piece.target % 3) * 40;
            int srcV = (piece.target / 3) * 40;
            g.blit(BG_TEXTURE, px, py, pieceSize, pieceSize, srcU, srcV, 40, 40, 120, 120);
        }
    }

    private void renderStorage(GuiGraphics g, int left, int top) {
        MinigameUI.roundRect(g, left + 115, top + 190, left + 315, top + 245, 8, 0xFF5C6F88);
        g.drawCenteredString(font, modeText("bag"), width / 2, top + 211, WHITE);
        for (Piece piece : pieces) {
            if (piece.item != null) {
                int px = Math.round(piece.x);
                int py = Math.round(piece.y);
                if (piece == draggedPiece) {
                    g.renderItem(piece.item, px + 4, py + 4);
                } else {
                    g.renderItem(piece.item, px, py);
                }
            }
        }
    }

    private void renderSlice(GuiGraphics g, int left, int top) {
        int cx = width / 2;
        int cy = top + 135;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(60f));
        g.pose().scale(10f, 10f, 1f);
        g.renderItem(CARROT, -8, -8);
        g.pose().popPose();
        // 引导线渲染在胡萝卜上方
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        for (int i = 1; i <= 4; i++) {
            int x = left + 105 + i * 44;
            int color = i <= progress ? GREEN : 0x66FF6666;
            g.fill(x - 1, top + 90, x + 1, top + 180, color);
        }
        g.pose().popPose();
        g.drawCenteredString(font, modeText("hint"), width / 2, top + 195, MUTED);
    }

    private void renderCards(GuiGraphics g, int left, int top) {
        boolean faceDown = animationTicks > 50;
        g.drawCenteredString(font, faceDown ? modeText("pick") : modeText("remember"), width / 2, top + 36, MUTED);
        // 每个位置独立计算动画偏移
        for (int i = 0; i < 3; i++) {
            int baseX = left + 108 + i * 90;
            int baseY = top + 92;
            int drawX = baseX;
            int drawY = baseY;
            // 计算独立交换动画
            if (swapFromA >= 0 && swapFromB >= 0 && swapStartTick > 0) {
                int elapsed = animationTicks - swapStartTick;
                if (elapsed >= 0 && elapsed < 22) {
                    float t = Mth.clamp(elapsed / 22f, 0f, 1f);
                    if (i == swapFromA || i == swapFromB) {
                        int other = (i == swapFromA) ? swapFromB : swapFromA;
                        int otherX = left + 108 + other * 90;
                        int fromX = otherX;
                        int toX = baseX;
                        // 左→右上弧，右→左下弧
                        float arcHeight = (float) Math.sin(t * Math.PI) * 40;
                        if (fromX < toX) {
                            // 从左到右 → 上弧
                            drawY = baseY - (int) arcHeight;
                        } else {
                            // 从右到左 → 下弧
                            drawY = baseY + (int) arcHeight;
                        }
                        drawX = (int) (fromX + (toX - fromX) * t);
                    }
                }
            }
            int slot = selectedIndices.get(i);
            int color = !faceDown && slot == highlightedCard ? YELLOW : 0xFFE8EEF8;
            MinigameUI.roundRect(g, drawX, drawY, drawX + 58, drawY + 82, 6, faceDown ? 0xFF344667 : color);
            MinigameUI.roundBorder(g, drawX, drawY, drawX + 58, drawY + 82, 6, 2, 0xFF667788);
            g.drawCenteredString(font, Component.literal(faceDown ? "?" : String.valueOf(slot + 1)), drawX + 29, drawY + 36, 0xFF10131A);
        }
    }

    private void renderJar(GuiGraphics g, int left, int top) {
        MinigameUI.roundRect(g, left + 40, top + 218, left + 390, top + 242, 6, 0xFF485464);
        g.drawCenteredString(font, modeText("hint"), width / 2, top + 35, MUTED);
        for (Piece piece : pieces) {
            g.pose().pushPose();
            g.pose().translate((int) piece.x + 16, (int) piece.y + 16, 0);
            g.pose().scale(4f, 4f, 1f);
            g.renderItem(DECORATED_POT, -8, -8);
            g.pose().popPose();
        }
    }

    private void renderZone(GuiGraphics g, int left, int top) {
        int barX = left + 85;
        int barY = top + 105;
        int barW = 260;
        boolean inZone = Math.abs(progress - targetTemp) <= 7;
        MinigameUI.progressBar(g, barX, barY, barW, 18, progress / 100f, inZone ? GREEN : BLUE);
        int zx = barX + (targetTemp - 7) * barW / 100;
        int zw = 14 * barW / 100;
        g.fill(zx, barY - 8, zx + zw, barY + 26, 0x664ACB73);
        g.drawCenteredString(font, tr("zone_calibration.hold", clearTicks / 20), width / 2, top + 145, WHITE);
        drawButton(g, width / 2 - 42, top + 185, 84, 28, modeText("push"));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 扫雷右键直接处理，不走拦截
        if(button==1&&mode==Mode.MINESWEEPER){clickMine((int)mouseX,(int)mouseY,1);return true;}
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        mouseHeld = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        switch (mode) {
            case REACTOR_TEMPERATURE -> clickTemperature(mouseX, mouseY);
            case WIRE_CONNECT -> clickWire(mouseX, mouseY);
            case CLEAN_DEBRIS -> clickDebris(mouseX, mouseY);
            case SHOOTING -> fireBullet();
            case SEQUENCE_BUTTONS -> clickSequence(mouseX, mouseY);
            case PULL_LEVER -> selectedKnob = 0;
            case ITEM_CHECKLIST -> clickChecklist(mouseX, mouseY);
            case SWIPE_CARD, PLAY_MUSIC, BOX_SORT, SHAPE_MATCH, TRASH_RECYCLE, CLEAN_STAINS, LIGHT_CANDLES,
                    PUZZLE, STORAGE, BREAK_JAR -> beginDrag(mouseX, mouseY);
            case WIRE_TUNING -> selectedWire = nearestWire(mouseX, mouseY);
            case SIGNAL_CALIBRATION -> selectedKnob = nearestKnob(mouseX, mouseY);
            case WHACK_MOLE -> clickMole(mouseX, mouseY);
            case SLICE_FOOD -> { /* handled by mouseDragged */ }
            case THREE_CARDS -> clickCard(mouseX, mouseY);
            case WATER_VALVE -> clickWaterValve(mouseX, mouseY);
            case TYPING -> clickTyping(mouseX, mouseY);
            case PIPE_BIRD -> flapBird();
            case MOUSE_WHACK -> clickMouseWhack(mouseX, mouseY);
            case BRICK_BREAKER -> {
                if (!ballLaunched) {
                    ballLaunched = true;
                    ballVX = (rng.nextFloat() - 0.5f) * 2f;
                    ballVY = -4.5f;
                }
            }
            case MAKE_CHANGE -> beginDrag(mouseX, mouseY);
            case MINESWEEPER -> clickMine((int)mouseX,(int)mouseY,0);
            case SIMON_SAYS -> { int cx=panelLeft()+PANEL_W/2,cy=panelTop()+PANEL_H/2-20; for(int i=0;i<5;i++){int x=cx-130+i*65; if(inRect(mouseX,mouseY,x,cy,55,55)){clickSimon(i);return true;}} }
            case GUESS_NUMBER -> { int bx=panelLeft()+(PANEL_W-60)/2; if(inRect(mouseX,mouseY,bx,panelTop()+125,60,23)){try{int g=Integer.parseInt(guessInput);guessCount++;if(g==guessSecret)complete();else if(g<guessSecret)guessLow=g+1;else guessHigh=g-1;}catch(Exception e){}guessInput="";} }
            case REACTION_TEST -> { if(reactState==1){long ms=System.currentTimeMillis()-reactStart;reactElapsed=ms;if(ms<1500){reactState=2;reactSuccess++;if(reactSuccess>=3)complete();}else{reactFailed=true;reactState=2;}} }
            case LINK_MATCH -> { int cs=32,ox=panelLeft()+30,oy=panelTop()+40,cy=(int)((mouseY-oy)/cs),cx=(int)((mouseX-ox)/cs); if(cy>=0&&cy<4&&cx>=0&&cx<6){if(linkSelX<0){linkSelX=cx;linkSelY=cy;}else{if(linkGrid[linkSelY][linkSelX]==linkGrid[cy][cx]&&canLink(linkSelX,linkSelY,cx,cy)){linkGrid[cy][cx]=-1;linkGrid[linkSelY][linkSelX]=-1;int left=0;for(int r=0;r<4;r++)for(int c=0;c<6;c++)if(linkGrid[r][c]>=0)left++;if(left==0)complete();}linkSelX=-1;linkSelY=-1;}}}
            case TETRIS -> { int nr=(tetrisRot+1)%4; if(canPlaceTetris(tetrisPX,tetrisPY,tetrisPiece,nr))tetrisRot=nr; }
            case MEMORY_MATCH -> { if(memWait>0)return true; int cs=38,ox=panelLeft()+70,oy=panelTop()+30,idx=(int)((mouseY-oy)/cs)*4+(int)((mouseX-ox)/cs); if(idx>=0&&idx<16&&memRevealed[idx]==0&&memCards[idx]>=0){if(memSel1<0){memSel1=idx;memRevealed[idx]=1;}else if(memSel2<0&&idx!=memSel1){memSel2=idx;memRevealed[idx]=1;if(memCards[memSel1]==memCards[memSel2]){memCards[memSel1]=-1;memCards[memSel2]=-1;memFound++;memSel1=-1;memSel2=-1;if(memFound>=8)complete();}else memWait=20;}} }
            case PIPE_CONNECT -> { int cs=40,ox=panelLeft()+(PANEL_W-pipeW*cs)/2,oy=panelTop()+40; int c=(int)((mouseX-ox)/cs),r=(int)((mouseY-oy)/cs); if(r>=0&&r<pipeH&&c>=0&&c<pipeW)pipeGrid[r][c]=((pipeGrid[r][c]&15)<<1|(pipeGrid[r][c]>>3))&15; }
            case LIGHTS_OUT -> { int cs=38,ox=panelLeft()+100,oy=panelTop()+40; int cc=(int)((mouseX-ox)/cs),rr=(int)((mouseY-oy)/cs); if(rr>=0&&rr<lightsSize&&cc>=0&&cc<lightsSize){toggleLight(rr,cc);toggleLight(rr,cc+1);toggleLight(rr,cc-1);toggleLight(rr+1,cc);toggleLight(rr-1,cc);} }
            case GAME_24 -> click24(mouseX, mouseY);
            case BALANCE_SCALE -> clickScale(mouseX, mouseY);
            case COLOR_SORT -> clickColorSort(mouseX, mouseY);
            case KLOTSKI -> clickKlotski(mouseX, mouseY);
            default -> {
            }
        }
        return true;
    }

    private void clickTemperature(double mouseX, double mouseY) {
        int left = panelLeft();
        int top = panelTop();
        if (inRect(mouseX, mouseY, left + 260, top + 72, 72, 44)) currentTemp++;
        if (inRect(mouseX, mouseY, left + 260, top + 142, 72, 44)) currentTemp--;
        if (currentTemp == targetTemp) complete();
    }

    private void clickWire(double mouseX, double mouseY) {
        int left = panelLeft();
        int top = panelTop();
        for (int i = 0; i < 3; i++) {
            if (inCircle(mouseX, mouseY, left + 130 + i * 85, top + 72, 16)) {
                selectedTopWire = i;
                return;
            }
            if (selectedTopWire >= 0 && inCircle(mouseX, mouseY, left + 130 + i * 85, top + 192, 16)) {
                int[] bottomToTop = {2, 0, 1};
                if (bottomToTop[i] == selectedTopWire) {
                    connections.set(selectedTopWire, i);
                    selectedTopWire = -1;
                    if (connections.stream().allMatch(v -> v >= 0)) complete();
                }
            }
        }
    }

    private void clickDebris(double mouseX, double mouseY) {
        dots.removeIf(dot -> distance((float) mouseX, (float) mouseY, dot.x, dot.y) < dot.radius + 8);
        if (dots.isEmpty()) complete();
    }

    private void fireBullet() {
        if (!bulletActive) {
            bulletX = width / 2f;
            bulletY = panelTop() + 192;
            bulletActive = true;
        }
    }

    private void clickSequence(double mouseX, double mouseY) {
        int left = panelLeft();
        int top = panelTop();
        for (int i = 0; i < 6; i++) {
            int x = left + 90 + (i % 3) * 100;
            int y = top + 75 + (i / 3) * 75;
            if (inRect(mouseX, mouseY, x, y, 56, 42)) {
                if (i == nextIndex) {
                    nextIndex++;
                    if (nextIndex >= 6) complete();
                } else {
                    nextIndex = 0;
                }
            }
        }
    }

    private void clickChecklist(double mouseX, double mouseY) {
        for (Piece p : pieces) {
            if (!p.placed && inRect(mouseX, mouseY, p.x, p.y, 64, 28)) {
                if (p.target == 1) {
                    p.placed = true;
                    if (pieces.stream().filter(piece -> piece.target == 1).allMatch(piece -> piece.placed)) complete();
                }
                return;
            }
        }
    }

    private void clickMole(double mouseX, double mouseY) {
        for (Dot dot : dots) {
            if (dot.active && dot.life > 0 && inCircle(mouseX, mouseY, dot.x, dot.y, 24)) {
                dot.life = 0;
                dot.active = false;
                successCount++;
                if (successCount >= 5) complete();
                return;
            }
        }
    }

    private float sliceStartY = -1;

    private void dragSlice(double mouseY) {
        int top = panelTop();
        int left = panelLeft();
        int targetIdx = progress + 1;
        for (int i = 1; i <= 4; i++) {
            int x = left + 105 + i * 44;
            // 仅处理下一条未切的线
            if (i != targetIdx || Math.abs(lastMouseX - x) > 16) continue;
            // 从上到下划动
            if (sliceStartY < 0 && mouseY <= top + 100) {
                sliceStartY = (float) mouseY; // 开始划
            } else if (sliceStartY >= 0 && mouseY >= top + 170 && mouseY > sliceStartY + 20) {
                progress++;
                sliceStartY = -1;
                if (progress >= 4) complete();
                return;
            }
        }
    }

    private void clickCard(double mouseX, double mouseY) {
        if (animationTicks <= 150) return;
        int left = panelLeft();
        int top = panelTop();
        for (int i = 0; i < 3; i++) {
            int x = left + 108 + i * 90;
            int y = top + 92;
            if (inRect(mouseX, mouseY, x, y, 58, 82)) {
                if (selectedIndices.get(i) == highlightedCard) complete();
                else init();
                return;
            }
        }
    }

    private void beginDrag(double mouseX, double mouseY) {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            Piece p = pieces.get(i);
            if (!p.placed && inRect(mouseX, mouseY, p.x, p.y, 42, 32)) {
                draggedPiece = p;
                dragOffsetX = (float) mouseX - p.x;
                dragOffsetY = (float) mouseY - p.y;
                return;
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (mode == Mode.FRUIT_NINJA) {
            prevMouseX = lastMouseX;
            prevMouseY = lastMouseY;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (mode == Mode.FRUIT_NINJA) {
            checkFruitSlice();
            return true;
        }
        if (draggedPiece != null) {
            draggedPiece.x = (float) mouseX - dragOffsetX;
            draggedPiece.y = (float) mouseY - dragOffsetY;
            if (mode == Mode.SWIPE_CARD && mouseX >= panelLeft() + 275 && mouseX <= panelLeft() + 340
                    && mouseY <= panelTop() + 95) {
                armed = true;
            }
            return true;
        }
        if (mode == Mode.PULL_LEVER && selectedKnob == 0) {
            valueA = Mth.clamp(((float) mouseY - (panelTop() + 68)) / 137f, 0f, 1f);
            if (valueA > 0.95f) complete();
            return true;
        }
        if (mode == Mode.WIRE_TUNING && selectedWire >= 0) {
            int top = panelTop();
            int[] pivotX = {panelLeft() + 180, panelLeft() + 250};
            int pivotY = top + 135;
            int px = pivotX[selectedWire];
            double angle = Math.atan2(pivotY - mouseY, px - mouseX);
            float v = Mth.clamp((float) (1.0 - angle / Math.PI), 0f, 1f);
            if (selectedWire == 0) valueA = v;
            else valueB = v;
            return true;
        }
        if (mode == Mode.SIGNAL_CALIBRATION && selectedKnob >= 0) {
            int kx = panelLeft() + (selectedKnob == 0 ? 160 : 270);
            int ky = panelTop() + 218;
            float angle = (float) Math.atan2(ky - mouseY, mouseX - kx);
            float normalized = Mth.clamp((float) ((Math.PI - angle) / Math.PI), 0f, 1f);
            if (selectedKnob == 0) valueA = normalized;
            else valueB = normalized;
            return true;
        }
        if (mode == Mode.WATER_VALVE && valveDragging) {
            dragWaterValve(mouseX, mouseY);
            return true;
        }
        if (mode == Mode.SLICE_FOOD && mouseHeld) {
            dragSlice(mouseY);
            return true;
        }
        // 华容道鼠标滑动
        if (mode == Mode.KLOTSKI && klotskiSelR >= 0) {
            double dx=mouseX-klotskiStartX,dy=mouseY-klotskiStartY;
            int cs=44; // cell+gap (42+2)
            if(Math.abs(dx)>=cs/2||Math.abs(dy)>=cs/2){
                if(Math.abs(dx)>=Math.abs(dy))moveKlotskiBlock(klotskiSelR,klotskiSelC,0,dx>0?1:-1);
                else moveKlotskiBlock(klotskiSelR,klotskiSelC,dy>0?1:-1,0);
                klotskiStartX=mouseX;klotskiStartY=mouseY;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseHeld = false;
        selectedWire = -1;
        selectedKnob = -1;
        valveDragging = false;
        sliceStartY = -1;
        sliceStartY = -1;
        if (draggedPiece != null) {
            releaseDrag(mouseX, mouseY);
            draggedPiece = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void releaseDrag(double mouseX, double mouseY) {
        int left = panelLeft();
        int top = panelTop();
        Piece p = draggedPiece;
        switch (mode) {
            case BOX_SORT -> {
                int row = Math.round(((float) mouseY - (top + 65)) / 45f);
                if (row == p.target && mouseX >= left + 70 && mouseX <= left + 350) placePiece(p, left + 310, top + 50 + row * 45);
            }
            case SHAPE_MATCH -> {
                for (Target target : targets) {
                    if (target.shape == p.target && distance(p.x + 20, p.y + 16, target.x, target.y) < 34) {
                        placePiece(p, target.x - 20, target.y - 16);
                    }
                }
            }
            case TRASH_RECYCLE -> {
                if (p.target == 0 && inRect(mouseX, mouseY, left + 78, top + 55, 110, 90)) placePiece(p, left + 110, top + 112);
                if (p.target == 1 && inRect(mouseX, mouseY, left + 242, top + 55, 110, 90)) placePiece(p, left + 274, top + 112);
            }
            case SWIPE_CARD -> {
                if (armed && mouseX >= left + 275 && mouseX <= left + 340 && mouseY >= top + 175) complete();
            }
            case PLAY_MUSIC -> {
                if (p.target == 1 && inCircle(mouseX, mouseY, width / 2, top + 105, 55)) complete();
            }
            case PUZZLE -> {
                int boardX = left + 245;
                int boardY = top + 80;
                int sx = boardX + (p.target % 3) * 42;
                int sy = boardY + (p.target / 3) * 42;
                if (distance(p.x, p.y, sx + 19, sy + 19) < 36) placePiece(p, sx, sy);
            }
            case STORAGE -> {
                if (inRect(mouseX, mouseY, left + 115, top + 190, 200, 55)) placePiece(p, left + 160 + rng.nextInt(85), top + 202);
            }
            case BREAK_JAR -> {
                if (p.y < top + 80) {
                    falling = true;
                    fallingY = 0;
                }
            }
            case MAKE_CHANGE -> {
                dropChangeBill(p, mouseX, mouseY);
                // 纸币回到原位
                p.x = panelLeft() + 60 + p.target * 82;
                p.y = top + 160;
            }
            default -> {
            }
        }
        if (isPlacementMode() && !pieces.isEmpty() && pieces.stream().allMatch(piece -> piece.placed || piece.target < 0)) {
            complete();
        }
    }

    private boolean isPlacementMode() {
        return mode == Mode.BOX_SORT || mode == Mode.SHAPE_MATCH || mode == Mode.TRASH_RECYCLE
                || mode == Mode.PUZZLE || mode == Mode.STORAGE;
    }

    private void placePiece(Piece p, float x, float y) {
        p.x = x;
        p.y = y;
        p.placed = true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (mode == Mode.SHOOTING && keyCode == GLFW.GLFW_KEY_SPACE) {
            fireBullet();
            return true;
        }
        if (mode == Mode.PIPE_BIRD && keyCode == GLFW.GLFW_KEY_SPACE) {
            flapBird();
            return true;
        }
        if (mode == Mode.RHYTHM && keyCode == GLFW.GLFW_KEY_SPACE) {
            int lineY = panelTop() + 195;
            for (Note note : notes) {
                if (Math.abs(note.y - lineY) < 17) {
                    notes.remove(note);
                    successCount++;
                    if (successCount >= 5) complete();
                    return true;
                }
            }
            return true;
        }
        if ((mode == Mode.TYPING || mode == Mode.GUESS_NUMBER) && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (mode == Mode.TYPING && typingInput.length() > 0) { typingInput.setLength(typingInput.length() - 1); typingError = false; }
            else if (mode == Mode.GUESS_NUMBER && guessInput.length() > 0) guessInput = guessInput.substring(0, guessInput.length() - 1);
            return true;
        }
        if (mode == Mode.TYPING && keyCode == GLFW.GLFW_KEY_ENTER) {
            checkTyping();
            return true;
        }
        if (mode == Mode.GUESS_NUMBER && keyCode == GLFW.GLFW_KEY_ENTER) {
            try{int g=Integer.parseInt(guessInput);guessCount++;if(g==guessSecret)complete();else if(g<guessSecret)guessLow=g+1;else guessHigh=g-1;}catch(Exception e){}
            guessInput="";
            return true;
        }
        // 贪吃蛇方向键
        if (mode == Mode.SNAKE) {
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) snakeNextDir = 0;
            else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) snakeNextDir = 1;
            else if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) snakeNextDir = 2;
            else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) snakeNextDir = 3;
            return true;
        }
        // 2048方向键
        if (mode == Mode.GAME_2048 && grid2048 != null) {
            int d = -1;
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) d = 0;
            else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) d = 1;
            else if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) d = 2;
            else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) d = 3;
            if (d >= 0 && slide2048(d)) { spawn2048(); for(int r=0;r<4;r++)for(int c=0;c<4;c++)if(grid2048[r][c]>=32)complete(); }
            return true;
        }
        // 俄罗斯方块方向键
        if (mode == Mode.TETRIS && tetrisBoard != null) {
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) { if (canPlaceTetris(tetrisPX - 1, tetrisPY, tetrisPiece, tetrisRot)) tetrisPX--; }
            else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) { if (canPlaceTetris(tetrisPX + 1, tetrisPY, tetrisPiece, tetrisRot)) tetrisPX++; }
            else if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) { if (canPlaceTetris(tetrisPX, tetrisPY + 1, tetrisPiece, tetrisRot)) tetrisPY++; }
            else if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_SPACE) { int nr = (tetrisRot + 1) % 4; if (canPlaceTetris(tetrisPX, tetrisPY, tetrisPiece, nr)) tetrisRot = nr; }
            return true;
        }
        // 迷宫方向键
        if (mode == Mode.MAZE && mazeGrid != null) {
            int nx = mazePX, ny = mazePY;
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) ny--;
            else if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) ny++;
            else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) nx--;
            else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) nx++;
            if (nx >= 0 && ny >= 0 && ny < mazeGrid.length && nx < mazeGrid[0].length && mazeGrid[ny][nx] == 0) { mazePX = nx; mazePY = ny; if (mazePX == mazeEX && mazePY == mazeEY) complete(); }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (mode == Mode.TYPING && chr >= ' ' && typingInput.length() < 10) {
            typingInput.append(Character.toUpperCase(chr));
            typingError = false;
            return true;
        }
        if (mode == Mode.GUESS_NUMBER && chr >= '0' && chr <= '9' && guessInput.length() < 3) {
            guessInput += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (mode == Mode.FRUIT_NINJA) {
            prevMouseX = lastMouseX;
            prevMouseY = lastMouseY;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (mode == Mode.FRUIT_NINJA) {
            checkFruitSlice();
        }
        if (mode == Mode.SWIPE_CARD && mouseX >= panelLeft() + 275 && mouseX <= panelLeft() + 340 && mouseY <= panelTop() + 95) {
            armed = true;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 透明背景，不做全屏暗化
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelLeft() {
        return (width - PANEL_W) / 2;
    }

    private int panelTop() {
        return (height - PANEL_H) / 2;
    }

    private void complete() {
        if (successTicks >= 0) {
            return;
        }
        onSuccess.run();
        successTicks = 0;
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.6F, 1.3F);
        }
    }

    private int nearestWire(double mouseX, double mouseY) {
        int top = panelTop();
        int[] pivotX = {panelLeft() + 180, panelLeft() + 250};
        int pivotY = top + 135;
        for (int i = 0; i < 2; i++) {
            float value = i == 0 ? valueA : valueB;
            double angle = Math.PI * (1 - value);
            int ex = pivotX[i] - (int) (Math.cos(angle) * 90);
            int ey = pivotY - (int) (Math.sin(angle) * 90);
            if (inCircle(mouseX, mouseY, ex, ey, 16)) return i;
            if (inCircle(mouseX, mouseY, pivotX[i], pivotY, 14)) return i;
        }
        return -1;
    }

    private int nearestKnob(double mouseX, double mouseY) {
        if (inCircle(mouseX, mouseY, panelLeft() + 160, panelTop() + 218, 22)) return 0;
        if (inCircle(mouseX, mouseY, panelLeft() + 270, panelTop() + 218, 22)) return 1;
        return -1;
    }

    private void drawPiece(GuiGraphics g, Piece p) {
        if (p == null) return;
        boolean dragging = p == draggedPiece;
        if (dragging) {
            // 拖拽时投影，给出抓取的层次感
            if (p.shape >= 0 && p.shape <= 3) {
                drawShape(g, p.shape, Math.round(p.x + 22), Math.round(p.y + 19), 16, 0x40000000, false);
            } else {
                MinigameUI.roundRect(g, Math.round(p.x + 2), Math.round(p.y + 3), Math.round(p.x + 44), Math.round(p.y + 35), 5, 0x40000000);
            }
        }
        if (p.shape >= 0 && p.shape <= 3) {
            drawShape(g, p.shape, Math.round(p.x + 20), Math.round(p.y + 16), 16, p.color, false);
        } else {
            MinigameUI.roundRect(g, Math.round(p.x), Math.round(p.y), Math.round(p.x + 42), Math.round(p.y + 32), 5, p.color);
        }
        g.drawCenteredString(font, p.label, Math.round(p.x + 21), Math.round(p.y + 37), WHITE);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component label) {
        boolean hover = inRect(hoverX, hoverY, x, y, w, h);
        MinigameUI.roundRect(g, x, y, x + w, y + h, 6, hover ? 0xFF5C7698 : 0xFF3C4F68);
        MinigameUI.roundRect(g, x + 2, y + 2, x + w - 2, y + h - 2, 5, hover ? 0xFF85A0C4 : 0xFF6F86A6);
        g.drawCenteredString(font, label, x + w / 2, y + h / 2 - 4, 0xFF10131A);
    }

    private void drawShape(GuiGraphics g, int shape, int cx, int cy, int r, int color, boolean outline) {
        if (shape == 0) {
            drawCircle(g, cx, cy, r, color);
            if (outline) drawCircle(g, cx, cy, Math.max(2, r - 4), PANEL);
        } else if (shape == 1) {
            MinigameUI.roundRect(g, cx - r, cy - r, cx + r, cy + r, 4, color);
            if (outline) MinigameUI.roundRect(g, cx - r + 4, cy - r + 4, cx + r - 4, cy + r - 4, 3, PANEL);
        } else if (shape == 2) {
            for (int row = 0; row < r * 2; row++) {
                int half = row / 2;
                g.fill(cx - half, cy - r + row, cx + half, cy - r + row + 1, color);
            }
        } else {
            g.fill(cx - r, cy - 4, cx + r, cy + 4, color);
            g.fill(cx - 4, cy - r, cx + 4, cy + r, color);
        }
    }

    private void drawCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        MinigameUI.filledCircle(g, cx, cy, r, color);
    }

    private void drawSteppedLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int midY = (y1 + y2) / 2;
        g.fill(Math.min(x1, x2), midY - 1, Math.max(x1, x2) + 2, midY + 1, color);
        g.fill(x1 - 1, Math.min(y1, midY), x1 + 1, Math.max(y1, midY) + 1, color);
        g.fill(x2 - 1, Math.min(y2, midY), x2 + 1, Math.max(y2, midY) + 1, color);
    }

    private void drawRadial(GuiGraphics g, int cx, int cy, int length, float value, int color) {
        double angle = Math.PI * (1f - value);
        int x = cx + (int) (Math.cos(angle) * length);
        int y = cy - (int) (Math.sin(angle) * length);
        drawSteppedLine(g, cx, cy, x, y, color);
    }

    private void drawArcTicks(GuiGraphics g, int cx, int cy, int radius) {
        for (int i = 0; i <= 20; i++) {
            double angle = Math.PI * i / 20.0;
            int x = cx - (int) (Math.cos(angle) * radius);
            int y = cy - (int) (Math.sin(angle) * radius);
            drawCircle(g, x, y, 2, 0x66FFFFFF);
        }
    }

    private void drawKnob(GuiGraphics g, int cx, int cy, float value) {
        drawCircle(g, cx, cy, 19, 0xFF526178);
        drawRadial(g, cx, cy, 16, value, YELLOW);
    }

    private void drawDebris(GuiGraphics g, Dot dot) {
        int color = switch (dot.kind) {
            case 0 -> 0xFF8E6D48;
            case 1 -> 0xFF6C7A89;
            case 2 -> 0xFF5FA05F;
            case 3 -> 0xFFB64E4E;
            default -> 0xFF8A78A8;
        };
        drawShape(g, dot.kind % 4, dot.x, dot.y, dot.radius, color, false);
    }

    // ══════════════════════════════════════════════
    // 打字小游戏
    // ══════════════════════════════════════════════

    private void clickTyping(double mouseX, double mouseY) {
        int top = panelTop();
        // 点击确认按钮
        if (inRect(mouseX, mouseY, width / 2 - 30, top + 185, 60, 24)) {
            checkTyping();
        }
    }

    private void checkTyping() {
        if (typingInput.toString().equals(typingTarget)) {
            complete();
        } else {
            typingError = true;
        }
    }

    private void renderTyping(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, modeText("hint"), width / 2, top + 36, MUTED);

        // 目标字符串显示
        for (int i = 0; i < 10; i++) {
            char c = typingTarget.charAt(i);
            int cx = left + 45 + i * 36;
            MinigameUI.roundRect(g, cx, top + 80, cx + 28, top + 108, 4, 0xFF2A3545);
            g.drawCenteredString(font, Component.literal(String.valueOf(c)), cx + 14, top + 93, YELLOW);
        }

        // 输入框
        int inputX = left + 45;
        int inputY = top + 125;
        int inputW = 352;
        int inputH = 28;
        MinigameUI.roundRect(g, inputX, inputY, inputX + inputW, inputY + inputH, 4,
                typingError ? 0x88603030 : 0xFF3C5066);

        String displayInput = typingInput.toString();
        for (int i = 0; i < 10; i++) {
            int cx = inputX + 12 + i * 34;
            char dc = i < displayInput.length() ? displayInput.charAt(i) : '_';
            boolean correct = i < typingTarget.length() && i < displayInput.length()
                    && displayInput.charAt(i) == typingTarget.charAt(i);
            int color = !typingError ? WHITE : (correct ? GREEN : RED);
            g.drawString(font, Component.literal(String.valueOf(dc)), cx, inputY + 9, color);
        }

        // 确认按钮
        drawButton(g, width / 2 - 30, top + 185, 60, 24, modeText("confirm"));
    }

    // ══════════════════════════════════════════════
    // 管道小鸟（Flappy Bird）
    // ══════════════════════════════════════════════

    private void flapBird() {
        if (!birdAlive) {
            // 死亡后点击重新开始
            init();
            return;
        }
        birdVelocity = -5.5f;
    }

    private void tickPipeBird() {
        if (!birdAlive) return;

        int top = panelTop();
        float gravity = 0.32f;

        // 重力
        birdVelocity += gravity;
        birdY += birdVelocity;

        // 边界检测
        if (birdY < top + HEADER_H + 4) {
            birdY = top + HEADER_H + 4;
            birdVelocity = 0;
        }
        if (birdY > top + PANEL_H - 10) {
            birdY = top + PANEL_H - 10;
            birdVelocity = 0;
        }

        // 生成管道
        if (pipePairs.isEmpty() || pipePairs.get(pipePairs.size() - 1).x < panelLeft() + 280) {
            spawnPipePair();
        }

        // 移动管道
        for (PipePair pp : pipePairs) {
            pp.x -= 3.5f;
        }

        // 移除超出屏幕的管道
        pipePairs.removeIf(pp -> pp.x < panelLeft() - 60);

        // 碰撞检测与通过计数
        int birdCx = width / 2 - 20;
        for (PipePair pp : pipePairs) {
            // 碰撞检测
            if (birdCx + 18 > pp.x && birdCx < pp.x + 30) {
                if (birdY - 12 < pp.topHeight || birdY + 12 > pp.bottomY) {
                    birdAlive = false;
                    return;
                }
            }
            // 通过管道计数
            if (!pp.passed && birdCx > pp.x + 30) {
                pp.passed = true;
                pipesPassed++;
                if (pipesPassed >= PIPES_REQUIRED) {
                    complete();
                    return;
                }
            }
        }
    }

    private void spawnPipePair() {
        int left = panelLeft();
        int top = panelTop();
        int gapSize = 88; // 较大间隙以降低难度
        int minGapY = top + HEADER_H + 30;
        int maxGapY = top + PANEL_H - 30 - gapSize;
        int gapY = minGapY + rng.nextInt(maxGapY - minGapY);
        PipePair pp = new PipePair();
        pp.x = left + PANEL_W + 10;
        pp.topHeight = gapY - top;
        pp.bottomY = gapY + gapSize;
        pp.passed = false;
        pipePairs.add(pp);
    }

    private void renderPipeBird(GuiGraphics g, int left, int top) {
        int cx = width / 2 - 20;

        if (!birdAlive) {
            g.drawCenteredString(font, tr("pipe_bird.dead"), width / 2, top + 70, RED);
            g.drawCenteredString(font, tr("pipe_bird.restart"), width / 2, top + 100, MUTED);
            // 仍绘制管道
            for (PipePair pp : pipePairs) {
                renderPipe(g, left, top, pp);
            }
            return;
        }

        // 提示分数
        g.drawCenteredString(font, tr("common.hits", pipesPassed, PIPES_REQUIRED), width / 2, top + 34, WHITE);

        // 绘制管道
        for (PipePair pp : pipePairs) {
            renderPipe(g, left, top, pp);
        }

        // 绘制鸟（简单的圆形+翅膀）
        int birdX = cx + 9;
        int birdYRound = Math.round(birdY);
        // 身体
        drawCircle(g, birdX, birdYRound, 12, 0xFFFFCC00);
        // 翅膀
        float wingFlap = (float) Math.sin(uiTicks * 0.5f) * 4f;
        if (birdVelocity < 0) wingFlap = -3;
        g.fill(birdX - 8, birdYRound - 8, birdX - 4, birdYRound + (int) wingFlap + 2, 0xFFFFAA00);
        // 眼睛
        drawCircle(g, birdX + 5, birdYRound - 4, 3, WHITE);
        drawCircle(g, birdX + 6, birdYRound - 4, 2, 0xFF111111);
    }

    private void renderPipe(GuiGraphics g, int left, int top, PipePair pp) {
        int pipeW = 30;
        // 上管道
        if (pp.topHeight > 0) {
            MinigameUI.roundRect(g, (int) pp.x, top + HEADER_H, (int) pp.x + pipeW, top + HEADER_H + pp.topHeight, 0,
                    0xFF3D8B37);
            // 管口
            g.fill((int) pp.x - 3, top + HEADER_H + pp.topHeight - 4, (int) pp.x + pipeW + 3,
                    top + HEADER_H + pp.topHeight, 0xFF2E6E28);
        }
        // 下管道
        if (pp.bottomY < top + PANEL_H) {
            MinigameUI.roundRect(g, (int) pp.x, pp.bottomY, (int) pp.x + pipeW, top + PANEL_H, 0, 0xFF3D8B37);
            // 管口
            g.fill((int) pp.x - 3, pp.bottomY, (int) pp.x + pipeW + 3, pp.bottomY + 4, 0xFF2E6E28);
        }
    }

    // ══════════════════════════════════════════════
    // 打老鼠
    // ══════════════════════════════════════════════

    private void tickMouseWhack() {
        int left = panelLeft();
        int top = panelTop();
        // 生成老鼠
        if (--mouseSpawnTimer <= 0) {
            mouseSpawnTimer = 35 + rng.nextInt(30);
            // 随机选方向：0=左→右 1=右→左 2=上→下 3=下→上
            int dir = rng.nextInt(4);
            int holeIdx = rng.nextInt(3);
            float fromX, fromY, toX, toY;
            switch (dir) {
                case 0: // 左→右
                    fromX = left + 10; toX = left + PANEL_W - 30;
                    fromY = toY = top + HEADER_H + 30 + holeIdx * 60;
                    break;
                case 1: // 右→左
                    fromX = left + PANEL_W - 30; toX = left + 10;
                    fromY = toY = top + HEADER_H + 30 + holeIdx * 60;
                    break;
                case 2: // 上→下
                    fromX = toX = left + 60 + holeIdx * 160;
                    fromY = top + HEADER_H + 5; toY = top + PANEL_H - 15;
                    break;
                default: // 下→上
                    fromX = toX = left + 60 + holeIdx * 160;
                    fromY = top + PANEL_H - 15; toY = top + HEADER_H + 5;
                    break;
            }
            float speed = 0.02f + rng.nextFloat() * 0.025f;
            mice.add(new RunningMouse(fromX, fromY, toX, toY, speed));
        }
        // 更新老鼠位置（每只老鼠速度不同）
        for (RunningMouse m : mice) m.progress += m.speed;
        mice.removeIf(m -> m.caught || m.progress >= 1f);
    }

    private void renderMouseWhack(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, tr("common.hits", miceCaught, MICE_TARGET), width / 2, top + 15, WHITE);
        // 绘制老鼠洞
        for (int i = 0; i < 3; i++) {
            // 左侧洞
            drawCircle(g, left + 10, top + HEADER_H + 30 + i * 60, 12, 0xFF2A1A10);
            // 右侧洞
            drawCircle(g, left + PANEL_W - 20, top + HEADER_H + 30 + i * 60, 12, 0xFF2A1A10);
            // 上侧洞
            drawCircle(g, left + 60 + i * 160, top + HEADER_H + 5, 12, 0xFF2A1A10);
            // 下侧洞
            drawCircle(g, left + 60 + i * 160, top + PANEL_H - 15, 12, 0xFF2A1A10);
        }
        // 绘制老鼠
        for (RunningMouse m : mice) {
            if (m.caught) continue;
            float mx = m.fromX + (m.toX - m.fromX) * m.progress;
            float my = m.fromY + (m.toY - m.fromY) * m.progress;
            g.pose().pushPose();
            g.pose().translate(mx, my, 0);
            // 水平移动时朝右，垂直移动时朝下
            if (Math.abs(m.toX - m.fromX) > Math.abs(m.toY - m.fromY)) {
                if (m.toX < m.fromX) g.pose().scale(-1f, 1f, 1f);
            }
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(new ItemStack(Items.BLACK_DYE), -8, -8);
            g.pose().popPose();
        }
    }

    private void clickMouseWhack(double mouseX, double mouseY) {
        for (RunningMouse m : mice) {
            if (m.caught) continue;
            float mx = m.fromX + (m.toX - m.fromX) * m.progress;
            float my = m.fromY + (m.toY - m.fromY) * m.progress;
            if (inCircle(mouseX, mouseY, mx, my, 16)) {
                m.caught = true;
                miceCaught++;
                if (miceCaught >= MICE_TARGET) complete();
                return;
            }
        }
    }

    // ══════════════════════════════════════════════
    // 打砖块
    // ══════════════════════════════════════════════

    private void tickBrickBreaker() {
        int left = panelLeft();
        int top = panelTop();
        float speed = 4.5f;
        // 炮台跟随鼠标
        paddleX = Mth.clamp((float) lastMouseX - 30, left + 5, left + PANEL_W - 65);

        if (!ballLaunched) {
            ballX = paddleX + 30;
            ballY = top + PANEL_H - 30;
            return;
        }
        // 移动球
        ballX += ballVX;
        ballY += ballVY;

        // 墙壁反弹
        if (ballX < left + 4 || ballX > left + PANEL_W - 4) ballVX = -ballVX;
        if (ballY < top + HEADER_H) ballVY = -ballVY;

        // 球掉落 → 重置到炮台
        if (ballY > top + PANEL_H) {
            ballLaunched = false;
            ballVX = 0;
            ballVY = 0;
            return;
        }

        // 炮台反弹（自然弹走，不重置位置和方向）
        float paddleY = top + PANEL_H - 22;
        if (ballVY > 0 && ballY + 8 > paddleY && ballY - 8 < paddleY + 12
                && ballX > paddleX - 4 && ballX < paddleX + 64) {
            ballVY = -Math.abs(ballVY);
            ballVX += (ballX - (paddleX + 30)) * 0.12f;
            ballY = paddleY - 9;
        }

        // 砖块碰撞
        for (Brick b : bricks) {
            if (!b.alive) continue;
            if (ballX + 8 > b.x && ballX - 8 < b.x + b.w && ballY + 8 > b.y && ballY - 8 < b.y + b.h) {
                b.alive = false;
                bricksRemaining--;
                // 反弹方向
                float cx = b.x + b.w / 2f;
                float cy = b.y + b.h / 2f;
                if (Math.abs(ballX - cx) * b.h > Math.abs(ballY - cy) * b.w) {
                    ballVX = -ballVX;
                } else {
                    ballVY = -ballVY;
                }
                if (bricksRemaining <= 0) complete();
                break;
            }
        }

        // 速度钳制
        float v = (float) Math.sqrt(ballVX * ballVX + ballVY * ballVY);
        if (v > 0) { ballVX = ballVX / v * speed; ballVY = ballVY / v * speed; }
    }

    private void renderBrickBreaker(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, tr("common.hits", Math.max(0, bricksRemaining), bricks.size()),
                width / 2, top + 4, WHITE);
        for (Brick b : bricks) {
            if (!b.alive) continue;
            MinigameUI.roundRect(g, b.x, b.y, b.x + b.w, b.y + b.h, 3, b.color);
            MinigameUI.roundBorder(g, b.x, b.y, b.x + b.w, b.y + b.h, 3, 1, 0x44FFFFFF);
        }
        // 炮台
        float py = top + PANEL_H - 22;
        MinigameUI.roundRect(g, (int) paddleX, (int) py, (int) paddleX + 60, (int) py + 12, 4, 0xFF8899AA);
        // 球
        drawCircle(g, Math.round(ballX), Math.round(ballY), 8, WHITE);
        if (!ballLaunched) {
            g.drawCenteredString(font, modeText("hint"), width / 2, (int) py + 16, MUTED);
        }
    }

    // ══════════════════════════════════════════════
    // 找零钱
    // ══════════════════════════════════════════════

    private void renderMakeChange(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, modeText("target", changeTarget), width / 2, top + 14, YELLOW);
        int currentSum = 0;
        for (int i = 0; i < 4; i++) currentSum += playerCounts[i] * BILL_VALUES[i];
        g.drawCenteredString(font, modeText("current", currentSum), width / 2, top + 32, currentSum == changeTarget ? GREEN : WHITE);

        // 收银台区域
        int regY = top + 195;
        MinigameUI.roundRect(g, left + 40, regY, left + PANEL_W - 40, regY + 40, 6, 0xFF2A3545);
        MinigameUI.roundBorder(g, left + 40, regY, left + PANEL_W - 40, regY + 40, 6, 1, 0xFF445566);
        g.drawCenteredString(font, modeText("register"), width / 2, regY + 12, MUTED);

        // 已放入的纸币
        int xOff = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < playerCounts[i]; j++) {
                int bx = left + 50 + xOff * 18;
                if (bx > left + PANEL_W - 70) break;
                MinigameUI.roundRect(g, bx, regY + 4, bx + 16, regY + 36, 3, BILL_COLORS[i]);
                g.drawString(font, String.valueOf(BILL_VALUES[i]), bx + 4, regY + 14, WHITE);
                xOff++;
            }
        }

        // 可拖动纸币
        for (Piece p : pieces) {
            if (p == draggedPiece) continue;
            MinigameUI.roundRect(g, (int) p.x, (int) p.y, (int) p.x + 42, (int) p.y + 32, 5, p.color);
            g.drawCenteredString(font, p.label, (int) p.x + 21, (int) p.y + 12, WHITE);
        }
        // 拖拽中纸币
        if (draggedPiece != null) {
            Piece p = draggedPiece;
            MinigameUI.roundRect(g, (int) p.x + 2, (int) p.y + 2, (int) p.x + 44, (int) p.y + 34, 5, 0x40000000);
            MinigameUI.roundRect(g, (int) p.x, (int) p.y, (int) p.x + 42, (int) p.y + 32, 5, p.color);
            g.drawCenteredString(font, p.label, (int) p.x + 21, (int) p.y + 12, WHITE);
        }
    }

    private void dropChangeBill(Piece p, double mouseX, double mouseY) {
        int top = panelTop();
        int regY = top + 195;
        if (mouseY >= regY && mouseY <= regY + 40) {
            int idx = p.target; // bill index 0=¥1,1=¥2,2=¥5,3=¥10
            playerCounts[idx]++;
            int currentSum = 0;
            for (int i = 0; i < 4; i++) currentSum += playerCounts[i] * BILL_VALUES[i];
            if (currentSum == changeTarget) {
                // 检查是否是最优解（贪心）
                boolean optimal = true;
                for (int i = 0; i < 4; i++) {
                    if (playerCounts[i] != greedyCounts[i]) { optimal = false; break; }
                }
                if (optimal) complete();
            }
        }
    }

    // ══════════════════════════════════════════════
    // 水果忍者
    // ══════════════════════════════════════════════

    private void tickFruitNinja() {
        int left = panelLeft();
        int top = panelTop();
        // 生成水果
        if (--fruitSpawnTimer <= 0) {
            fruitSpawnTimer = 18 + rng.nextInt(22);
            boolean isBomb = rng.nextInt(8) == 0;
            float spawnX = left + PANEL_W * 0.15f + rng.nextFloat() * PANEL_W * 0.7f;
            float vx = (rng.nextFloat() - 0.5f) * 3.5f;
            float vy = -(7f + rng.nextFloat() * 5f);
            fruitList.add(new Fruit(spawnX, top + PANEL_H - 10, vx, vy, isBomb ? -1 : rng.nextInt(FRUITS.length), isBomb));
        }
        // 物理更新
        for (Fruit f : fruitList) {
            f.vy += 0.26f;
            f.x += f.vx;
            f.y += f.vy;
            f.rotation += f.vx * 2f;
        }
        // 超出面板区域则移除
        fruitList.removeIf(f -> f.y > top + PANEL_H + 20 || f.x < left - 20 || f.x > left + PANEL_W + 20
                || f.y < top + HEADER_H - 30);
    }

    private void checkFruitSlice() {
        double dx = lastMouseX - prevMouseX;
        double dy = lastMouseY - prevMouseY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 8 || !mouseHeld) return;
        for (Fruit f : fruitList) {
            if (f.sliced) continue;
            if (lineCircleDist(prevMouseX, prevMouseY, lastMouseX, lastMouseY, f.x, f.y) < 22) {
                f.sliced = true;
                if (f.isBomb) {
                    fruitSliced = Math.max(0, fruitSliced - 1);
                } else {
                    fruitSliced++;
                    if (fruitSliced >= FRUIT_TARGET) complete();
                }
            }
        }
    }

    private double lineCircleDist(double x1, double y1, double x2, double y2, double cx, double cy) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-6) return Math.sqrt((cx - x1) * (cx - x1) + (cy - y1) * (cy - y1));
        double t = Mth.clamp(((cx - x1) * dx + (cy - y1) * dy) / len2, 0, 1);
        double projX = x1 + t * dx, projY = y1 + t * dy;
        return Math.sqrt((cx - projX) * (cx - projX) + (cy - projY) * (cy - projY));
    }

    private void renderFruitNinja(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, tr("common.hits", fruitSliced, FRUIT_TARGET), width / 2, top + 15, WHITE);
        for (Fruit f : fruitList) {
            int fx = Math.round(f.x) - 12;
            int fy = Math.round(f.y) - 12;
            if (f.sliced) continue;
            g.pose().pushPose();
            g.pose().translate(f.x, f.y, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(f.rotation));
            ItemStack is = f.isBomb ? bombItem : FRUITS[f.type];
            if (is != null) g.renderItem(is, -12, -12);
            g.pose().popPose();
        }
    }

    // ══════════════════════════════════════════════
    // 水阀小游戏
    // ══════════════════════════════════════════════

    private void clickWaterValve(double mouseX, double mouseY) {
        int cx = width / 2;
        int cy = panelTop() + 110;
        if (inCircle(mouseX, mouseY, cx, cy, 80)) {
            valveDragging = true;
            valveLastMouseAngle = Math.atan2(cy - mouseY, mouseX - cx);
        }
    }

    private void dragWaterValve(double mouseX, double mouseY) {
        int cx = width / 2;
        int cy = panelTop() + 110;
        double currentAngle = Math.atan2(cy - mouseY, mouseX - cx);
        double delta = currentAngle - valveLastMouseAngle;

        // 角度差值归一化到 [-PI, PI]
        if (delta > Math.PI) delta -= 2.0 * Math.PI;
        if (delta < -Math.PI) delta += 2.0 * Math.PI;

        // 判断方向是否正确
        boolean correct = valveClockwise ? (delta < 0) : (delta > 0);
        if (correct) {
            valveTotalRotation += Math.abs(delta);
        } else if (Math.abs(delta) > 0.01) {
            // 反方向有惩罚但不重置所有进度
            valveTotalRotation = Math.max(0, valveTotalRotation - Math.abs(delta) * 1.5);
        }

        valveAngle += delta;
        valveLastMouseAngle = currentAngle;

        if (valveTotalRotation >= VALVE_REQUIRED) {
            complete();
        }
    }

    /** tickWaterValve：没有持续 tick 逻辑，进度由鼠标拖拽驱动 */
    private void tickWaterValve() {
        // 进度完全由鼠标拖拽操作驱动，无需持续 tick 逻辑
    }

    private void renderWaterValve(GuiGraphics g, int left, int top) {
        int cx = width / 2;
        int cy = top + 110;
        int outerR = 70;
        int innerR = 14;
        int spokeCount = 4;

        // 方向提示文字
        Component dirHint = valveClockwise ? modeText("clockwise") : modeText("counterclockwise");
        g.drawCenteredString(font, dirHint, cx, top + 30, YELLOW);

        // 拖拽提示
        g.drawCenteredString(font, modeText("hint"), cx, top + 212, MUTED);

        // 外圈
        MinigameUI.ring(g, cx, cy, outerR, 6, 0xFF8090A0);
        drawCircle(g, cx, cy, outerR - 10, 0x443C5060);

        // 辐条（随阀轮旋转）
        for (int i = 0; i < spokeCount; i++) {
            double spokeAngle = valveAngle + i * Math.PI / 2.0;
            int sx = cx - (int) (Math.cos(spokeAngle) * innerR);
            int sy = cy + (int) (Math.sin(spokeAngle) * innerR);
            int ex = cx - (int) (Math.cos(spokeAngle) * (outerR - 8));
            int ey = cy + (int) (Math.sin(spokeAngle) * (outerR - 8));
            g.fill(Math.min(sx, ex), Math.min(sy, ey), Math.max(sx, ex) + 2, Math.max(sy, ey) + 2, 0xFF6B7D90);
        }

        // 中心轴
        drawCircle(g, cx, cy, innerR, 0xFF384554);
        drawCircle(g, cx, cy, innerR - 4, 0xFF4A5E72);

        // 旋转方向箭头（在外圈上绘制弧形箭头）
        drawValveDirectionArrow(g, cx, cy, outerR + 6, valveClockwise);

        // 手柄抓点（4个方向的抓点）
        for (int i = 0; i < spokeCount; i++) {
            double gripAngle = valveAngle + i * Math.PI / 2.0;
            int gx = cx - (int) (Math.cos(gripAngle) * (outerR - 12));
            int gy = cy + (int) (Math.sin(gripAngle) * (outerR - 12));
            drawCircle(g, gx, gy, 6, 0xFF95AABB);
        }
    }

    /** 在阀门外圈绘制方向指示箭头 */
    private void drawValveDirectionArrow(GuiGraphics g, int cx, int cy, int r, boolean clockwise) {
        if (clockwise) {
            // 顺时针箭头：在右侧绘制向下弯曲的箭头
            int x1 = cx + r + 5;
            int y1 = cy - 12;
            int x2 = cx + r + 5;
            int y2 = cy + 12;
            // 箭头头部
            g.fill(x2 - 3, y2 - 4, x2 + 3, y2, 0xFFE0C060);
            g.fill(x2 - 5, y2 - 9, x2 + 5, y2 - 4, 0xFFE0C060);
            // 箭头杆
            g.fill(x1 - 1, y1, x1 + 2, y2 - 4, 0xFFE0C060);
            // 弯曲部分（上方）
            g.fill(cx + r - 2, cy - r - 15, cx + r + 14, cy - r - 13, 0xFFE0C060);
        } else {
            // 逆时针箭头：在右侧绘制向上弯曲的箭头
            int x1 = cx + r + 5;
            int y1 = cy + 12;
            int x2 = cx + r + 5;
            int y2 = cy - 12;
            // 箭头头部
            g.fill(x2 - 3, y2, x2 + 3, y2 + 4, 0xFFE0C060);
            g.fill(x2 - 5, y2 + 4, x2 + 5, y2 + 9, 0xFFE0C060);
            // 箭头杆
            g.fill(x1 - 1, y2 + 4, x1 + 2, y1, 0xFFE0C060);
            // 弯曲部分（下方）
            g.fill(cx + r - 2, cy + r + 13, cx + r + 14, cy + r + 15, 0xFFE0C060);
        }
    }

    private Component shapeLabel(int shape) {
        String id = switch (shape) {
            case 0 -> "circle";
            case 1 -> "square";
            case 2 -> "triangle";
            default -> "cross";
        };
        return tr("shape." + id);
    }

    private Component label(String id) {
        return tr("label." + id);
    }

    private int shapeColor(int shape) {
        return switch (shape) {
            case 0 -> BLUE;
            case 1 -> GREEN;
            case 2 -> YELLOW;
            default -> RED;
        };
    }

    private boolean inRect(double x, double y, double rx, double ry, double rw, double rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }

    private boolean inCircle(double x, double y, double cx, double cy, double r) {
        double dx = x - cx;
        double dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ════════════════════════════════════════════════════ 18新游戏实现

    // ── 贪吃蛇 ──
    private void setupSnake() {
        int cx = panelLeft() + PANEL_W/2, cy = panelTop() + PANEL_H/2;
        snakeBody.clear(); snakeBody.add(new int[]{cx, cy}); snakeFoodX = cx+40; snakeFoodY = cy;
        snakeDir = snakeNextDir = 1; snakeTimer = 0; snakeEaten = 0;
        // 生成障碍物
        snakeObs.clear();
        int left=panelLeft(),top=panelTop();
        for(int i=0;i<8;i++){
            int ox=left+60+rng.nextInt(PANEL_W-120),oy=top+HEADER_H+20+rng.nextInt(PANEL_H-HEADER_H-60);
            snakeObs.add(new int[]{ox,oy});
        }
    }
    private void tickSnake() {
        if(++snakeTimer<6)return; snakeTimer=0; snakeDir=snakeNextDir;
        int[]h=snakeBody.get(0); int nx=h[0],ny=h[1];
        switch(snakeDir){case 0:ny-=12;break;case 1:nx+=12;break;case 2:ny+=12;break;case 3:nx-=12;break;}
        int left=panelLeft(),top=panelTop(),right=left+PANEL_W,bot=top+PANEL_H-20;
        if(nx<left+6||nx>right-6||ny<top+HEADER_H+6||ny>bot-6){init();return;}
        for(int[]s:snakeBody)if(s[0]==nx&&s[1]==ny){init();return;}
        // 障碍物碰撞
        for(int[]o:snakeObs)if(Math.abs(nx-o[0])<10&&Math.abs(ny-o[1])<10){init();return;}
        snakeBody.add(0,new int[]{nx,ny});
        if(Math.abs(nx-snakeFoodX)<10&&Math.abs(ny-snakeFoodY)<10){
            snakeFoodX=left+30+rng.nextInt(PANEL_W-60); snakeFoodY=top+HEADER_H+20+rng.nextInt(PANEL_H-HEADER_H-40);
            snakeEaten++;
            if(snakeEaten>=5)complete();
        }else snakeBody.remove(snakeBody.size()-1);
    }
    private void renderSnake(GuiGraphics g,int left,int top){
        for(int[]o:snakeObs)MinigameUI.roundRect(g,o[0]-6,o[1]-6,o[0]+6,o[1]+6,3,0xFF665544); // 墙
        for(int[]s:snakeBody)drawCircle(g,s[0],s[1],5,0xFF44CC44);
        drawCircle(g,snakeFoodX,snakeFoodY,6,RED);
        g.drawCenteredString(font,tr("common.hits",snakeEaten,5),width/2,top+6,WHITE);
    }

    // ── 扫雷 ──
    private void setupMinesweeper(){int left=panelLeft(),top=panelTop();
        mineGrid=new int[mineH][mineW]; mineRevealed=new int[mineH][mineW]; mineFlags=new int[mineH][mineW];
        mineCount=minesLeft=3+rng.nextInt(4); // 5x5→3~6颗雷
        for(int i=0;i<mineCount;i++){int x,y;do{x=rng.nextInt(mineW);y=rng.nextInt(mineH);}while(mineGrid[y][x]!=0);mineGrid[y][x]=-1;}
        for(int y=0;y<mineH;y++)for(int x=0;x<mineW;x++)if(mineGrid[y][x]==0)for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int ny=y+dy,nx=x+dx;if(ny>=0&&ny<mineH&&nx>=0&&nx<mineW&&mineGrid[ny][nx]==-1)mineGrid[y][x]++;}
    }
    private void renderMinesweeper(GuiGraphics g,int left,int top){
        int cs=35,ox=left+(PANEL_W-mineW*cs)/2,oy=top+40;
        for(int y=0;y<mineH;y++)for(int x=0;x<mineW;x++){
            int rx=ox+x*cs,ry=oy+y*cs,col=mineRevealed[y][x]==1?0xFF667788:0xFF3C4C5E;
            MinigameUI.roundRect(g,rx,ry,rx+cs-2,ry+cs-2,3,col);
            if(mineRevealed[y][x]==1){
                int v=mineGrid[y][x]; int tc=v==-1?RED:switch(v){case 1->BLUE;case 2->GREEN;case 3->RED;case 4->0xFF4444AA;default->WHITE;};
                g.drawCenteredString(font,Component.literal(v==-1?"*":v==0?"":String.valueOf(v)),rx+cs/2,ry+10,tc);
            }else if(mineFlags[y][x]==1){g.fill(rx+cs/2-6,ry+3,rx+cs/2+6,ry+cs-6,RED); g.fill(rx+cs/2-1,ry+3,rx+cs/2+1,ry+cs-3,0xFFCC0000);}
        }
        g.drawCenteredString(font,Component.literal(""+minesLeft),width/2,top+12,WHITE);
    }
    private void clickMine(int mx,int my,int btn){
        int left=panelLeft(),top=panelTop(),cs=35,ox=left+(PANEL_W-mineW*cs)/2,oy=top+40;
        int cx=(mx-ox)/cs,cy=(my-oy)/cs;
        if(cx<0||cx>=mineW||cy<0||cy>=mineH)return;
        if(btn==1){if(mineRevealed[cy][cx]==0&&mineFlags[cy][cx]==0){mineFlags[cy][cx]=1;minesLeft--;}}
        else{if(mineFlags[cy][cx]==1||mineRevealed[cy][cx]==1)return;revealMine(cy,cx);}
    }
    private void revealMine(int y,int x){
        if(y<0||y>=mineH||x<0||x>=mineW||mineRevealed[y][x]==1||mineFlags[y][x]==1)return;
        mineRevealed[y][x]=1; if(mineGrid[y][x]==-1){init();return;}
        if(mineGrid[y][x]==0)for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)revealMine(y+dy,x+dx);
        int unrevealed=0;for(int i=0;i<mineH;i++)for(int j=0;j<mineW;j++)if(mineRevealed[i][j]==0)unrevealed++;
        if(unrevealed==mineCount)complete();
    }

    // ── 记忆游戏（5灰块动画→按序点击）──
    // 5色块按序记忆：亮完5个→玩家按序点→全对通关(一次就行)
    private boolean[] simonClicked; // 玩家点过的变绿
    private void setupSimon(){simonSeq.clear();simonStep=0;simonPlayerIdx=0;simonFlash=-1;simonTimer=0;
        for(int i=0;i<5;i++)simonSeq.add(i);Collections.shuffle(simonSeq,rng); // 0~4各一次,随机顺序
        simonClicked=new boolean[5];}
    private void tickSimon(){
        if(simonStep<5){if(++simonTimer>20){simonTimer=0;simonFlash=simonStep;simonStep++;}} // 每格1秒
        else simonFlash=-1;}
    private void renderSimon(GuiGraphics g,int left,int top){
        int[]cols={RED,GREEN,BLUE,YELLOW,0xFFAA66FF}; int cx=left+PANEL_W/2,cy=top+PANEL_H/2-20;
        for(int i=0;i<5;i++){int x=cx-130+i*65;
            boolean flash=simonFlash>=0&&simonSeq.get(simonFlash)==i;
            int col=simonClicked[i]?GREEN:(flash?cols[i]:0xFF334455);
            MinigameUI.roundRect(g,x,cy,x+55,cy+55,6,col);}
        g.drawCenteredString(font,tr("common.hits",simonPlayerIdx,5),width/2,top+8,WHITE);
        if(simonFlash>=0)g.drawCenteredString(font,Component.literal("记住顺序..."),width/2,top+130,MUTED);
    }
    private void clickSimon(int i){
        if(simonFlash>=0||simonPlayerIdx>=5)return;
        if(i==simonSeq.get(simonPlayerIdx)){simonClicked[i]=true;simonPlayerIdx++;
            if(simonPlayerIdx>=5)complete();}
        else init();
    }

    // ── 2048 ──
    private void setup2048(){grid2048=new int[4][4];spawn2048();spawn2048();}
    private void spawn2048(){List<int[]>e=new ArrayList<>();for(int r=0;r<4;r++)for(int c=0;c<4;c++)if(grid2048[r][c]==0)e.add(new int[]{r,c});if(!e.isEmpty()){int[]p=e.get(rng.nextInt(e.size()));grid2048[p[0]][p[1]]=rng.nextInt(3)==0?4:2;}}
    private void render2048(GuiGraphics g,int left,int top){
        int cs=56,gap=4,ox=left+(PANEL_W-4*cs-3*gap)/2,oy=top+40;
        for(int r=0;r<4;r++)for(int c=0;c<4;c++){
            int v=grid2048[r][c],x=ox+c*(cs+gap),y=oy+r*(cs+gap);
            int col=v==0?0xFF334455:v<=8?0xFF4488AA:v<=32?0xFF44AA66:v<=128?0xFFDDAA44:0xFFDD6644;
            MinigameUI.roundRect(g,x,y,x+cs,y+cs,5,col);
            if(v>0)g.drawCenteredString(font,Component.literal(""+v),x+cs/2,y+cs/2-5,v>=128?0xFF101010:WHITE);
        }
    }
    // 参考用户提供的标准2048合并算法
    private int[] mergeArray(int[] arr){
        int[] result=new int[4];int index=0;
        // 压缩非零元素
        for(int i=0;i<4;i++)if(arr[i]!=0)result[index++]=arr[i];
        // 合并相邻相同元素（每对只合并一次）
        for(int i=0;i<3;i++)if(result[i]==result[i+1]&&result[i]!=0){result[i]*=2;result[i+1]=0;}
        // 再次压缩
        int[] fin=new int[4];int idx=0;
        for(int i=0;i<4;i++)if(result[i]!=0)fin[idx++]=result[i];
        return fin;
    }
    private boolean slide2048(int d){
        boolean moved=false;
        for(int n=0;n<4;n++){
            int[]vals=new int[4];
            for(int i=0;i<4;i++){
                int nr=d<2?n:i,nc=d<2?i:n;
                int r=d==2?3-nr:d==3?nr:nr,c=d==1?3-nc:d==0?nc:nc;
                vals[i]=grid2048[r][c];
            }
            int[]merged=mergeArray(vals);
            for(int i=0;i<4;i++){
                int nr=d<2?n:i,nc=d<2?i:n;
                int r=d==2?3-nr:d==3?nr:nr,c=d==1?3-nc:d==0?nc:nc;
                if(grid2048[r][c]!=merged[i]){grid2048[r][c]=merged[i];moved=true;}
            }
        }
        return moved;
    }

    // ── 接蛋 ──
    private void tickCatchEggs(){if(--eggSpawnT<=0){eggSpawnT=25+rng.nextInt(20);eggs.add(new Egg(panelLeft()+30+rng.nextInt(PANEL_W-60),-20,rng.nextInt(3)));}
        for(Egg e:eggs){e.y+=3.5f; if(e.y>panelTop()+PANEL_H)eggsMissed++;} eggs.removeIf(e->e.y>panelTop()+PANEL_H||e.caught);
        basketX=Mth.clamp((float)lastMouseX-25,panelLeft()+5,panelLeft()+PANEL_W-55);
        int by=panelTop()+PANEL_H-30;
        for(Egg e:eggs){if(e.y+16>by&&e.y<by+16&&e.x+16>basketX&&e.x<basketX+50){e.caught=true;eggsCaught++;if(eggsCaught>=5)complete();}}
    }
    private void renderCatchEggs(GuiGraphics g,int left,int top){
        for(Egg e:eggs){int col=e.type==0?0xFFF5DEB3:e.type==1?0xFFCD853F:e.type==2?GREEN:RED;drawCircle(g,Math.round(e.x+16),Math.round(e.y+16),16,col);}
        MinigameUI.roundRect(g,(int)basketX,top+PANEL_H-30,(int)basketX+50,top+PANEL_H-14,4,0xFF8B6914);
        g.drawCenteredString(font,tr("common.hits",eggsCaught,5),width/2,top+8,WHITE);
    }

    // ── 颜色分类 (Water Sort Puzzle) ──
    private void setupColorSort(){
        colorSelected=-1;
        int T=6,L=4;
        colorTubes=new int[T][L];
        // 4种颜色各4个，洗牌后分配（保证有解——最多执行10次重试避免死循环）
        List<Integer> items=new ArrayList<>();
        for(int c=0;c<4;c++)for(int i=0;i<4;i++)items.add(c);
        int retry=0;
        do{
            Collections.shuffle(items,rng);
            int idx=0;
            for(int t=0;t<T;t++)Arrays.fill(colorTubes[t],-1);
            for(int t=0;t<4;t++)for(int l=0;l<4;l++)colorTubes[t][l]=items.get(idx++);
            retry++;
        }while(colorSortDone()&&retry<10);
    }
    private boolean emptyTube(int t){for(int l=0;l<4;l++)if(colorTubes[t][l]>=0)return false;return true;}
    private int tubeTop(int t){for(int l=3;l>=0;l--)if(colorTubes[t][l]>=0)return colorTubes[t][l];return -1;}
    private int tubeTopIdx(int t){for(int l=3;l>=0;l--)if(colorTubes[t][l]>=0)return l;return -1;}
    private int tubeTopCount(int t){int top=tubeTop(t);if(top<0)return 0;int cnt=0;for(int l=3;l>=0;l--){if(colorTubes[t][l]==top)cnt++;else break;}return cnt;}
    private int tubeFree(int t){int cnt=0;for(int l=0;l<4;l++)if(colorTubes[t][l]<0)cnt++;return cnt;}
    private boolean pour(int from,int to){
        int tc=tubeTop(from),sc=tubeTop(to);
        if(tc<0)return false;if(sc>=0&&sc!=tc)return false;
        if(tubeFree(to)<=0)return false;
        int fi=tubeTopIdx(from);
        colorTubes[from][fi]=-1;
        int ti=tubeTopIdx(to);
        colorTubes[to][ti+1]=tc;
        return true;
    }
    private boolean colorSortDone(){for(int t=0;t<6;t++){if(emptyTube(t))continue;int prev=-1;for(int l=0;l<4;l++)if(colorTubes[t][l]>=0){if(prev>=0&&colorTubes[t][l]!=prev)return false;prev=colorTubes[t][l];}if(prev>=0&&colorTubes[t][3]<0)return false;}return true;}
    private void tickColorSort(){if(colorSortDone())complete();}
    private void renderColorSort(GuiGraphics g,int left,int top){
        int T=6,tw=42,th=120,gap=10,total=T*tw+(T-1)*gap,sx=left+(PANEL_W-total)/2,sy=top+HEADER_H+20;
        int[]cols={RED,BLUE,GREEN,YELLOW};
        for(int t=0;t<T;t++){
            int tx=sx+t*(tw+gap);
            MinigameUI.roundRect(g,tx,sy,tx+tw,sy+th,6,0x30FFFFFF);
            MinigameUI.roundBorder(g,tx,sy,tx+tw,sy+th,6,2,0x80FFFFFF);
            if(t==colorSelected)MinigameUI.roundBorder(g,tx-3,sy-3,tx+tw+3,sy+th+3,8,2,YELLOW);
            int lh=(th-8)/4;
            for(int l=0;l<4;l++){int c=colorTubes[t][l];if(c>=0){int ly=sy+th-4-(l+1)*lh;g.fill(tx+4,ly,tx+tw-4,ly+lh,cols[c]);}}
            g.fill(tx+3,sy,tx+tw-3,sy+3,0x60FFFFFF); // 管口
        }
    }
    private void clickColorSort(double mx,double my){
        int T=6,tw=42,th=120,gap=10,total=T*tw+(T-1)*gap,sx=panelLeft()+(PANEL_W-total)/2,sy=panelTop()+HEADER_H+20;
        for(int t=0;t<T;t++){int tx=sx+t*(tw+gap);if(inRect(mx,my,tx,sy,tw,th)){
            if(colorSelected<0){if(!emptyTube(t))colorSelected=t;}
            else if(colorSelected==t)colorSelected=-1;
            else{if(pour(colorSelected,t))colorSelected=-1;else if(!emptyTube(t))colorSelected=t;}
            return;
        }}
        colorSelected=-1;
    }
    // 猜数字 ──
    private void setupGuessNumber(){guessSecret=1+rng.nextInt(100);guessLow=1;guessHigh=100;guessCount=0;guessInput="";}
    // 反应测试 ──
    private void tickReactionTest(){
        if(reactState==0){reactTimer++;if(reactTimer>30+rng.nextInt(50)){reactState=1;reactStart=System.currentTimeMillis();}}
        else if(reactState==2){reactTimer++;if(reactTimer>40){reactTimer=0;reactState=0;reactFailed=false;}}
    }
    // 连连看 ──
    private void setupLinkMatch(){linkGrid=new int[4][6];List<Integer>p=new ArrayList<>();for(int i=0;i<12;i++){p.add(i%6);p.add(i%6);}Collections.shuffle(p,rng);
        int idx=0;for(int r=0;r<4;r++)for(int c=0;c<6;c++)linkGrid[r][c]=p.get(idx++);linkSelX=-1;linkSelY=-1;}
    private void renderLinkMatch(GuiGraphics g,int left,int top){
        int cs=32,ox=left+30,oy=top+40; int[]cols={RED,GREEN,BLUE,YELLOW,0xFFAA66FF,0xFFFF8844};
        for(int r=0;r<4;r++)for(int c=0;c<6;c++){
            int x=ox+c*cs,y=oy+r*cs,val=linkGrid[r][c];
            int col=val>=0?cols[val]:0xFF222222;
            MinigameUI.roundRect(g,x,y,x+cs-2,y+cs-2,4,col);
            if((r==linkSelY&&c==linkSelX))MinigameUI.roundBorder(g,x,y,x+cs-2,y+cs-2,4,2,WHITE);
        }
    }
    private boolean canLink(int x1,int y1,int x2,int y2){if(x1==x2&&y1==y2)return false;
        return checkStraight(x1,y1,x2,y2)||check1Bend(x1,y1,x2,y2)||check2Bend(x1,y1,x2,y2);}
    private boolean isEmptyCell(int x,int y){return x<0||x>=6||y<0||y>=4||linkGrid[y][x]<0;}
    private boolean checkStraight(int x1,int y1,int x2,int y2){
        if(x1==x2){for(int y=Math.min(y1,y2)+1;y<Math.max(y1,y2);y++)if(!isEmptyCell(x1,y))return false;return true;}
        if(y1==y2){for(int x=Math.min(x1,x2)+1;x<Math.max(x1,x2);x++)if(!isEmptyCell(x,y1))return false;return true;}return false;}
    private boolean check1Bend(int x1,int y1,int x2,int y2){return checkCorner(x1,y1,x2,y2,x1,y2)||checkCorner(x1,y1,x2,y2,x2,y1);}
    private boolean checkCorner(int x1,int y1,int x2,int y2,int cx,int cy){return isEmptyCell(cx,cy)&&checkStraight(x1,y1,cx,cy)&&checkStraight(cx,cy,x2,y2);}
    private boolean check2Bend(int x1,int y1,int x2,int y2){for(int r=-1;r<=4;r++)for(int c=-1;c<=6;c++)if((r==y1||r==y2||c==x1||c==x2)&&isEmptyCell(c,r))if(checkCorner(x1,y1,x2,y2,c,r))return true;return false;}

    // ── 俄罗斯方块（8x6面板，消除1行即通关）──
    private void setupTetris(){tetrisBoard=new int[8][6];spawnTetrisPiece();tetrisTimer=0;tetrisLines=0;}
    private int[][] getTetrisShape(int p,int r){return switch(p){
        case 0->new int[][]{{0,0},{1,0},{0,1},{1,1}};case 1->new int[][]{{0,0},{0,1},{0,2},{0,3}};
        case 2->new int[][]{{0,0},{1,0},{2,0},{2,1}};case 3->new int[][]{{1,0},{0,1},{1,1},{2,1}};
        case 4->new int[][]{{0,0},{0,1},{1,1},{1,2}};default->new int[][]{{0,0},{1,0},{1,1},{2,1}};};}
    private void spawnTetrisPiece(){tetrisPiece=rng.nextInt(6);tetrisRot=0;tetrisPX=2;tetrisPY=0;}
    private void tickTetris(){if(++tetrisTimer<12)return;tetrisTimer=0;
        if(canPlaceTetris(tetrisPX,tetrisPY+1,tetrisPiece,tetrisRot))tetrisPY++;else{placeTetris();clearTetrisLines();spawnTetrisPiece();if(!canPlaceTetris(tetrisPX,tetrisPY,tetrisPiece,tetrisRot))init();}}
    private boolean canPlaceTetris(int px,int py,int p,int r){int[][]s=getTetrisShape(p,r);for(int[]c:s){int x=px+c[0],y=py+c[1];if(x<0||x>=6||y>=8||(y>=0&&tetrisBoard[y][x]!=0))return false;}return true;}
    private void placeTetris(){int[][]s=getTetrisShape(tetrisPiece,tetrisRot);for(int[]c:s){int x=tetrisPX+c[0],y=tetrisPY+c[1];if(y>=0)tetrisBoard[y][x]=1;}}
    private void clearTetrisLines(){for(int r=7;r>=0;r--){boolean full=true;for(int c=0;c<6;c++)if(tetrisBoard[r][c]==0){full=false;break;}if(full){tetrisLines++;for(int rr=r;rr>0;rr--)System.arraycopy(tetrisBoard[rr-1],0,tetrisBoard[rr],0,6);r++;if(tetrisLines>=1)complete();}}}
    private void renderTetris(GuiGraphics g,int left,int top){int cs=26,ox=left+(PANEL_W-6*cs)/2,oy=top+30;
        // 绘制游戏区域边界
        MinigameUI.roundBorder(g,ox-2,oy-2,ox+6*cs+2,oy+8*cs+2,3,2,0x66FFFFFF);
        for(int r=0;r<8;r++)for(int c=0;c<6;c++)if(tetrisBoard[r][c]!=0)MinigameUI.roundRect(g,ox+c*cs,oy+r*cs,ox+c*cs+cs-1,oy+r*cs+cs-1,2,0xFF44AACC);
        int[][]s=getTetrisShape(tetrisPiece,tetrisRot);for(int[]c:s){int x=tetrisPX+c[0],y=tetrisPY+c[1];if(y>=0)MinigameUI.roundRect(g,ox+x*cs,oy+y*cs,ox+x*cs+cs-1,oy+y*cs+cs-1,2,0xFFAA44CC);}
        g.drawCenteredString(font,tr("common.hits",tetrisLines,1),width/2,top+6,WHITE);}

    // ── 翻牌配对 ──
    private void setupMemMatch(){memCards=new int[16];memRevealed=new int[16];List<Integer>p=new ArrayList<>();for(int i=0;i<8;i++){p.add(i);p.add(i);}Collections.shuffle(p,rng);for(int i=0;i<16;i++)memCards[i]=p.get(i);memSel1=-1;memSel2=-1;memFound=0;memWait=0;}
    private void tickMemMatch(){if(memWait>0){memWait--;if(memWait<=0){memRevealed[memSel1]=0;memRevealed[memSel2]=0;memSel1=-1;memSel2=-1;}}}
    private void renderMemMatch(GuiGraphics g,int left,int top){int cs=38,ox=left+70,oy=top+30;
        int[]cols={RED,GREEN,BLUE,YELLOW,0xFFAA66FF,0xFFFF8844,0xFF44AACC,0xFFCC66AA};
        for(int i=0;i<16;i++){int x=ox+(i%4)*cs,y=oy+(i/4)*cs;boolean rev=memRevealed[i]==1||memCards[i]<0;
            MinigameUI.roundRect(g,x,y,x+cs-2,y+cs-2,4,rev?cols[Math.abs(memCards[i])%8]:0xFF334455);
            if(rev&&memCards[i]>=0)g.drawCenteredString(font,Component.literal(""+((memCards[i]%8)+1)),x+cs/2,y+10,WHITE);
            if(i==memSel1||i==memSel2)MinigameUI.roundBorder(g,x,y,x+cs-2,y+cs-2,4,2,WHITE);}
        g.drawCenteredString(font,tr("common.hits",memFound,8),width/2,top+8,WHITE);}

    // ── 接管道 ──
    // pipeVal: bit0=上 bit1=右 bit2=下 bit3=左 (连接方向)
    private void setupPipeConnect(){pipeGrid=new int[pipeH][pipeW];
        int[][]solution=new int[pipeH][pipeW];
        boolean[][]onPath=new boolean[pipeH][pipeW];
        onPath[0][0]=true;
        int r=0,c=0;
        // 随机走到终点
        while(!(r==pipeH-1&&c==pipeW-1)){
            List<int[]>opts=new ArrayList<>();
            if(r>0&&!onPath[r-1][c])opts.add(new int[]{r-1,c,0,2}); if(r<pipeH-1&&!onPath[r+1][c])opts.add(new int[]{r+1,c,2,0});
            if(c>0&&!onPath[r][c-1])opts.add(new int[]{r,c-1,3,1}); if(c<pipeW-1&&!onPath[r][c+1])opts.add(new int[]{r,c+1,1,3});
            if(opts.isEmpty()){r=0;c=0;for(int i=0;i<pipeH;i++)Arrays.fill(onPath[i],false);onPath[0][0]=true;continue;}
            int[]n=opts.get(rng.nextInt(opts.size()));
            solution[r][c]|=(1<<n[2]); solution[n[0]][n[1]]|=(1<<n[3]);
            r=n[0];c=n[1];onPath[r][c]=true;
        }
        // 非路径格：连接到一个相邻格（确保无死局）
        for(r=0;r<pipeH;r++)for(c=0;c<pipeW;c++)if(!onPath[r][c]){
            int[][]nb={{r-1,c,0,2},{r+1,c,2,0},{r,c-1,3,1},{r,c+1,1,3}};
            Collections.shuffle(Arrays.asList(nb),rng);
            for(int[]n:nb){int nr=n[0],nc=n[1];if(nr>=0&&nr<pipeH&&nc>=0&&nc<pipeW){solution[r][c]|=(1<<n[2]);solution[nr][nc]|=(1<<n[3]);break;}}
        }
        for(r=0;r<pipeH;r++)for(c=0;c<pipeW;c++)pipeGrid[r][c]=solution[r][c];
        // 仅随机旋转非路径上的管道（增加难度，不破坏路径）
        for(int i=0;i<6;i++){int pr=rng.nextInt(pipeH),pc=rng.nextInt(pipeW);if(!onPath[pr][pc])pipeGrid[pr][pc]=(pipeGrid[pr][pc]<<rng.nextInt(3))&15;}
    }
    private void renderPipeConnect(GuiGraphics g,int left,int top){int cs=40,ox=left+(PANEL_W-pipeW*cs)/2,oy=top+40;
        for(int r=0;r<pipeH;r++)for(int c=0;c<pipeW;c++){int x=ox+c*cs,y=oy+r*cs,v=pipeGrid[r][c];
            MinigameUI.roundRect(g,x,y,x+cs-2,y+cs-2,3,0xFF334455);
            if((v&1)!=0)g.fill(x+cs/2-2,y,x+cs/2+2,y+cs/2,0xFF44CCFF); if((v&2)!=0)g.fill(x+cs/2,y+cs/2-2,x+cs,y+cs/2+2,0xFF44CCFF);
            if((v&4)!=0)g.fill(x+cs/2-2,y+cs/2,x+cs/2+2,y+cs,0xFF44CCFF); if((v&8)!=0)g.fill(x,y+cs/2-2,x+cs/2,y+cs/2+2,0xFF44CCFF);}
        // 起点(绿色) 终点(红色)
        drawCircle(g,ox+cs/2,oy+cs/2,6,GREEN); drawCircle(g,ox+(pipeW-1)*cs+cs/2,oy+(pipeH-1)*cs+cs/2,6,RED);
        if(checkPipeConnected())complete();
    }
    private boolean checkPipeConnected(){
        boolean[][]vis=new boolean[pipeH][pipeW];vis[0][0]=true; int count=1;
        while(count>0){count=0;
            for(int r=0;r<pipeH;r++)for(int c=0;c<pipeW;c++)if(vis[r][c]){
                int v=pipeGrid[r][c];
                if((v&1)!=0&&r>0&&!vis[r-1][c]&&(pipeGrid[r-1][c]&4)!=0){vis[r-1][c]=true;count++;}
                if((v&2)!=0&&c<pipeW-1&&!vis[r][c+1]&&(pipeGrid[r][c+1]&8)!=0){vis[r][c+1]=true;count++;}
                if((v&4)!=0&&r<pipeH-1&&!vis[r+1][c]&&(pipeGrid[r+1][c]&1)!=0){vis[r+1][c]=true;count++;}
                if((v&8)!=0&&c>0&&!vis[r][c-1]&&(pipeGrid[r][c-1]&2)!=0){vis[r][c-1]=true;count++;}
            }
        }
        return vis[pipeH-1][pipeW-1];
    }

    // ── 点灯 ──
    private void setupLightsOut(){lightGrid=new boolean[lightsSize][lightsSize];int onCount=0;
        while(onCount<3){for(int r=0;r<lightsSize;r++)Arrays.fill(lightGrid[r],false);for(int i=0;i<8;i++)toggleLight(rng.nextInt(lightsSize),rng.nextInt(lightsSize));
            onCount=0;for(int r=0;r<lightsSize;r++)for(int c=0;c<lightsSize;c++)if(lightGrid[r][c])onCount++;}}
    private void toggleLight(int r,int c){if(r>=0&&r<lightsSize&&c>=0&&c<lightsSize)lightGrid[r][c]=!lightGrid[r][c];}
    private void renderLightsOut(GuiGraphics g,int left,int top){int cs=38,ox=left+100,oy=top+40;
        for(int r=0;r<lightsSize;r++)for(int c=0;c<lightsSize;c++){int x=ox+c*cs,y=oy+r*cs;
            MinigameUI.roundRect(g,x,y,x+cs-2,y+cs-2,4,lightGrid[r][c]?YELLOW:0xFF334455);}
        int onCount=0;for(int r=0;r<lightsSize;r++)for(int c=0;c<lightsSize;c++)if(lightGrid[r][c])onCount++;if(onCount<=2)complete();}

    // ── 24点 (运营商+数字，保证有解) ──
    private boolean canMake24(int[]nums){
        int[][]perms=permute4(nums);
        for(int[]p:perms)for(int o1=0;o1<4;o1++)for(int o2=0;o2<4;o2++)for(int o3=0;o3<4;o3++){
            if(calc24(calc24(p[0],o1,p[1]),o2,calc24(p[2],o3,p[3]))==24)return true;}
        return false;}
    private int[][] permute4(int[]n){List<int[]>res=new ArrayList<>();for(int a=0;a<4;a++)for(int b=0;b<4;b++)if(b!=a)for(int c=0;c<4;c++)if(c!=a&&c!=b)for(int d=0;d<4;d++)if(d!=a&&d!=b&&d!=c){res.add(new int[]{n[a],n[b],n[c],n[d]});}
        return res.toArray(new int[0][]);}
    private void setupGame24(){game24Nums=new int[4];
        do{for(int i=0;i<4;i++)game24Nums[i]=1+rng.nextInt(10);}while(!canMake24(game24Nums));
        game24Sel1=-1;game24Sel2=-1;game24Expr=new int[7];for(int i=0;i<7;i++)game24Expr[i]=-1;game24Drag=-1;game24Used=new boolean[4];
        game24Expr[1]=0;game24Expr[3]=0;game24Expr[5]=0;} // 全部+，玩家自行切换
    private int[] game24Expr; private int game24Drag; private boolean[] game24Used;
    private int eval24(){int a=game24Expr[0],b=game24Expr[2],c=game24Expr[4],d=game24Expr[6];if(a<0||b<0||c<0||d<0)return-1;int op1=game24Expr[1],op2=game24Expr[3],op3=game24Expr[5];if(op1<0||op2<0||op3<0)return-1;
        return calc24(calc24(a,op1,b),op2,calc24(c,op3,d));}
    private int calc24(int x,int op,int y){return switch(op){case 0->x+y;case 1->x-y;case 2->x*y;case 3->y!=0?x/y:0;default->0;};}
    private void clear24Slot(int ei){if(ei%2==0&&game24Expr[ei]>=0){int v=game24Expr[ei];for(int j=0;j<4;j++)if(game24Nums[j]==v&&game24Used[j]){game24Used[j]=false;break;}game24Expr[ei]=-1;}}
    private void renderGame24(GuiGraphics g,int left,int top){
        int bx=left+10,by=top+75; String[]ops={"+","-","×","/"};
        // 上方数字选择（已使用的变暗）
        for(int i=0;i<4;i++){int x=bx+i*46;boolean used=game24Used[i];MinigameUI.roundRect(g,x,by-45,x+38,by-8,4,game24Drag==i?YELLOW:used?0xFF222222:0xFF334455);
            g.drawCenteredString(font,Component.literal(""+game24Nums[i]),x+19,by-30,used?MUTED:WHITE);}
        // 表达式: ( N1 OP1 N2 ) OP2 ( N3 OP3 N4 )
        int cw=24; // 每个token宽
        String[]fixed={"(","","","",")","","(","","","",")"}; // 括号 op2 括号
        for(int i=0;i<11;i++){int x=bx+i*cw;
            if(i==1||i==3||i==7||i==9){ // 4个数字槽 → expr[0],[2],[4],[6]
                int ei=i==1?0:i==3?2:i==7?4:6; int val=game24Expr[ei];
                MinigameUI.roundRect(g,x,by+10,x+cw-1,by+38,4,val>=0?0xFF445566:0xFF334455);
                if(val>=0)g.drawCenteredString(font,Component.literal(""+val),x+cw/2,by+22,WHITE);}
            else if(i==2||i==5||i==8){int oi=i==2?1:i==5?3:5; MinigameUI.roundRect(g,x,by+10,x+cw-1,by+38,4,0xFF3377AA); MinigameUI.roundBorder(g,x,by+10,x+cw-1,by+38,4,1,0xFF55AAFF); g.drawCenteredString(font,Component.literal(ops[Math.abs(game24Expr[oi]%4)]),x+cw/2,by+22,WHITE);}
            else{g.drawString(font,fixed[i],x,by+13,MUTED);}
        }
        g.drawCenteredString(font,tr("game_24.hint"),width/2,top+160,WHITE);
        int v=eval24();if(v==24)complete();
    }
    private void click24(double mx,double my){int bx=panelLeft()+10,by=panelTop()+75; int cw=24;
        // 点击上方数字 → 选中（已使用的不能选）
        for(int i=0;i<4;i++){int x=bx+i*46;if(inRect(mx,my,x,by-45,38,37)){if(game24Drag>=0){game24Drag=-1;return;}if(!game24Used[i])game24Drag=i;return;}}
        // 点击表达式数字槽(位置1,3,7,9) → 放置/覆盖/清除
        int[]nMap={1,3,7,9};int[]eMap={0,2,4,6}; // nMap:token位置, eMap:expr索引
        for(int k=0;k<4;k++){int x=bx+nMap[k]*cw;if(inRect(mx,my,x,by+10,cw-1,28)){
            if(game24Drag>=0){clear24Slot(eMap[k]);game24Expr[eMap[k]]=game24Nums[game24Drag];game24Used[game24Drag]=true;game24Drag=-1;}else{clear24Slot(eMap[k]);}return;}}
        // 点击运算符(pos 2,5,8) → 切换
        if(inRect(mx,my,bx+2*cw,by+10,cw-1,28)){game24Expr[1]=(game24Expr[1]+1)%4;return;}
        if(inRect(mx,my,bx+5*cw,by+10,cw-1,28)){game24Expr[3]=(game24Expr[3]+1)%4;return;}
        if(inRect(mx,my,bx+8*cw,by+10,cw-1,28)){game24Expr[5]=(game24Expr[5]+1)%4;return;}
        if(game24Drag>=0)game24Drag=-1;
    }

    // ── 迷宫 ──
    private void setupMaze(){int w=14,h=10;mazeGrid=new int[h][w];mazePX=1;mazePY=1;mazeEX=w-2;mazeEY=h-2;
        for(int r=0;r<h;r++)for(int c=0;c<w;c++)mazeGrid[r][c]=(r==0||r==h-1||c==0||c==w-1)?1:-1;carveMaze(1,1);
        for(int r=0;r<h;r++)for(int c=0;c<w;c++)if(mazeGrid[r][c]==-1)mazeGrid[r][c]=1;mazeGrid[mazeEY][mazeEX]=0;
        // 确保终点至少有一个相邻通路（四周全是墙则打通一条到最近通路）
        int[][]nb={{0,1},{0,-1},{1,0},{-1,0}};boolean hasPath=false;
        for(int[]d:nb){int ny=mazeEY+d[0],nx=mazeEX+d[1];if(ny>=0&&ny<h&&nx>=0&&nx<w&&mazeGrid[ny][nx]==0)hasPath=true;}
        if(!hasPath)for(int[]d:nb){int ny=mazeEY+d[0],nx=mazeEX+d[1];if(ny>0&&ny<h-1&&nx>0&&nx<w-1){mazeGrid[ny][nx]=0;break;}}}
    private void carveMaze(int r,int c){mazeGrid[r][c]=0;int[][]dirs={{0,2},{2,0},{0,-2},{-2,0}};Collections.shuffle(Arrays.asList(dirs),rng);
        for(int[]d:dirs){int nr=r+d[0],nc=c+d[1];if(nr>0&&nr<mazeGrid.length-1&&nc>0&&nc<mazeGrid[0].length-1&&mazeGrid[nr][nc]==-1){mazeGrid[r+d[0]/2][c+d[1]/2]=0;carveMaze(nr,nc);}}}
    private void renderMaze(GuiGraphics g,int left,int top){int cs=16,ox=left+100,oy=top+30;
        for(int r=0;r<mazeGrid.length;r++)for(int c=0;c<mazeGrid[0].length;c++)if(mazeGrid[r][c]==1)MinigameUI.roundRect(g,ox+c*cs,oy+r*cs,ox+c*cs+cs,oy+r*cs+cs,1,0xFF334455);
        drawCircle(g,ox+mazePX*cs+cs/2,oy+mazePY*cs+cs/2,5,RED);drawCircle(g,ox+mazeEX*cs+cs/2,oy+mazeEY*cs+cs/2,5,GREEN);}

    // ── 天平（物品拖拽） ──
    private int scaleLeftWt, scaleRightWt; private int[] scaleItems={2,3,4,5,7}; private int scaleDragItem=-1; private float scaleDragX,scaleDragY;
    private int[] scaleSlots={-1,-1,-1,-1,-1,-1,-1,-1,-1,-1}; // 0~4左盘,5~9右盘
    private void setupBalanceScale(){scaleLeftWt=0;scaleRightWt=0;scaleDragItem=-1;
        for(int i=0;i<10;i++)scaleSlots[i]=-1;}
    private void renderBalanceScale(GuiGraphics g,int left,int top){
        int px=width/2,py=top+100; // 支点
        g.fill(px-2,py,px+2,py+40,0xFF886644); // 支柱
        g.fill(px-80,py+40,px+80,py+44,0xFF886644); // 横杆
        g.fill(px-80,py+44,px-70,py+80,0xFF886644); // 左绳
        g.fill(px+70,py+44,px+80,py+80,0xFF886644); // 右绳
        MinigameUI.roundRect(g,px-100,py+80,px-50,py+100,3,0xFF66AACC); // 左盘
        MinigameUI.roundRect(g,px+50,py+80,px+100,py+100,3,0xFF66AACC); // 右盘
        // 左盘物品
        for(int i=0;i<5;i++)if(scaleSlots[i]>=0){int si=scaleSlots[i],ix=px-95+i*10; g.renderItem(new ItemStack(scaleItemTex[si]),ix,py+82);}
        // 右盘物品
        for(int i=5;i<10;i++)if(scaleSlots[i]>=0){int si=scaleSlots[i],ix=px+55+(i-5)*10; g.renderItem(new ItemStack(scaleItemTex[si]),ix,py+82);}
        // 底部可拖拽物品
        for(int i=0;i<5;i++){int ix=left+40+i*80; g.renderItem(new ItemStack(scaleItemTex[i]),ix,top+PANEL_H-40); g.drawString(font,String.valueOf(scaleItems[i]),ix+20,top+PANEL_H-24,WHITE);}
        g.drawCenteredString(font,Component.literal("左:"+scaleLeftWt+"  右:"+scaleRightWt),width/2,top+HEADER_H+5,WHITE);
        if(scaleLeftWt>0&&scaleLeftWt==scaleRightWt)complete();
        // 渲染拖拽中的物品
        if(scaleDragItem>=0)g.renderItem(new ItemStack(scaleItemTex[scaleDragItem]),(int)lastMouseX-8,(int)lastMouseY-8);
    }
    private int findFreeSlot(int base){for(int i=0;i<5;i++)if(scaleSlots[base+i]<0)return base+i;return -1;}
    private static final net.minecraft.world.item.Item[] scaleItemTex={Items.IRON_INGOT,Items.GOLD_INGOT,Items.DIAMOND,Items.NETHERITE_INGOT,Items.EMERALD};
    private void clickScale(double mx,double my){int left=panelLeft(),top=panelTop();
        for(int i=0;i<5;i++){int ix=left+40+i*80;if(inRect(mx,my,ix,top+PANEL_H-40,16,16)){scaleDragItem=i;scaleDragX=(float)mx-8;scaleDragY=(float)my-8;return;}}
        // 左盘
        if(inRect(mx,my,width/2-100,top+100+80,50,20)){int si=findFreeSlot(0);if(scaleDragItem>=0&&si>=0){scaleSlots[si]=scaleDragItem;scaleLeftWt+=scaleItems[scaleDragItem];scaleDragItem=-1;}}
        // 右盘
        if(inRect(mx,my,width/2+50,top+100+80,50,20)){int si=findFreeSlot(5);if(scaleDragItem>=0&&si>=0){scaleSlots[si]=scaleDragItem;scaleRightWt+=scaleItems[scaleDragItem];scaleDragItem=-1;}}
        scaleDragItem=-1;
    }

    // ── 华容道 ──
    // 4列×5行, 0=空 1=曹操2×2 2~5=竖将1×2 6=横将2×1 7~10=兵1×1
    private void setupKlotski(){
        klotskiGrid=new int[5][4];klotskiSelR=-1;klotskiSelC=-1;
        // 经典横刀立马布局
        // 张飞 曹操 曹操 马超
        // 张飞 曹操 曹操 马超
        // 赵云 关羽 关羽 黄忠
        // 赵云  卒     卒   黄忠
        //  卒    空    空    卒
        int[][] layout={
            {2, 1, 1, 4},
            {2, 1, 1, 4},
            {3, 6, 6, 5},
            {3, 7, 8, 5},
            {9, 0, 0,10}
        };
        for(int r=0;r<5;r++)for(int c=0;c<4;c++)klotskiGrid[r][c]=layout[r][c];
    }
    private int[] klotskiBlockSize(int br,int bc){
        int id=klotskiGrid[br][bc];if(id<=0)return new int[]{0,0};
        // 先找到块左上角
        int topR=br,leftC=bc;
        while(topR>0&&klotskiGrid[topR-1][bc]==id)topR--;
        while(leftC>0&&klotskiGrid[br][leftC-1]==id)leftC--;
        // 再扫描完整宽高
        int maxR=topR,maxC=leftC;
        while(maxR+1<5&&klotskiGrid[maxR+1][leftC]==id)maxR++;
        while(maxC+1<4&&klotskiGrid[topR][maxC+1]==id)maxC++;
        return new int[]{maxR-topR+1,maxC-leftC+1};
    }
    // 获取块左上角坐标
    private int[] klotskiTopLeft(int br,int bc){
        int id=klotskiGrid[br][bc];if(id<=0)return new int[]{br,bc};
        while(br>0&&klotskiGrid[br-1][bc]==id)br--;
        while(bc>0&&klotskiGrid[br][bc-1]==id)bc--;
        return new int[]{br,bc};
    }
    private boolean canMoveKlotski(int br,int bc,int dr,int dc){
        int[]sz=klotskiBlockSize(br,bc);int h=sz[0],w=sz[1];
        for(int r=br;r<br+h;r++)for(int c=bc;c<bc+w;c++){
            int nr=r+dr,nc=c+dc;
            if(nr<0||nr>=5||nc<0||nc>=4)return false;
            if(klotskiGrid[nr][nc]!=0&&klotskiGrid[nr][nc]!=klotskiGrid[br][bc])return false;
        }return true;}
    private void moveKlotskiBlock(int br,int bc,int dr,int dc){
        if(!canMoveKlotski(br,bc,dr,dc))return;
        int[]sz=klotskiBlockSize(br,bc);int h=sz[0],w=sz[1],id=klotskiGrid[br][bc];
        // 清除旧位置
        for(int r=br;r<br+h;r++)for(int c=bc;c<bc+w;c++)klotskiGrid[r][c]=0;
        // 写入新位置
        int nr=br+dr,nc=bc+dc;
        for(int r=nr;r<nr+h;r++)for(int c=nc;c<nc+w;c++)klotskiGrid[r][c]=id;
        klotskiSelR=nr;klotskiSelC=nc;
        // 曹操(1)到达底部中央(行4列1-2)即胜利
        if(id==1&&nr+h-1==4&&nc==1)complete();
    }
    private void renderKlotski(GuiGraphics g,int left,int top){
        int cs=42,gap=2,ox=left+(PANEL_W-4*cs-3*gap)/2,oy=top+30;
        int[]cols={0,0xFFDD6644,0xFF44AACC,0xFF44CC66,0xFFCC66AA,0xFFDDAA44,0xFF8866CC,0xFFDD8888,0xFF88DD88,0xFF8888DD,0xFFDDAACC};
        String[]names={"","曹操","张飞","赵云","马超","黄忠","关羽","卒","卒","卒","卒"};
        // 选中块整体高亮
        if(klotskiSelR>=0){
            int[]sz=klotskiBlockSize(klotskiSelR,klotskiSelC);
            int sx=ox+klotskiSelC*(cs+gap)-2,sy=oy+klotskiSelR*(cs+gap)-2;
            MinigameUI.roundBorder(g,sx,sy,sx+sz[1]*(cs+gap)-gap+4,sy+sz[0]*(cs+gap)-gap+4,6,2,YELLOW);
        }
        for(int r=0;r<5;r++)for(int c=0;c<4;c++){
            int x=ox+c*(cs+gap),y=oy+r*(cs+gap);int id=klotskiGrid[r][c];
            MinigameUI.roundRect(g,x,y,x+cs,y+cs,4,id>0?cols[id]:0xFF222233);
            if(id>0&&(r==0||klotskiGrid[r-1][c]!=id)&&(c==0||klotskiGrid[r][c-1]!=id))
                g.drawCenteredString(font,Component.literal(names[id]),x+cs/2,y+cs/2-5,WHITE);
        }
        // 出口标记(底部中间)
        int ex=ox+1*(cs+gap)+cs/2,ey=oy+5*(cs+gap);
        g.fill(ex-12,ey-4,ex+12,ey+4,GREEN);
    }
    private void clickKlotski(double mx,double my){
        int cs=42,gap=2,ox=panelLeft()+(PANEL_W-4*cs-3*gap)/2,oy=panelTop()+30;
        int c=(int)((mx-ox)/(cs+gap)),r=(int)((my-oy)/(cs+gap));
        if(r>=0&&r<5&&c>=0&&c<4&&klotskiGrid[r][c]>0){
            int[]tl=klotskiTopLeft(r,c);klotskiSelR=tl[0];klotskiSelC=tl[1];klotskiStartX=mx;klotskiStartY=my;}
    }

    // ── 内类 ──
    private static class Egg {float x,y;int type;boolean caught;Egg(float x,float y,int t){this.x=x;this.y=y;this.type=t;}}

    private static class Brick {
        int x, y, w, h, color;
        boolean alive = true;
        Brick(int x, int y, int w, int h, int color) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.color = color;
        }
    }

    private static class RunningMouse {
        float fromX, fromY, toX, toY, progress, speed;
        boolean caught;
        RunningMouse(float fromX, float fromY, float toX, float toY, float speed) {
            this.fromX = fromX; this.fromY = fromY; this.toX = toX; this.toY = toY; this.speed = speed;
        }
    }

    private static class Fruit {
        float x, y, vx, vy, rotation;
        int type;
        boolean isBomb, sliced;
        Fruit(float x, float y, float vx, float vy, int type, boolean isBomb) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.type = type; this.isBomb = isBomb;
        }
    }

    private static class PipePair {
        float x;
        int topHeight;
        int bottomY;
        boolean passed;
    }

    private static class Piece {
        final Component label;
        final int shape;
        final int color;
        final int target;
        float x;
        float y;
        boolean placed;
        ItemStack item;

        Piece(Component label, int shape, int color, float x, float y, int target) {
            this.label = label;
            this.shape = shape;
            this.color = color;
            this.x = x;
            this.y = y;
            this.target = target;
            this.item = null;
        }
    }

    private static class Dot {
        final int x;
        final int y;
        final int radius;
        final int kind;
        int life = 45;
        boolean active;

        Dot(int x, int y, int radius, int kind) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.kind = kind;
        }
    }

    private static class Target {
        float x;
        float y;
        float speed;
        int shape;
        boolean hit;

        Target(float x, float y, float speed) {
            this.x = x;
            this.y = y;
            this.speed = speed;
        }

        Target(float x, float y, int shape) {
            this.x = x;
            this.y = y;
            this.shape = shape;
        }
    }

    private static class Note {
        final float x;
        float y;

        Note(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
