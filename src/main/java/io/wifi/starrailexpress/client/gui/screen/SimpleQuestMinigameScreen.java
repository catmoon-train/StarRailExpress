package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Shared lightweight GUI for the map-task minigames that do not need custom assets.
 */
public class SimpleQuestMinigameScreen extends Screen {

    public enum Mode {
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
        ZONE_CALIBRATION("zone_calibration");

        private final String id;

        Mode(String id) {
            this.id = id;
        }
    }

    private static final int PANEL_W = 430;
    private static final int PANEL_H = 270;
    private static final int WHITE = 0xFFE8EEF8;
    private static final int GREEN = 0xFF4ACB73;
    private static final int RED = 0xFFFF6B6B;
    private static final int YELLOW = 0xFFFFD166;
    private static final int BLUE = 0xFF4A8BFF;
    private static final int PANEL = 0xEE263246;
    private static final int PANEL_DARK = 0xFF162130;

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
    private boolean falling;
    private boolean armed;
    private float fallingY;

    public SimpleQuestMinigameScreen(BlockPos questPos, Runnable onSuccess, Mode mode) {
        super(Component.translatable("minigame.starrailexpress." + mode.id));
        this.onSuccess = onSuccess;
        this.mode = mode;
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
                        pieces.add(new Piece(shapeName(shape), shape, shapeColor(shape),
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
            }
            case SIGNAL_CALIBRATION -> {
                valueA = 0.15f;
                valueB = 0.85f;
            }
            case SHAPE_MATCH -> {
                for (int shape = 0; shape < 4; shape++) {
                    targets.add(new Target(left + 75 + shape * 90, top + 75 + (shape % 2) * 45, shape));
                    pieces.add(new Piece(shapeName(shape), shape, shapeColor(shape),
                            left + 80 + shape * 78, top + 205, shape));
                }
                Collections.shuffle(pieces, rng);
            }
            case CLEAN_STAINS -> {
                for (int i = 0; i < 16; i++) {
                    dots.add(new Dot(left + 70 + rng.nextInt(290), top + 55 + rng.nextInt(145), 15, 4));
                }
                pieces.add(new Piece("Sponge", 1, 0xFFFFFF88, left + 185, top + 215, -1));
            }
            case TRASH_RECYCLE -> {
                String[] names = {"Bottle", "Can", "Paper", "Core", "Peel", "Dust"};
                for (int i = 0; i < names.length; i++) {
                    pieces.add(new Piece(names[i], i % 3, i < 3 ? BLUE : YELLOW,
                            left + 45 + i * 60, top + 205, i < 3 ? 0 : 1));
                }
            }
            case ITEM_CHECKLIST -> {
                List<String> names = new ArrayList<>(List.of("Key", "Wire", "Cup", "Chip", "Tape", "Gear", "Pen"));
                Collections.shuffle(names, rng);
                for (int i = 0; i < 3; i++) selectedIndices.add(i);
                for (int i = 0; i < names.size(); i++) {
                    pieces.add(new Piece(names.get(i), 1, i < 3 ? GREEN : 0xFF6B7890,
                            left + 240 + (i % 2) * 80, top + 55 + (i / 2) * 42, i < 3 ? 1 : 0));
                }
            }
            case SWIPE_CARD -> pieces.add(new Piece("CARD", 1, 0xFF66BBFF, left + 65, top + 110, 1));
            case PLAY_MUSIC -> {
                correctRecord = rng.nextInt(4);
                for (int i = 0; i < 4; i++) {
                    pieces.add(new Piece("Disc " + (i + 1), 0, i == correctRecord ? GREEN : 0xFF7E6BEF,
                            left + 80 + i * 70, top + 205, i == correctRecord ? 1 : 0));
                }
            }
            case RHYTHM -> spawnTimer = 20;
            case LIGHT_CANDLES -> {
                for (int i = 0; i < 3; i++) {
                    Dot candle = new Dot(left + 120 + i * 90, top + 120, 0, 0);
                    candle.life = 0;
                    dots.add(candle);
                }
                pieces.add(new Piece("Flint", 2, 0xFFAAAAAA, left + 190, top + 215, -1));
            }
            case WHACK_MOLE -> spawnMole();
            case PUZZLE -> {
                for (int i = 0; i < 8; i++) {
                    pieces.add(new Piece(String.valueOf(i + 1), 1, 0xFF6AA6FF,
                            left + 40 + (i % 4) * 45, top + 190 + (i / 4) * 35, i));
                }
                Collections.shuffle(pieces, rng);
            }
            case STORAGE -> {
                String[] names = {"Book", "Cup", "Tool", "Sock", "Map", "Box"};
                for (int i = 0; i < names.length; i++) {
                    pieces.add(new Piece(names[i], i % 4, shapeColor(i % 4),
                            left + 55 + i * 58, top + 80 + rng.nextInt(80), 1));
                }
            }
            case THREE_CARDS -> {
                highlightedCard = rng.nextInt(3);
                for (int i = 0; i < 3; i++) selectedIndices.add(i);
            }
            case BREAK_JAR -> pieces.add(new Piece("Jar", 0, 0xFFC98C58, width / 2f - 18, top + 205, 1));
            case ZONE_CALIBRATION -> {
                progress = 35;
                targetTemp = 46 + rng.nextInt(12);
            }
            default -> {
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
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
                float clarity = 1f - (Math.abs(valueA - 0.5f) + Math.abs(valueB - 0.5f));
                if (clarity > 0.94f) complete();
            }
            case SIGNAL_CALIBRATION -> {
                if (Math.abs(valueA - 0.30f) < 0.025f && Math.abs(valueB - 0.70f) < 0.025f) complete();
            }
            case CLEAN_STAINS -> {
                if (draggedPiece != null) {
                    for (Dot dot : dots) {
                        if (dot.life > 0 && distance(draggedPiece.x + 18, draggedPiece.y + 18, dot.x, dot.y) < 28) {
                            dot.life--;
                        }
                    }
                    if (dots.stream().allMatch(dot -> dot.life <= 0)) complete();
                }
            }
            case RHYTHM -> tickRhythm();
            case LIGHT_CANDLES -> tickCandles();
            case WHACK_MOLE -> {
                if (--spawnTimer <= 0) spawnMole();
            }
            case THREE_CARDS -> {
                animationTicks++;
                if (animationTicks > 50 && animationTicks % 22 == 0 && animationTicks < 150) {
                    Collections.swap(selectedIndices, rng.nextInt(3), rng.nextInt(3));
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
        dots.clear();
        int left = panelLeft();
        int top = panelTop();
        for (int i = 0; i < 9; i++) {
            Dot dot = new Dot(left + 115 + (i % 3) * 90, top + 70 + (i / 3) * 50, 0, 0);
            dot.active = false;
            dots.add(dot);
        }
        dots.get(rng.nextInt(dots.size())).active = true;
        spawnTimer = 35 + rng.nextInt(25);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int top = panelTop();
        g.fill(left, top, left + PANEL_W, top + PANEL_H, PANEL);
        g.fill(left, top, left + PANEL_W, top + 22, 0xFF3E5D83);
        g.drawCenteredString(font, title, width / 2, top + 7, WHITE);
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
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderTemperature(GuiGraphics g, int left, int top) {
        int barX = left + 135;
        int barY = top + 58;
        int barH = 145;
        g.drawCenteredString(font, Component.literal("Target " + targetTemp + "F"), barX + 18, barY - 18, YELLOW);
        g.drawCenteredString(font, Component.literal("Current " + currentTemp + "F"), barX + 18, barY + barH + 10, WHITE);
        g.fill(barX, barY, barX + 36, barY + barH, PANEL_DARK);
        int fillH = Mth.clamp((currentTemp - 90) * barH / 70, 0, barH);
        g.fill(barX + 4, barY + barH - fillH, barX + 32, barY + barH - 4, RED);
        int targetY = barY + barH - Mth.clamp((targetTemp - 90) * barH / 70, 0, barH);
        g.fill(barX - 8, targetY - 1, barX + 44, targetY + 1, YELLOW);
        drawButton(g, left + 260, top + 72, 72, 44, "^");
        drawButton(g, left + 260, top + 142, 72, 44, "v");
    }

    private void renderBoxSort(GuiGraphics g, int left, int top) {
        for (int i = 0; i < 3; i++) {
            int y = top + 48 + i * 45;
            g.fill(left + 70, y, left + 350, y + 34, PANEL_DARK);
            drawShape(g, i, left + 92, y + 17, 13, 0x55FFFFFF, true);
            g.drawString(font, shapeName(i), left + 118, y + 13, WHITE);
        }
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderWireConnect(GuiGraphics g, int left, int top) {
        int[] topColors = {RED, YELLOW, BLUE};
        int[] bottomColors = {BLUE, RED, YELLOW};
        for (int i = 0; i < 3; i++) {
            int tx = left + 130 + i * 85;
            int bx = left + 130 + i * 85;
            drawCircle(g, tx, top + 72, 12, topColors[i]);
            drawCircle(g, bx, top + 192, 12, bottomColors[i]);
            int b = connections.get(i);
            if (b >= 0) drawSteppedLine(g, tx, top + 84, left + 130 + b * 85, top + 180, topColors[i]);
        }
        g.drawCenteredString(font, Component.literal("Connect matching colors"), width / 2, top + 34, WHITE);
    }

    private void renderHoldButton(GuiGraphics g, int left, int top) {
        drawCircle(g, width / 2, top + 130, 40, 0xFFB72E3A);
        drawCircle(g, width / 2, top + 130, 26, mouseHeld ? 0xFFFF5A5A : RED);
        drawProgress(g, left + 100, top + 200, 230, 14, progress / 100f);
    }

    private void renderDebris(GuiGraphics g, int left, int top) {
        g.fill(left + 58, top + 42, left + 372, top + 215, 0xFF475468);
        for (Dot dot : dots) drawDebris(g, dot);
        g.drawCenteredString(font, Component.literal("Click all debris"), width / 2, top + 226, WHITE);
    }

    private void renderShooting(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, Component.literal("Hits " + successCount + " / 5"), width / 2, top + 34, WHITE);
        targets.forEach(target -> {
            drawCircle(g, Math.round(target.x), Math.round(target.y), 14, RED);
            drawCircle(g, Math.round(target.x), Math.round(target.y), 7, WHITE);
        });
        g.fill(width / 2 - 24, top + 220, width / 2 + 24, top + 240, 0xFF7D8797);
        g.fill(width / 2 - 5, top + 192, width / 2 + 5, top + 220, 0xFFB8C0CC);
        if (bulletActive) drawCircle(g, Math.round(bulletX), Math.round(bulletY), 5, YELLOW);
    }

    private void renderWireTuning(GuiGraphics g, int left, int top) {
        float clarity = Mth.clamp(1f - (Math.abs(valueA - 0.5f) + Math.abs(valueB - 0.5f)), 0f, 1f);
        g.fill(left + 120, top + 70, left + 310, top + 175, 0xFF04070C);
        g.fill(left + 130, top + 80, left + 300, top + 165, ((int) (clarity * 255) << 24) | 0x0055CCFF);
        g.drawCenteredString(font, Component.literal("TV clarity"), width / 2, top + 48, WHITE);
        for (int i = 0; i < 2; i++) {
            float value = i == 0 ? valueA : valueB;
            int x = left + 170 + i * 85;
            int endX = x + Math.round((value - 0.5f) * 92);
            drawSteppedLine(g, x, top + 58, endX, top + 42, i == 0 ? RED : BLUE);
            drawCircle(g, endX, top + 42, 8, i == 0 ? RED : BLUE);
        }
    }

    private void renderSignal(GuiGraphics g, int left, int top) {
        int cx = width / 2;
        int cy = top + 155;
        drawArcTicks(g, cx, cy, 115);
        drawRadial(g, cx, cy, 100, 0.30f, 0x88FFFFFF);
        drawRadial(g, cx, cy, 100, 0.70f, 0x88FFFFFF);
        drawRadial(g, cx, cy, 100, valueA, RED);
        drawRadial(g, cx, cy, 100, valueB, RED);
        drawKnob(g, left + 160, top + 218, valueA);
        drawKnob(g, left + 270, top + 218, valueB);
    }

    private void renderShapeMatch(GuiGraphics g, int left, int top) {
        for (Target target : targets) drawShape(g, target.shape, Math.round(target.x), Math.round(target.y), 20, 0x55FFFFFF, true);
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderSequence(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, Component.literal("Click in order"), width / 2, top + 38, WHITE);
        for (int i = 0; i < 6; i++) {
            int x = left + 90 + (i % 3) * 100;
            int y = top + 75 + (i / 3) * 75;
            int color = i < nextIndex ? GREEN : i == nextIndex ? YELLOW : 0xFF596579;
            g.fill(x, y, x + 56, y + 42, color);
            g.drawCenteredString(font, Component.literal(String.valueOf(i + 1)), x + 28, y + 17, 0xFF10131A);
        }
    }

    private void renderStains(GuiGraphics g, int left, int top) {
        g.fill(left + 58, top + 42, left + 372, top + 205, 0xFFB8B0A0);
        for (Dot dot : dots) {
            if (dot.life > 0) drawCircle(g, dot.x, dot.y, 6 + dot.life / 3, 0xAA3A2A20);
        }
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderLever(GuiGraphics g, int left, int top) {
        int railX = width / 2;
        int minY = top + 68;
        int maxY = top + 205;
        g.fill(railX - 8, minY, railX + 8, maxY, 0xFF242D3A);
        int knobY = Math.round(Mth.lerp(valueA, minY, maxY));
        drawCircle(g, railX, knobY, 24, RED);
        g.drawCenteredString(font, Component.literal("Drag lever down"), width / 2, top + 35, WHITE);
    }

    private void renderTrash(GuiGraphics g, int left, int top) {
        g.fill(left + 78, top + 55, left + 188, top + 145, 0xFF2E5C91);
        g.fill(left + 242, top + 55, left + 352, top + 145, 0xFF5E8F42);
        g.drawCenteredString(font, Component.literal("Recycle"), left + 133, top + 92, WHITE);
        g.drawCenteredString(font, Component.literal("Other"), left + 297, top + 92, WHITE);
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderChecklist(GuiGraphics g, int left, int top) {
        g.drawString(font, Component.literal("Needed"), left + 75, top + 50, YELLOW);
        for (int i = 0; i < 3; i++) {
            Piece p = pieces.get(i);
            g.drawString(font, Component.literal("- " + p.label), left + 75, top + 73 + i * 24, WHITE);
        }
        g.drawString(font, Component.literal("Items"), left + 240, top + 50, YELLOW);
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            int color = p.placed ? GREEN : p.color;
            g.fill(Math.round(p.x), Math.round(p.y), Math.round(p.x + 70), Math.round(p.y + 26), color);
            g.drawCenteredString(font, Component.literal(p.label), Math.round(p.x + 35), Math.round(p.y + 9), 0xFF10131A);
        }
    }

    private void renderSwipe(GuiGraphics g, int left, int top) {
        Piece card = pieces.isEmpty() ? null : pieces.get(0);
        g.fill(left + 275, top + 62, left + 340, top + 205, 0xFF273142);
        g.fill(left + 285, top + 82, left + 330, top + 92, 0xFF0D1118);
        g.drawCenteredString(font, Component.literal("Swipe down"), left + 307, top + 215, WHITE);
        drawPiece(g, card);
    }

    private void renderMusic(GuiGraphics g, int left, int top) {
        drawCircle(g, width / 2, top + 105, 45, 0xFF38445A);
        drawCircle(g, width / 2, top + 105, 18, 0xFF0F1520);
        g.fill(width / 2 + 36, top + 72, width / 2 + 95, top + 82, 0xFFB9C2D1);
        g.drawCenteredString(font, Component.literal("Play Disc " + (correctRecord + 1)), width / 2, top + 34, YELLOW);
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderRhythm(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, Component.literal("Hits " + successCount + " / 5"), width / 2, top + 35, WHITE);
        int lineY = top + 195;
        g.fill(left + 70, lineY, left + 360, lineY + 3, YELLOW);
        for (Note note : notes) drawCircle(g, Math.round(note.x), Math.round(note.y), 10, BLUE);
    }

    private void renderCandles(GuiGraphics g, int left, int top) {
        for (Dot candle : dots) {
            g.fill(candle.x - 9, candle.y - 5, candle.x + 9, candle.y + 45, 0xFFFFF1C7);
            if (candle.life >= 45) drawCircle(g, candle.x, candle.y - 12, 10, YELLOW);
            else drawProgress(g, candle.x - 18, candle.y + 52, 36, 4, candle.life / 45f);
        }
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderMole(GuiGraphics g, int left, int top) {
        g.drawCenteredString(font, Component.literal("Hits " + successCount + " / 5"), width / 2, top + 36, WHITE);
        for (Dot dot : dots) {
            drawCircle(g, dot.x, dot.y, 20, 0xFF3A2B1F);
            if (dot.active) {
                drawCircle(g, dot.x, dot.y - 8, 16, 0xFFB77B46);
                g.drawCenteredString(font, Component.literal("!"), dot.x, dot.y - 13, WHITE);
            }
        }
    }

    private void renderPuzzle(GuiGraphics g, int left, int top) {
        int startX = left + 155;
        int startY = top + 52;
        for (int i = 0; i < 9; i++) {
            g.fill(startX + (i % 3) * 42, startY + (i / 3) * 42,
                    startX + (i % 3) * 42 + 38, startY + (i / 3) * 42 + 38, 0x553F5574);
        }
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderStorage(GuiGraphics g, int left, int top) {
        g.fill(left + 115, top + 190, left + 315, top + 245, 0xFF5C6F88);
        g.drawCenteredString(font, Component.literal("Storage Bag"), width / 2, top + 211, WHITE);
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderSlice(GuiGraphics g, int left, int top) {
        g.fill(left + 105, top + 105, left + 325, top + 165, 0xFFFFB65C);
        for (int i = 1; i <= 4; i++) {
            int x = left + 105 + i * 44;
            int color = i <= progress ? GREEN : 0x66FFFFFF;
            g.fill(x - 1, top + 98, x + 1, top + 172, color);
        }
        g.drawCenteredString(font, Component.literal("Click the guide lines"), width / 2, top + 195, WHITE);
    }

    private void renderCards(GuiGraphics g, int left, int top) {
        boolean faceDown = animationTicks > 50;
        g.drawCenteredString(font, Component.literal(faceDown ? "Pick the highlighted card" : "Remember it"), width / 2, top + 36, WHITE);
        for (int i = 0; i < 3; i++) {
            int slot = selectedIndices.get(i);
            int x = left + 108 + i * 90;
            int y = top + 92;
            int color = !faceDown && slot == highlightedCard ? YELLOW : 0xFFE8EEF8;
            g.fill(x, y, x + 58, y + 82, faceDown ? 0xFF344667 : color);
            g.drawCenteredString(font, Component.literal(faceDown ? "?" : String.valueOf(slot + 1)), x + 29, y + 36, 0xFF10131A);
        }
    }

    private void renderJar(GuiGraphics g, int left, int top) {
        g.fill(left + 40, top + 218, left + 390, top + 242, 0xFF485464);
        g.drawCenteredString(font, Component.literal("Drag the jar up and release"), width / 2, top + 35, WHITE);
        pieces.forEach(piece -> drawPiece(g, piece));
    }

    private void renderZone(GuiGraphics g, int left, int top) {
        int barX = left + 85;
        int barY = top + 105;
        int barW = 260;
        drawProgress(g, barX, barY, barW, 18, progress / 100f);
        int zx = barX + (targetTemp - 7) * barW / 100;
        int zw = 14 * barW / 100;
        g.fill(zx, barY - 8, zx + zw, barY + 26, 0x664ACB73);
        g.drawCenteredString(font, Component.literal("Hold in zone: " + (clearTicks / 20) + " / 5s"), width / 2, top + 145, WHITE);
        drawButton(g, width / 2 - 42, top + 185, 84, 28, "Push");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
            case SLICE_FOOD -> clickSlice(mouseX, mouseY);
            case THREE_CARDS -> clickCard(mouseX, mouseY);
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
            if (!p.placed && inRect(mouseX, mouseY, p.x, p.y, 70, 26)) {
                if (p.target == 1) {
                    p.placed = true;
                    if (pieces.stream().filter(piece -> piece.target == 1).allMatch(piece -> piece.placed)) complete();
                } else {
                    selectedIndices.clear();
                }
            }
        }
    }

    private void clickMole(double mouseX, double mouseY) {
        for (Dot dot : dots) {
            if (dot.active && inCircle(mouseX, mouseY, dot.x, dot.y, 24)) {
                successCount++;
                if (successCount >= 5) complete();
                else spawnMole();
                return;
            }
        }
    }

    private void clickSlice(double mouseX, double mouseY) {
        int left = panelLeft();
        int top = panelTop();
        for (int i = 1; i <= 4; i++) {
            int x = left + 105 + i * 44;
            if (i == progress + 1 && Math.abs(mouseX - x) < 12 && mouseY >= top + 92 && mouseY <= top + 178) {
                progress++;
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
        lastMouseX = mouseX;
        lastMouseY = mouseY;
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
            float v = Mth.clamp(((float) mouseX - (panelLeft() + 170 + selectedWire * 85) + 46) / 92f, 0f, 1f);
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseHeld = false;
        selectedWire = -1;
        selectedKnob = -1;
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
                int sx = left + 155 + (p.target % 3) * 42;
                int sy = top + 52 + (p.target / 3) * 42;
                if (distance(p.x, p.y, sx, sy) < 36) placePiece(p, sx, sy);
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (mode == Mode.SWIPE_CARD && mouseX >= panelLeft() + 275 && mouseX <= panelLeft() + 340 && mouseY <= panelTop() + 95) {
            armed = true;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xD010131A, 0xD016213E);
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
        onSuccess.run();
        onClose();
    }

    private int nearestWire(double mouseX, double mouseY) {
        for (int i = 0; i < 2; i++) {
            int x = panelLeft() + 170 + i * 85 + Math.round(((i == 0 ? valueA : valueB) - 0.5f) * 92);
            if (inCircle(mouseX, mouseY, x, panelTop() + 42, 16)) return i;
        }
        return -1;
    }

    private int nearestKnob(double mouseX, double mouseY) {
        if (inCircle(mouseX, mouseY, panelLeft() + 160, panelTop() + 218, 22)) return 0;
        if (inCircle(mouseX, mouseY, panelLeft() + 270, panelTop() + 218, 22)) return 1;
        return -1;
    }

    private void drawPiece(GuiGraphics g, Piece p) {
        if (p.shape >= 0 && p.shape <= 3) {
            drawShape(g, p.shape, Math.round(p.x + 20), Math.round(p.y + 16), 16, p.color, false);
        } else {
            g.fill(Math.round(p.x), Math.round(p.y), Math.round(p.x + 42), Math.round(p.y + 32), p.color);
        }
        g.drawCenteredString(font, Component.literal(p.label), Math.round(p.x + 21), Math.round(p.y + 37), WHITE);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label) {
        g.fill(x, y, x + w, y + h, 0xFF4B607C);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF6F86A6);
        g.drawCenteredString(font, Component.literal(label), x + w / 2, y + h / 2 - 4, 0xFF10131A);
    }

    private void drawProgress(GuiGraphics g, int x, int y, int w, int h, float value) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF5A6B82);
        g.fill(x, y, x + w, y + h, PANEL_DARK);
        g.fill(x, y, x + Math.round(w * Mth.clamp(value, 0f, 1f)), y + h, GREEN);
    }

    private void drawShape(GuiGraphics g, int shape, int cx, int cy, int r, int color, boolean outline) {
        if (shape == 0) {
            drawCircle(g, cx, cy, r, color);
            if (outline) drawCircle(g, cx, cy, Math.max(2, r - 4), PANEL);
        } else if (shape == 1) {
            g.fill(cx - r, cy - r, cx + r, cy + r, color);
            if (outline) g.fill(cx - r + 4, cy - r + 4, cx + r - 4, cy + r - 4, PANEL);
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
        for (int y = -r; y <= r; y++) {
            int half = (int) Math.sqrt(r * r - y * y);
            g.fill(cx - half, cy + y, cx + half + 1, cy + y + 1, color);
        }
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

    private String shapeName(int shape) {
        return switch (shape) {
            case 0 -> "Circle";
            case 1 -> "Square";
            case 2 -> "Triangle";
            default -> "Cross";
        };
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

    private static class Piece {
        final String label;
        final int shape;
        final int color;
        final int target;
        float x;
        float y;
        boolean placed;

        Piece(String label, int shape, int color, float x, float y, int target) {
            this.label = label;
            this.shape = shape;
            this.color = color;
            this.x = x;
            this.y = y;
            this.target = target;
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
