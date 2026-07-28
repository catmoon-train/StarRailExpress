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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class RhythmGameScreen extends Screen {
    private static final float NOTE_SPEED = 0.2F;
    private static final int JUDGE_LINE_X = 60;
    private static final int PERFECT_WINDOW = 80;
    private static final int GOOD_WINDOW = 150;
    private static final int MISS_THRESHOLD = 50;
    private static final int ADVANCE_DISPLAY_TIME = 3900;

    private int getTrackY(int track) {
        int centerY = this.height / 2;
        int spacing = 50; // 两轨间距
        return track == 0 ? centerY - spacing / 2 : centerY + spacing / 2;
    }

    private static final SoundEvent CLICK_SOUND = SoundEvents.NOTE_BLOCK_SNARE.value();
    private static final SoundEvent HIT_SOUND = SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();

    private static final long MUSIC_DELAY_MS = 3000;
    private static final long END_DELAY_MS = 2000;

    private final MapData currentMap;
    private final Deque<Note> pendingNotes;
    private final List<LiveNote> activeNotes = new ArrayList<>();
    private final List<Long> beatTimes = new ArrayList<>();
    private int nextBeatIndex = 0;

    private enum GameState {
        WAITING, PLAYING, PAUSED, FINISHED
    }

    private GameState gameState = GameState.WAITING;

    private long musicStartTime = -1;
    private long songStartTime = -1;
    private long pauseStart = 0;
    private long totalPauseDuration = 0;
    private long allNotesProcessedTime = -1;

    private final boolean[] trackPressed = new boolean[2];

    private int score = 0, combo = 0, maxCombo = 0;
    private int perfectCount = 0, goodCount = 0, missCount = 0;

    // 击中特效
    private final List<HitEffect> hitEffects = new ArrayList<>();

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
            this.startButton = Button.builder(Component.translatable("gui.rhythm.start"), btn -> {
                gameState = GameState.PLAYING;
                musicStartTime = System.currentTimeMillis() + MUSIC_DELAY_MS;
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

        // 检查音乐是否该开始了
        if (songStartTime < 0 && musicStartTime > 0 && System.currentTimeMillis() >= musicStartTime) {
            playMusic();
            songStartTime = System.currentTimeMillis();
        }

        long currentTime = getCurrentMusicTime();
        boolean musicStarted = (songStartTime >= 0); // 音乐是否已经播放

        // 1. 将新音符加入活跃列表（即使在音乐开始前也允许，但只显示不判定）
        while (!pendingNotes.isEmpty()) {
            Note next = pendingNotes.peek();
            if (next.startTime - ADVANCE_DISPLAY_TIME <= currentTime) {
                pendingNotes.poll();
                LiveNote ln = new LiveNote(next);
                // 初始化位置，避免首帧跳动
                ln.updatePosition(currentTime);
                ln.prevX = ln.currentX;
                activeNotes.add(ln);
            } else
                break;
        }

        // 2. 更新所有活跃音符位置
        for (LiveNote ln : activeNotes) {
            ln.updatePosition(currentTime);
        }

        // 3. 仅在音乐开始后进行判定处理
        if (musicStarted) {
            // 3a. HoldSingle 自动连打（长按效果）
            for (int track = 0; track < 2; track++) {
                if (trackPressed[track]) {
                    // 寻找第一个未处理的 HoldSingle 音符
                    for (LiveNote ln : activeNotes) {
                        if (ln.track == track && ln.type == NoteType.HOLDSINGLE && ln.state == NoteState.ACTIVE) {
                            if (currentTime >= ln.note.startTime) {
                                long diff = currentTime - ln.note.startTime;
                                if (diff <= PERFECT_WINDOW) {
                                    hitNote(ln, true);
                                } else {
                                    hitNote(ln, false);
                                }
                                break; // 每次 tick 只命中一个，防止一帧内扫光所有
                            }
                        }
                    }
                }
            }

            // 3b. 移除已处理的音符，以及检查普通 miss
            Iterator<LiveNote> it = activeNotes.iterator();
            while (it.hasNext()) {
                LiveNote ln = it.next();
                if (ln.state == NoteState.HIT || ln.state == NoteState.MISSED) {
                    it.remove();
                    continue;
                }
                // 如果音符还活跃，并且已经越过判定线太远 → miss
                if (ln.state == NoteState.ACTIVE && ln.currentX < JUDGE_LINE_X - MISS_THRESHOLD) {
                    triggerMiss(ln);
                    it.remove();
                }
            }

            // 3c. Hold 长按持续检测
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

            // 3d. 播放节拍音效
            while (nextBeatIndex < beatTimes.size() && beatTimes.get(nextBeatIndex) <= currentTime) {
                playClickSound();
                nextBeatIndex++;
            }
        }

        // 4. 更新特效
        hitEffects.removeIf(e -> System.currentTimeMillis() - e.startTime > 800);

        // 5. 结束检测（只在音乐开始后）
        if (musicStarted && activeNotes.isEmpty() && pendingNotes.isEmpty()) {
            if (allNotesProcessedTime < 0) {
                allNotesProcessedTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - allNotesProcessedTime >= END_DELAY_MS) {
                gameState = GameState.FINISHED;
            }
        } else if (!activeNotes.isEmpty() || !pendingNotes.isEmpty()) {
            allNotesProcessedTime = -1;
        }
    }

    private long getCurrentMusicTime() {
        if (songStartTime >= 0) {
            return Math.max(0, System.currentTimeMillis() - songStartTime - totalPauseDuration);
        }
        if (musicStartTime > 0) {
            // 音乐尚未开始，返回负值（距离音乐开始还有多少毫秒）
            return System.currentTimeMillis() - musicStartTime - totalPauseDuration;
        }
        return 0;
    }

    private void playMusic() {
        ResourceLocation soundLocation = ResourceLocation.tryParse(currentMap.Src);
        Minecraft.getInstance().getSoundManager().play(
                new SimpleSoundInstance(soundLocation, SoundSource.VOICE, 1.0F, 1.0F,
                        RandomSource.create(), false, 0, SimpleSoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    // ===== 渲染 =====
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTracks(graphics);

        // 绘制所有活跃音符（音乐未开始时也渲染，实现预滚动）
        for (LiveNote ln : activeNotes) {
            drawNote(graphics, ln, partialTick);
        }

        // 击中特效
        for (HitEffect effect : hitEffects) {
            long elapsed = System.currentTimeMillis() - effect.startTime;
            float life = 1.0f - (elapsed / 800f); // 800ms 生命周期
            if (life <= 0)
                continue; // 跳过已消失的特效（后续会被 removeIf 清除）
            // 使用 ease-out 曲线，让消失更平滑
            float alpha = life * life; // 平方淡出
            int color = effect.perfect ? 0xFFFFD700 : 0xFF00FF00;
            int finalColor = ((int) (alpha * 255) << 24) | (color & 0x00FFFFFF);
            graphics.drawCenteredString(font, effect.text, effect.x, effect.y - (int) ((1 - life) * 20), finalColor);
        }

        drawHUD(graphics);

        if (gameState == GameState.WAITING) {
            drawCenteredString(graphics, font, Component.translatable("gui.rhythm.waiting"),
                    width / 2, height / 2 - 10, 0xFFFFFF);
        } else if (gameState == GameState.FINISHED) {
            drawResult(graphics);
        }
    }

    private void drawTracks(GuiGraphics graphics) {
        int TRACK_Y_UP = getTrackY(0);
        int TRACK_Y_DOWN = getTrackY(1);
        graphics.fill(JUDGE_LINE_X, TRACK_Y_UP - 12, width, TRACK_Y_UP + 12, 0x22000000);
        graphics.fill(JUDGE_LINE_X, TRACK_Y_DOWN - 12, width, TRACK_Y_DOWN + 12, 0x22000000);
        graphics.fill(JUDGE_LINE_X - 1, TRACK_Y_UP - 20, JUDGE_LINE_X + 1, TRACK_Y_DOWN + 20, 0xFFFFFFFF);
        graphics.drawString(font, "▲", JUDGE_LINE_X - 20, TRACK_Y_UP - 8, 0xFFFFAA00);
        graphics.drawString(font, "▼", JUDGE_LINE_X - 20, TRACK_Y_DOWN - 8, 0xFFAA00FF);
    }

    private void drawNote(GuiGraphics graphics, LiveNote ln, float partialTick) {

        int TRACK_Y_UP = getTrackY(0);
        int TRACK_Y_DOWN = getTrackY(1);
        int y = ln.track == 0 ? TRACK_Y_UP : TRACK_Y_DOWN;
        int x = Mth.lerpInt(partialTick, ln.prevX, ln.currentX);

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
                int tailX = Mth.lerpInt(partialTick, ln.prevTailX, ln.currentTailX);
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
            graphics.drawString(font, Component.translatable("gui.rhythm.hint.up"), 10, height - 40, 0xAAAAAA);
            graphics.drawString(font, Component.translatable("gui.rhythm.hint.down"), 10, height - 20, 0xAAAAAA);
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

    // ===== 输入处理 =====
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (gameState == GameState.FINISHED) {
            onClose();
            return true;
        }
        if (gameState == GameState.WAITING)
            return super.mouseClicked(mouseX, mouseY, button);
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
        if (gameState == GameState.PLAYING || gameState == GameState.PAUSED) {
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
        if (songStartTime < 0)
            return; // 音乐未开始，不进行任何判定

        long now = getCurrentMusicTime();
        // 只处理 Single 和 Hold 头部，HoldSingle 由 tick 自动处理
        for (LiveNote ln : activeNotes) {
            if (ln.track != track || ln.state != NoteState.ACTIVE)
                continue;
            if (ln.type == NoteType.SINGLE) {
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

        int TRACK_Y_UP = getTrackY(0);
        int TRACK_Y_DOWN = getTrackY(1);
        note.state = NoteState.HIT;
        score += (perfect ? 100 : 70) * comboMultiplier();
        if (perfect)
            perfectCount++;
        else
            goodCount++;
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        playHitSound();
        int y = note.track == 0 ? TRACK_Y_UP : TRACK_Y_DOWN;
        hitEffects.add(new HitEffect(note.currentX, y, perfect ? "Perfect!" : "Good!", perfect));
    }

    private void triggerMiss(LiveNote note) {
        note.state = NoteState.MISSED;
        combo = 0;
        missCount++;
    }

    private void startHold(LiveNote note) {

        int TRACK_Y_UP = getTrackY(0);
        int TRACK_Y_DOWN = getTrackY(1);
        note.state = NoteState.HOLDING;
        note.held = true;
        score += 50 * comboMultiplier();
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        playHitSound();
        int y = note.track == 0 ? TRACK_Y_UP : TRACK_Y_DOWN;
        hitEffects.add(new HitEffect(note.currentX, y, "Hold!", true));
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
        if (combo >= 100)
            return 2;
        return 1; // 小于 100 时至少保证基础 1 倍
    }

    // ===== 音效 =====
    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(CLICK_SOUND, 0.8F, 1.0F));
    }

    private void playHitSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HIT_SOUND, 1.0F, 1.0F));
    }

    // ===== 暂停 =====
    private void togglePause() {
        if (gameState == GameState.PLAYING) {
            gameState = GameState.PAUSED;
            pauseStart = System.currentTimeMillis();
            Minecraft.getInstance().getSoundManager().pause();
        } else if (gameState == GameState.PAUSED) {
            long delta = System.currentTimeMillis() - pauseStart;
            totalPauseDuration += delta;
            if (songStartTime < 0 && musicStartTime > 0)
                musicStartTime += delta;
            gameState = GameState.PLAYING;
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
        int prevX, currentX;
        int prevTailX, currentTailX;
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
            prevX = currentX;
            currentX = JUDGE_LINE_X + (int) ((note.startTime - currentTime) * NOTE_SPEED);

            if (type == NoteType.HOLD) {
                prevTailX = currentTailX;
                currentTailX = JUDGE_LINE_X + (int) ((note.endTime - currentTime) * NOTE_SPEED);
            } else {
                prevTailX = currentTailX = 0;
            }
        }

        int getTailX() {
            return currentTailX;
        }

        boolean isHolding() {
            return trackPressed[track];
        }
    }

    private static class HitEffect {
        final int x, y;
        final String text;
        final boolean perfect;
        final long startTime = System.currentTimeMillis();

        HitEffect(int x, int y, String text, boolean perfect) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.perfect = perfect;
        }
    }

    public static void open(MapData map) {
        Minecraft.getInstance().setScreen(new RhythmGameScreen(map));
    }
}