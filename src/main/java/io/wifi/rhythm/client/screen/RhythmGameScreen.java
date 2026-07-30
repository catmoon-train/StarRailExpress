package io.wifi.rhythm.client.screen;

import io.wifi.rhythm.client.utils.OggPlayer;
import io.wifi.rhythm.data.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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

    private static final SoundEvent CLICK_SOUND = SoundEvents.NOTE_BLOCK_SNARE.value();
    private static final SoundEvent HIT_SOUND = SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();
    private static final long MUSIC_DELAY_MS = 3000;
    private static final long END_DELAY_MS = 2000;

    private final RhythmMapData currentMap;
    private final Deque<RhythmNote> pendingNotes;
    private final List<LiveNote> activeNotes = new ArrayList<>();
    private final List<Long> beatTimes = new ArrayList<>();
    private int nextBeatIndex = 0;

    private enum GameState {
        WAITING, PLAYING, PAUSED, FINISHED
    }

    private GameState gameState = GameState.WAITING;

    // 游戏整体时间轴
    private long gameStartTime = -1;
    private long totalPauseDuration = 0;
    private long pauseStart = 0;
    private long musicStartGameTime = -1;
    private boolean musicPlayed = false;

    // 平滑模拟时间相关（用于渲染和判定）
    private long musicStartSystemTime = -1;
    private long musicPausedDuration = 0;
    private long musicPauseStart = 0;
    private long smoothedTimeDrift = 0; // 漂移修正量（毫秒）
    private long lastCalibrationTime = -1; // 上次校准的系统时间

    private OggPlayer musicPlayer;
    private long allNotesProcessedTime = -1;

    private final boolean[] trackPressed = new boolean[2];
    private final boolean[] prevTrackPressed = new boolean[2];

    private int score = 0, combo = 0, maxCombo = 0;
    private int perfectCount = 0, goodCount = 0, missCount = 0;
    private final List<HitEffect> hitEffects = new ArrayList<>();

    private Screen parent = null;
    private Button startButton;

    public RhythmGameScreen(RhythmMapData map) {
        super(Component.empty());
        this.currentMap = map;
        List<RhythmNote> sorted = new ArrayList<>(map.Notes);
        sorted.sort(Comparator.comparingInt(n -> n.startTime));
        this.pendingNotes = new ArrayDeque<>(sorted);
        buildBeatTimes();
    }

    public RhythmGameScreen(Screen parent, RhythmMapData map) {
        this(map);
        this.parent = parent;
    }

    private void buildBeatTimes() {
        List<Long> times = new ArrayList<>();
        if (currentMap.NoteClick != null) {
            for (RhythmNoteClick nc : currentMap.NoteClick) {
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
            this.startButton = Button.builder(Component.translatable("gui.rhythm.start"), btn -> startGame())
                    .pos((this.width - 100) / 2, this.height / 2 + 20)
                    .size(100, 20)
                    .build();
            this.addRenderableWidget(startButton);
        }
    }

    private void startGame() {
        gameState = GameState.PLAYING;
        gameStartTime = System.currentTimeMillis();
        totalPauseDuration = 0;
        musicPlayed = false;
        musicStartGameTime = MUSIC_DELAY_MS;

        ResourceLocation musicRes = ResourceLocation.tryParse(currentMap.Src);
        musicRes = transformResourcePackogg(musicRes);
        musicPlayer = new OggPlayer(musicRes);
        musicPlayer.preloadRaw();

        smoothedTimeDrift = 0;
        lastCalibrationTime = -1;

        if (startButton != null)
            removeWidget(startButton);
    }

    private ResourceLocation transformResourcePackogg(ResourceLocation musicRes) {
        WeighedSoundEvents entry = this.minecraft.getSoundManager().getSoundEvent(musicRes);
        if (entry != null) {
            final var sound = entry.getSound(RandomSource.create());
            return sound.getPath();
        }
        return ResourceLocation.fromNamespaceAndPath(musicRes.getNamespace(), "sounds/" + musicRes.getPath() + ".ogg");
    }

    @Override
    public void onClose() {
        if (musicPlayer != null)
            musicPlayer.stop();
        minecraft.getSoundManager().stop(null, net.minecraft.sounds.SoundSource.VOICE);
        minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        if (gameState != GameState.PLAYING)
            return;

        long gameTime = getGameTime();

        if (!musicPlayed && gameTime >= musicStartGameTime) {
            musicPlayer.play();
            musicPlayed = true;
            musicStartSystemTime = System.currentTimeMillis();
            musicPausedDuration = 0;
            lastCalibrationTime = System.currentTimeMillis();
        }

        // 平滑模拟时间（用于渲染和判定）
        long smoothTime = 0;
        if (musicPlayed && musicPlayer.isPlaying()) {
            smoothTime = getSmoothedMusicTime();
            // 定期校准
            long now = System.currentTimeMillis();
            if (lastCalibrationTime > 0 && now - lastCalibrationTime >= 2000) {
                long audioPos = musicPlayer.getPositionMs();
                long rawSimulated = Math.max(0, now - musicStartSystemTime - musicPausedDuration);
                long diff = audioPos - rawSimulated;
                smoothedTimeDrift = (long) (smoothedTimeDrift * 0.95 + diff * 0.05);
                lastCalibrationTime = now;
            }
        }

        // 谱面时间：统一使用平滑模拟时间（音乐开始前使用游戏时间模拟）
        long rawMusicTime;
        long currentMusicTime; // 用于判定和显示
        if (musicPlayed && musicPlayer.isPlaying()) {
            currentMusicTime = smoothTime;
            rawMusicTime = smoothTime;
        } else {
            rawMusicTime = gameTime - MUSIC_DELAY_MS;
            currentMusicTime = Math.max(0, rawMusicTime);
        }

        // 1. 新音符加入
        while (!pendingNotes.isEmpty()) {
            RhythmNote next = pendingNotes.peek();
            long delayedStart = next.startTime + currentMap.Delayer;
            if (delayedStart - ADVANCE_DISPLAY_TIME <= rawMusicTime) {
                pendingNotes.poll();
                activeNotes.add(new LiveNote(next, currentMap.Delayer));
            } else
                break;
        }

        // 2. 判定逻辑（基于平滑模拟时间）
        if (musicPlayed) {
            boolean[] trackJustPressed = new boolean[2];
            for (int i = 0; i < 2; i++) {
                trackJustPressed[i] = trackPressed[i] && !prevTrackPressed[i];
            }

            for (int track = 0; track < 2; track++) {
                if (!trackJustPressed[track])
                    continue;
                for (LiveNote ln : activeNotes) {
                    if (ln.track != track || ln.state != NoteState.ACTIVE)
                        continue;
                    long effectiveStart = ln.note.startTime + ln.delayer;
                    if (ln.type == NoteType.SINGLE) {
                        long diff = Math.abs(currentMusicTime - effectiveStart);
                        if (diff <= PERFECT_WINDOW) {
                            hitNote(ln, true, currentMusicTime);
                            break;
                        } else if (diff <= GOOD_WINDOW) {
                            hitNote(ln, false, currentMusicTime);
                            break;
                        }
                    } else if (ln.type == NoteType.HOLD) {
                        long diff = Math.abs(currentMusicTime - effectiveStart);
                        if (diff <= GOOD_WINDOW) {
                            startHold(ln, currentMusicTime);
                            break;
                        }
                    }
                }
            }

            for (int track = 0; track < 2; track++) {
                if (!trackPressed[track])
                    continue;
                for (LiveNote ln : activeNotes) {
                    if (ln.track == track && ln.type == NoteType.HOLDSINGLE && ln.state == NoteState.ACTIVE) {
                        long effectiveStart = ln.note.startTime + ln.delayer;
                        if (currentMusicTime >= effectiveStart) {
                            long diff = currentMusicTime - effectiveStart;
                            if (diff <= PERFECT_WINDOW)
                                hitNote(ln, true, currentMusicTime);
                            else
                                hitNote(ln, false, currentMusicTime);
                            break;
                        }
                    }
                }
            }

            for (LiveNote ln : activeNotes) {
                if (ln.type == NoteType.HOLD && ln.state == NoteState.HOLDING) {
                    if (ln.isHolding()) {
                        if (getTailX(ln, currentMusicTime) <= JUDGE_LINE_X) {
                            completeHold(ln);
                        }
                    } else {
                        breakHold(ln);
                    }
                }
            }

            Iterator<LiveNote> it = activeNotes.iterator();
            while (it.hasNext()) {
                LiveNote ln = it.next();
                if (ln.state == NoteState.HIT || ln.state == NoteState.MISSED) {
                    it.remove();
                    continue;
                }
                if (ln.state == NoteState.ACTIVE && getNoteX(ln, currentMusicTime) < JUDGE_LINE_X - MISS_THRESHOLD) {
                    triggerMiss(ln);
                    it.remove();
                }
            }

            while (nextBeatIndex < beatTimes.size() && beatTimes.get(nextBeatIndex) <= currentMusicTime) {
                playClickSound();
                nextBeatIndex++;
            }
        }

        if (musicPlayed && activeNotes.isEmpty() && pendingNotes.isEmpty()) {
            if (allNotesProcessedTime < 0) {
                allNotesProcessedTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - allNotesProcessedTime >= END_DELAY_MS) {
                gameState = GameState.FINISHED;
            }
        } else if (!activeNotes.isEmpty() || !pendingNotes.isEmpty()) {
            allNotesProcessedTime = -1;
        }

        System.arraycopy(trackPressed, 0, prevTrackPressed, 0, 2);
    }

    private long getGameTime() {
        if (gameStartTime < 0)
            return 0;
        long elapsed = System.currentTimeMillis() - gameStartTime;
        if (gameState == GameState.PAUSED) {
            long currentPause = System.currentTimeMillis() - pauseStart;
            return Math.max(0, elapsed - (totalPauseDuration + currentPause));
        } else {
            return Math.max(0, elapsed - totalPauseDuration);
        }
    }

    private long getSmoothedMusicTime() {
        if (musicStartSystemTime < 0)
            return 0;
        long elapsed = System.currentTimeMillis() - musicStartSystemTime - musicPausedDuration;
        if (gameState == GameState.PAUSED && musicPlayed) {
            elapsed -= (System.currentTimeMillis() - musicPauseStart);
        }
        return Math.max(0, elapsed + smoothedTimeDrift);
    }

    private int getNoteX(LiveNote note, long musicTime) {
        long effectiveStart = note.note.startTime + note.delayer;
        return JUDGE_LINE_X + (int) ((effectiveStart - musicTime) * NOTE_SPEED);
    }

    private int getTailX(LiveNote note, long musicTime) {
        long effectiveEnd = note.note.endTime + note.delayer;
        return JUDGE_LINE_X + (int) ((effectiveEnd - musicTime) * NOTE_SPEED);
    }

    // ==================== 渲染 ====================
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTracks(graphics);

        long renderMusicTime;
        if (musicPlayed && musicPlayer.isPlaying()) {
            long base = getSmoothedMusicTime();
            if (gameState == GameState.PLAYING) {
                renderMusicTime = base + (long) (partialTick * 50);
            } else {
                renderMusicTime = base;
            }
        } else {
            long renderGameTime = getGameTime();
            if (gameState == GameState.PLAYING)
                renderGameTime += (long) (partialTick * 50);
            renderMusicTime = renderGameTime - MUSIC_DELAY_MS;
        }

        for (LiveNote ln : activeNotes) {
            drawNote(graphics, ln, renderMusicTime);
        }

        // 击中特效（优化淡出，无闪烁）
        long now = System.currentTimeMillis();
        Iterator<HitEffect> it = hitEffects.iterator();
        while (it.hasNext()) {
            HitEffect effect = it.next();
            long elapsed = now - effect.startTime;
            float life = 1.0f - (elapsed / 600f);
            if (life <= 0) {
                it.remove();
                continue;
            }
            float progress = 1.0f - life;
            float easeOut = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress);
            float alpha = 1.0f - easeOut;
            if (alpha < 0.02f)
                continue;

            int color = effect.perfect ? 0xFFFFD700 : 0xFF00FF00;
            int finalColor = ((int) (alpha * 255) << 24) | (color & 0x00FFFFFF);
            graphics.drawCenteredString(font, effect.text,
                    effect.x, effect.y - (int) (easeOut * 20), finalColor);
        }

        drawHUD(graphics);

        if (gameState == GameState.WAITING) {
            drawCenteredString(graphics, font, Component.translatable("gui.rhythm.waiting"),
                    width / 2, height / 2 - 10, 0xFFFFFF);
        } else if (gameState == GameState.FINISHED) {
            drawResult(graphics);
        }
    }

    private int getTrackY(int track) {
        int centerY = this.height / 2;
        int spacing = 50;
        return track == 0 ? centerY - spacing / 2 : centerY + spacing / 2;
    }

    private void drawTracks(GuiGraphics graphics) {
        int yUp = getTrackY(0);
        int yDown = getTrackY(1);
        graphics.fill(JUDGE_LINE_X, yUp - 12, width, yUp + 12, 0x22000000);
        graphics.fill(JUDGE_LINE_X, yDown - 12, width, yDown + 12, 0x22000000);
        graphics.fill(JUDGE_LINE_X - 1, yUp - 20, JUDGE_LINE_X + 1, yDown + 20, 0xFFFFFFFF);
        graphics.drawString(font, "▲", JUDGE_LINE_X - 20, yUp - 8, 0xFFFFAA00);
        graphics.drawString(font, "▼", JUDGE_LINE_X - 20, yDown - 8, 0xFFAA00FF);
    }

    private void drawNote(GuiGraphics graphics, LiveNote ln, long renderMusicTime) {
        int y = getTrackY(ln.track);
        int x = getNoteX(ln, renderMusicTime);

        switch (ln.type) {
            case SINGLE, HOLDSINGLE -> {
                int color = ln.track == 0 ? 0xFFFFAA00 : 0xFFAA00FF;
                int size = ln.type == NoteType.HOLDSINGLE ? 5 : 8;
                if (ln.type == NoteType.HOLDSINGLE)
                    color = 0xFFFF5500;
                if (x + size < JUDGE_LINE_X)
                    return;
                graphics.fill(x - size, y - size, x + size, y + size, color);
                graphics.renderOutline(x - size, y - size, size * 2, size * 2, 0xFFFFFFFF);
            }
            case HOLD -> {
                int headX = x;
                int tailX = getTailX(ln, renderMusicTime);
                if (tailX < JUDGE_LINE_X)
                    return;
                if (headX < JUDGE_LINE_X)
                    headX = JUDGE_LINE_X;
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

    // ==================== 输入处理 ====================
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
        if (gameState == GameState.WAITING && keyCode == GLFW.GLFW_KEY_SPACE) {
            startGame();
            return true;
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
    }

    private void releaseTrack(int track) {
        trackPressed[track] = false;
    }

    // ==================== 判定 ====================
    private void hitNote(LiveNote note, boolean perfect, long musicTime) {
        note.state = NoteState.HIT;
        score += (perfect ? 100 : 70) * comboMultiplier();
        if (perfect)
            perfectCount++;
        else
            goodCount++;
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        playHitSound();
        int y = getTrackY(note.track);
        hitEffects.add(new HitEffect(getNoteX(note, musicTime), y, perfect ? "Perfect!" : "Good!", perfect));
    }

    private void triggerMiss(LiveNote note) {
        note.state = NoteState.MISSED;
        combo = 0;
        missCount++;
    }

    private void startHold(LiveNote note, long musicTime) {
        note.state = NoteState.HOLDING;
        note.held = true;
        score += 50 * comboMultiplier();
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        playHitSound();
        int y = getTrackY(note.track);
        hitEffects.add(new HitEffect(getNoteX(note, musicTime), y, "Hold!", true));
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
        return combo >= 100 ? 2 : 1;
    }

    private void playClickSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(CLICK_SOUND, 0.8F, 1.0F));
    }

    private void playHitSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(HIT_SOUND, 1.0F, 1.0F));
    }

    // ==================== 暂停 ====================
    private void togglePause() {
        if (gameState == GameState.PLAYING) {
            gameState = GameState.PAUSED;
            pauseStart = System.currentTimeMillis();
            if (musicPlayer != null && musicPlayed) {
                musicPlayer.pause();
                musicPauseStart = System.currentTimeMillis();
            }
        } else if (gameState == GameState.PAUSED) {
            long delta = System.currentTimeMillis() - pauseStart;
            totalPauseDuration += delta;
            if (musicPlayer != null && musicPlayed) {
                musicPausedDuration += System.currentTimeMillis() - musicPauseStart;
                musicPlayer.resume();
            }
            lastCalibrationTime = 0; // 恢复后立即校准
            gameState = GameState.PLAYING;
        }
    }

    // ==================== 内部类 ====================
    private enum NoteType {
        SINGLE, HOLD, HOLDSINGLE
    }

    private enum NoteState {
        ACTIVE, HOLDING, HIT, MISSED
    }

    private class LiveNote {
        final RhythmNote note;
        final NoteType type;
        final int track;
        final int delayer;
        NoteState state = NoteState.ACTIVE;
        boolean held;

        LiveNote(RhythmNote n, int delayer) {
            this.note = n;
            this.delayer = delayer;
            this.type = switch (n.noteType) {
                case "Single" -> NoteType.SINGLE;
                case "Hold" -> NoteType.HOLD;
                case "HoldSingle" -> NoteType.HOLDSINGLE;
                default -> NoteType.SINGLE;
            };
            this.track = "Left".equals(n.positionType) ? 0 : 1;
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

    public static void open(RhythmMapData map) {
        Minecraft.getInstance().setScreen(new RhythmGameScreen(map));
    }

    public static void open(Screen parent, RhythmMapData map) {
        Minecraft.getInstance().setScreen(new RhythmGameScreen(parent, map));
    }
}