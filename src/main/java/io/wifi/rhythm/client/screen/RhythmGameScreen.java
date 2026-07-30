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

    private long musicStartTime = -1;
    private long songStartTime = -1;
    private long pauseStart = 0;
    private long totalPauseDuration = 0;
    private long allNotesProcessedTime = -1;

    // 按键状态（当前帧 / 上一帧）
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
        minecraft.getSoundManager().stop(null, SoundSource.VOICE);
        minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        if (gameState != GameState.PLAYING)
            return;

        // 保存上一帧按键状态，用于检测上升沿
        System.arraycopy(trackPressed, 0, prevTrackPressed, 0, 2);

        // 音乐开始检测
        if (songStartTime < 0 && musicStartTime > 0 && System.currentTimeMillis() >= musicStartTime) {
            playMusic();
            songStartTime = System.currentTimeMillis();
        }

        long currentTime = getCurrentMusicTime();
        boolean musicStarted = (songStartTime >= 0);

        // 1. 新音符加入
        while (!pendingNotes.isEmpty()) {
            RhythmNote next = pendingNotes.peek();
            long delayedStart = next.startTime + currentMap.Delayer;
            if (delayedStart - ADVANCE_DISPLAY_TIME <= currentTime) {
                pendingNotes.poll();
                activeNotes.add(new LiveNote(next, currentMap.Delayer));
            } else
                break;
        }

        // 2. 判定（仅在音乐开始后）
        if (musicStarted) {
            // 计算上升沿 (刚按下的瞬间)
            boolean[] trackJustPressed = new boolean[2];
            for (int i = 0; i < 2; i++) {
                trackJustPressed[i] = trackPressed[i] && !prevTrackPressed[i];
            }

            // 2a. SINGLE 与 HOLD 头部 —— 仅上升沿触发
            for (int track = 0; track < 2; track++) {
                if (!trackJustPressed[track])
                    continue;

                for (LiveNote ln : activeNotes) {
                    if (ln.track != track || ln.state != NoteState.ACTIVE)
                        continue;
                    long effectiveStart = ln.note.startTime + ln.delayer;

                    if (ln.type == NoteType.SINGLE) {
                        long diff = Math.abs(currentTime - effectiveStart);
                        if (diff <= PERFECT_WINDOW) {
                            hitNote(ln, true);
                            break;
                        } else if (diff <= GOOD_WINDOW) {
                            hitNote(ln, false);
                            break;
                        }
                    } else if (ln.type == NoteType.HOLD) {
                        long diff = Math.abs(currentTime - effectiveStart);
                        if (diff <= GOOD_WINDOW) {
                            startHold(ln);
                            break;
                        }
                    }
                }
            }

            // 2b. HOLDSINGLE 自动连打（持续按住）
            for (int track = 0; track < 2; track++) {
                if (!trackPressed[track])
                    continue;

                for (LiveNote ln : activeNotes) {
                    if (ln.track == track && ln.type == NoteType.HOLDSINGLE && ln.state == NoteState.ACTIVE) {
                        long effectiveStart = ln.note.startTime + ln.delayer;
                        if (currentTime >= effectiveStart) {
                            long diff = currentTime - effectiveStart;
                            if (diff <= PERFECT_WINDOW) {
                                hitNote(ln, true);
                            } else {
                                hitNote(ln, false);
                            }
                            break;
                        }
                    }
                }
            }

            // 2c. HOLD 长按持续检测
            for (LiveNote ln : activeNotes) {
                if (ln.type == NoteType.HOLD && ln.state == NoteState.HOLDING) {
                    if (ln.isHolding()) {
                        if (getTailX(ln, currentTime) <= JUDGE_LINE_X) {
                            completeHold(ln);
                        }
                    } else {
                        breakHold(ln);
                    }
                }
            }

            // 2d. 移除已处理音符 & miss 检查
            Iterator<LiveNote> it = activeNotes.iterator();
            while (it.hasNext()) {
                LiveNote ln = it.next();
                if (ln.state == NoteState.HIT || ln.state == NoteState.MISSED) {
                    it.remove();
                    continue;
                }
                // 检查是否飞过判定线太远（基于实时位置）
                if (ln.state == NoteState.ACTIVE && getNoteX(ln, currentTime) < JUDGE_LINE_X - MISS_THRESHOLD) {
                    triggerMiss(ln);
                    it.remove();
                }
            }

            // 2e. 节拍音效
            while (nextBeatIndex < beatTimes.size() && beatTimes.get(nextBeatIndex) <= currentTime) {
                playClickSound();
                nextBeatIndex++;
            }
        }

        // 3. 更新特效
        hitEffects.removeIf(e -> System.currentTimeMillis() - e.startTime > 800);

        // 4. 结束检测
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

    // 工具方法：基于音乐时间计算音符 x 坐标（实时，不依赖存储）
    private int getNoteX(LiveNote note, long time) {
        long effectiveStart = note.note.startTime + note.delayer;
        return JUDGE_LINE_X + (int) ((effectiveStart - time) * NOTE_SPEED);
    }

    private int getTailX(LiveNote note, long time) {
        long effectiveEnd = note.note.endTime + note.delayer;
        return JUDGE_LINE_X + (int) ((effectiveEnd - time) * NOTE_SPEED);
    }

    private long getCurrentMusicTime() {
        if (songStartTime >= 0) {
            return Math.max(0, System.currentTimeMillis() - songStartTime - totalPauseDuration);
        }
        if (musicStartTime > 0) {
            return System.currentTimeMillis() - musicStartTime - totalPauseDuration;
        }
        return 0;
    }

    private void playMusic() {
        ResourceLocation soundLocation = ResourceLocation.tryParse(currentMap.Src);
        minecraft.getSoundManager().play(
                new SimpleSoundInstance(soundLocation, SoundSource.VOICE, 1.0F, 1.0F,
                        RandomSource.create(), false, 0, SimpleSoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    // ===== 渲染（完全基于实时音乐时间 + partialTick，消除逻辑帧率影响） =====
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTracks(graphics);

        // 计算当前渲染时间（毫秒），用于平滑滚动
        long renderTime = getCurrentMusicTime();
        // 对于非暂停状态，partialTick 可以用来预测下一帧的音乐时间
        if (gameState == GameState.PLAYING) {
            renderTime += (long) (partialTick * 50); // 假设每个逻辑 tick 间隔 50ms
        }

        for (LiveNote ln : activeNotes) {
            drawNote(graphics, ln, renderTime);
        }

        // 击中特效
        long now = System.currentTimeMillis();
        for (HitEffect effect : hitEffects) {
            long elapsed = now - effect.startTime;
            float life = 1.0f - (elapsed / 800f);
            if (life <= 0)
                continue;
            float alpha = life * life;
            int color = effect.perfect ? 0xFFFFD700 : 0xFF00FF00;
            int finalColor = ((int) (alpha * 255) << 24) | (color & 0x00FFFFFF);
            graphics.drawCenteredString(font, effect.text,
                    effect.x, effect.y - (int) ((1 - life) * 20), finalColor);
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

    private void drawNote(GuiGraphics graphics, LiveNote ln, long renderTime) {
        int y = getTrackY(ln.track);
        int x = getNoteX(ln, renderTime);

        switch (ln.type) {
            case SINGLE, HOLDSINGLE -> {
                int color = ln.track == 0 ? 0xFFFFAA00 : 0xFFAA00FF;
                int size = ln.type == NoteType.HOLDSINGLE ? 5 : 8;
                if (ln.type == NoteType.HOLDSINGLE)
                    color = 0xFFFF5500;
                // 如果整个音符都在判定线左侧，则不渲染
                if (x + size < JUDGE_LINE_X)
                    return;
                // 可选：裁剪左边缘，这里简单跳过
                graphics.fill(x - size, y - size, x + size, y + size, color);
                graphics.renderOutline(x - size, y - size, size * 2, size * 2, 0xFFFFFFFF);
            }
            case HOLD -> {
                int headX = x;
                int tailX = getTailX(ln, renderTime);
                // 裁剪：整个长条在左侧则不画
                if (tailX < JUDGE_LINE_X)
                    return;
                // 将头部限制在判定线右侧
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

    // ===== 输入处理（仅记录状态，不进行判定） =====
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
    }

    private void releaseTrack(int track) {
        trackPressed[track] = false;
    }

    // ===== 判定方法 =====
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
        int y = getTrackY(note.track);
        // 特效位置取当前渲染位置
        hitEffects
                .add(new HitEffect(getNoteX(note, getCurrentMusicTime()), y, perfect ? "Perfect!" : "Good!", perfect));
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
        int y = getTrackY(note.track);
        hitEffects.add(new HitEffect(getNoteX(note, getCurrentMusicTime()), y, "Hold!", true));
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

    private void togglePause() {
        if (gameState == GameState.PLAYING) {
            gameState = GameState.PAUSED;
            pauseStart = System.currentTimeMillis();
            minecraft.getSoundManager().pause();
        } else if (gameState == GameState.PAUSED) {
            long delta = System.currentTimeMillis() - pauseStart;
            totalPauseDuration += delta;
            if (songStartTime < 0 && musicStartTime > 0)
                musicStartTime += delta;
            gameState = GameState.PLAYING;
            minecraft.getSoundManager().resume();
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