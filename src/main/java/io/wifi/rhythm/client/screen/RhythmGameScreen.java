package io.wifi.rhythm.client.screen;

import io.wifi.rhythm.data.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import org.lwjgl.glfw.GLFW;

import java.util.*;

public class RhythmGameScreen extends Screen {
    private static final float NOTE_SPEED = 0.3F;
    private static final int JUDGE_LINE_X = 60;
    private static final int TRACK_Y_UP = 240;
    private static final int TRACK_Y_DOWN = 290;
    private static final int PERFECT_WINDOW = 50;
    private static final int GOOD_WINDOW = 100;
    private static final int MISS_THRESHOLD = 200;

    // 替换为已注册的 SoundEvent（必须先在 ModSounds 中注册）
    private static final SoundEvent CLICK_SOUND = SoundEvents.NOTE_BLOCK_SNARE.value();
    private static final SoundEvent HIT_SOUND = SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();

    private final MapData currentMap;
    private final Deque<Note> pendingNotes;
    private final List<LiveNote> activeNotes = new ArrayList<>();

    private final List<Long> beatTimes = new ArrayList<>();
    private int nextBeatIndex = 0;

    // 游戏状态
    private enum GameState {
        WAITING, PLAYING, PAUSED, FINISHED
    }

    private GameState gameState = GameState.WAITING;

    private long songStartTime = -1;
    private long pauseStart = 0;
    private long totalPauseDuration = 0;

    private final boolean[] trackPressed = new boolean[2];

    private int score = 0;
    private int combo = 0;
    private int maxCombo = 0;
    private int perfectCount = 0;
    private int goodCount = 0;
    private int missCount = 0;

    private Button startButton;

    public RhythmGameScreen(MapData map) {
        super(Component.empty());
        this.currentMap = map;
        List<Note> sorted = new ArrayList<>(map.Notes);
        sorted.sort(Comparator.comparingInt(n -> n.startTime));
        this.pendingNotes = new ArrayDeque<>(sorted);
        buildBeatTimes();
    }

    private void buildBeatTimes() {
        List<Long> times = new ArrayList<>();
        if (currentMap.NoteClick != null) {
            for (NoteClick nc : currentMap.NoteClick) {
                long base = nc.StartTime;
                if (nc.SpecificMidiClick != null) {
                    for (int offset : nc.SpecificMidiClick) {
                        times.add(base + offset);
                    }
                }
            }
        }
        Collections.sort(times);
        this.beatTimes.addAll(times);
    }

    @Override
    protected void init() {
        super.init();
        if (gameState == GameState.WAITING) {
            // 添加开始按钮
            this.startButton = Button.builder(Component.translatable("gui.rhythm.start"), btn -> {
                gameState = GameState.PLAYING;
                playMusic();
                removeWidget(btn);
            })
                    .pos((this.width - 100) / 2, this.height / 2 + 20)
                    .size(100, 20)
                    .build();
            this.addRenderableWidget(startButton);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.VOICE);
        super.onClose();
    }

    @Override
    public void tick() {
        if (gameState != GameState.PLAYING)
            return;

        long currentTime = getCurrentMusicTime();

        while (!pendingNotes.isEmpty()) {
            Note next = pendingNotes.peek();
            if (next.startTime - 800 <= currentTime) {
                pendingNotes.poll();
                activeNotes.add(new LiveNote(next));
            } else
                break;
        }

        Iterator<LiveNote> it = activeNotes.iterator();
        while (it.hasNext()) {
            LiveNote ln = it.next();
            ln.updatePosition(currentTime);
            if (ln.state == NoteState.ACTIVE && ln.currentX < JUDGE_LINE_X - MISS_THRESHOLD) {
                triggerMiss(ln);
                it.remove();
            }
        }

        for (LiveNote ln : activeNotes) {
            if (ln.type == NoteType.HOLD && ln.state == NoteState.HOLDING) {
                if (ln.isHolding()) {
                    if (ln.getTailX() <= JUDGE_LINE_X) {
                        completeHold(ln);
                    }
                } else {
                    breakHold(ln);
                }
            }
        }

        while (nextBeatIndex < beatTimes.size() && beatTimes.get(nextBeatIndex) <= currentTime) {
            playClickSound();
            nextBeatIndex++;
        }

        if (activeNotes.isEmpty() && pendingNotes.isEmpty()) {
            gameState = GameState.FINISHED;
        }
    }

    private long getCurrentMusicTime() {
        if (songStartTime < 0)
            return 0;
        return Math.max(0, System.currentTimeMillis() - songStartTime - totalPauseDuration);
    }

    private void playMusic() {
        ResourceLocation soundLocation = ResourceLocation.tryParse(currentMap.Src);
        Minecraft.getInstance().getSoundManager().play(
                new SimpleSoundInstance(soundLocation, SoundSource.VOICE, 1.0F, 1.0F,
                        RandomSource.create(), false, 0, SimpleSoundInstance.Attenuation.NONE, 0, 0, 0, true));
        songStartTime = System.currentTimeMillis();
    }

    // ===== 渲染 =====
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTracks(graphics);

        for (LiveNote ln : activeNotes) {
            drawNote(graphics, ln);
        }

        drawHUD(graphics);

        if (gameState == GameState.WAITING) {
            drawCenteredString(graphics, font, Component.translatable("gui.rhythm.waiting"), width / 2, height / 2 - 10,
                    0xFFFFFF);
        } else if (gameState == GameState.FINISHED) {
            drawResult(graphics);
        }

    }

    private void drawTracks(GuiGraphics graphics) {
        graphics.fill(JUDGE_LINE_X, TRACK_Y_UP - 12, width, TRACK_Y_UP + 12, 0x22000000);
        graphics.fill(JUDGE_LINE_X, TRACK_Y_DOWN - 12, width, TRACK_Y_DOWN + 12, 0x22000000);
        graphics.fill(JUDGE_LINE_X - 1, TRACK_Y_UP - 20, JUDGE_LINE_X + 1, TRACK_Y_DOWN + 20, 0xFFFFFFFF);
        graphics.drawString(font, "▲", JUDGE_LINE_X - 20, TRACK_Y_UP - 8, 0xFFFFAA00);
        graphics.drawString(font, "▼", JUDGE_LINE_X - 20, TRACK_Y_DOWN - 8, 0xFFAA00FF);
    }

    private void drawNote(GuiGraphics graphics, LiveNote ln) {
        int y = ln.track == 0 ? TRACK_Y_UP : TRACK_Y_DOWN;
        int x = ln.currentX;
        switch (ln.type) {
            case SINGLE, HOLDSINGLE -> {
                int color = ln.track == 0 ? 0xFFFFAA00 : 0xFFAA00FF;
                if (ln.type == NoteType.HOLDSINGLE)
                    color = 0xFFFF5500;
                int size = 8;
                graphics.fill(x - size, y - size, x + size, y + size, color);
                graphics.renderOutline(x - size, y - size, size * 2, size * 2, 0xFFFFFFFF);
            }
            case HOLD -> {
                int headX = x;
                long currentTime = getCurrentMusicTime();
                int tailX = JUDGE_LINE_X + (int) ((ln.note.endTime - currentTime) * NOTE_SPEED);
                if (tailX < headX)
                    tailX = headX;
                int color = ln.held ? 0xFF00AA00 : (ln.track == 0 ? 0xFFFFAA00 : 0xFFAA00FF);
                graphics.fill(headX - 4, y - 6, tailX + 4, y + 6, color);
            }
        }
    }

    private void drawHUD(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("gui.rhythm.score", score), 10, 10, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.rhythm.combo", combo), 10, 24, 0xFFFFAA00);
        int total = perfectCount + goodCount + missCount;
        if (total > 0) {
            double accuracy = (perfectCount * 100.0 + goodCount * 70.0) / total;
            graphics.drawString(font, Component.translatable("gui.rhythm.accuracy", String.format("%.1f%%", accuracy)),
                    10, 38, 0xFFFFFF);
        }
        if (gameState != GameState.WAITING) {
            graphics.drawString(font, Component.translatable("gui.rhythm.hint.up"), width - 200, height - 40, 0xAAAAAA);
            graphics.drawString(font, Component.translatable("gui.rhythm.hint.down"), width - 200, height - 20,
                    0xAAAAAA);
        }
    }

    private void drawResult(GuiGraphics graphics) {
        drawCenteredString(graphics, font, Component.translatable("gui.rhythm.result.title"), width / 2,
                height / 2 - 30, 0xFFFFAA00);
        drawCenteredString(graphics, font, Component.translatable("gui.rhythm.result.score", score), width / 2,
                height / 2 - 10, 0xFFFFFF);
        drawCenteredString(graphics, font, Component.translatable("gui.rhythm.result.max_combo", maxCombo), width / 2,
                height / 2 + 10, 0xFFFFFF);
        drawCenteredString(graphics, font, Component.translatable("gui.rhythm.back"), width / 2, height / 2 + 40,
                0xFFAAAAAA);
    }

    private void drawCenteredString(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        graphics.drawCenteredString(font, text, x, y, color);
    }

    // ===== 输入 =====
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (gameState == GameState.FINISHED) {
            onClose();
            return true;
        }
        if (gameState == GameState.WAITING) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (gameState != GameState.PLAYING)
            return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressTrack(0);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            pressTrack(1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            releaseTrack(0);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            releaseTrack(1);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (gameState == GameState.PLAYING) {
            if (keyCode == GLFW.GLFW_KEY_W) {
                pressTrack(0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_S) {
                pressTrack(1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_SPACE) {
                togglePause();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_W) {
            releaseTrack(0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S) {
            releaseTrack(1);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void pressTrack(int track) {
        trackPressed[track] = true;
        long now = getCurrentMusicTime();
        for (LiveNote ln : activeNotes) {
            if (ln.track != track || ln.state != NoteState.ACTIVE)
                continue;
            if (ln.type == NoteType.SINGLE || ln.type == NoteType.HOLDSINGLE) {
                long diff = Math.abs(now - ln.note.startTime);
                if (diff <= PERFECT_WINDOW) {
                    hitNote(ln, true);
                    return;
                } else if (diff <= GOOD_WINDOW) {
                    hitNote(ln, false);
                    return;
                }
            } else if (ln.type == NoteType.HOLD) {
                long diff = Math.abs(now - ln.note.startTime);
                if (diff <= GOOD_WINDOW) {
                    startHold(ln);
                    return;
                }
            }
        }
    }

    private void releaseTrack(int track) {
        trackPressed[track] = false;
    }

    // ===== 判定 =====
    private void hitNote(LiveNote note, boolean perfect) {
        note.state = NoteState.HIT;
        score += (perfect ? 100 : 70) * comboMultiplier();
        if (perfect)
            perfectCount++;
        else
            goodCount++;
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        playHitSound();
    }

    private void triggerMiss(LiveNote note) {
        note.state = NoteState.MISSED;
        combo = 0;
        missCount++;
    }

    private void startHold(LiveNote note) {
        note.state = NoteState.HOLDING;
        note.held = true;
        score += 50 * comboMultiplier();
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        playHitSound();
    }

    private void completeHold(LiveNote note) {
        note.state = NoteState.HIT;
        score += 200 * comboMultiplier();
        combo++;
        maxCombo = Math.max(maxCombo, combo);
    }

    private void breakHold(LiveNote note) {
        note.state = NoteState.MISSED;
        note.held = false;
        combo = 0;
        missCount++;
    }

    private int comboMultiplier() {
        return combo >= 100 ? 2 : (combo >= 50 ? 1 : 0);
    }

    // ===== 音效（使用已注册的 SoundEvent） =====
    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(CLICK_SOUND, 0.8F, 1.0F));
    }

    private void playHitSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(HIT_SOUND, 1.0F, 1.0F));
    }

    // ===== 暂停 =====
    private void togglePause() {
        if (gameState == GameState.PLAYING) {
            gameState = GameState.PAUSED;
            pauseStart = System.currentTimeMillis();
            Minecraft.getInstance().getSoundManager().pause();
        } else if (gameState == GameState.PAUSED) {
            gameState = GameState.PLAYING;
            totalPauseDuration += System.currentTimeMillis() - pauseStart;
            Minecraft.getInstance().getSoundManager().resume();
        }
    }

    // ===== 内部类 =====
    private enum NoteType {
        SINGLE, HOLD, HOLDSINGLE
    }

    private enum NoteState {
        ACTIVE, HOLDING, HIT, MISSED
    }

    private class LiveNote {
        final Note note;
        final NoteType type;
        final int track;
        NoteState state = NoteState.ACTIVE;
        int currentX;
        boolean held;

        LiveNote(Note n) {
            this.note = n;
            this.type = switch (n.noteType) {
                case "Single" -> NoteType.SINGLE;
                case "Hold" -> NoteType.HOLD;
                case "HoldSingle" -> NoteType.HOLDSINGLE;
                default -> NoteType.SINGLE;
            };
            this.track = "Left".equals(n.positionType) ? 0 : 1;
        }

        void updatePosition(long currentTime) {
            currentX = JUDGE_LINE_X + (int) ((note.startTime - currentTime) * NOTE_SPEED);
        }

        int getTailX() {
            if (type != NoteType.HOLD)
                return currentX;
            return JUDGE_LINE_X + (int) ((note.endTime - getCurrentMusicTime()) * NOTE_SPEED);
        }

        boolean isHolding() {
            return trackPressed[track];
        }
    }

    public static void open(MapData map) {
        Minecraft.getInstance().setScreen(new RhythmGameScreen(map));
    }
}