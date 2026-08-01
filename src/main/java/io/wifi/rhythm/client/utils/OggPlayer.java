/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.rhythm.client.utils;

import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class OggPlayer {

    private final ResourceLocation oggLocation;
    private final Minecraft minecraft;
    private static final CopyOnWriteArrayList<OggPlayer> ACTIVE_PLAYERS = new CopyOnWriteArrayList<>();

    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private volatile boolean stopped = false;

    private volatile AudioFormat audioFormat;
    private volatile SourceDataLine line;
    private volatile float playSampleRate; // 实际播放采样率（受 speed 影响）

    private volatile byte[] rawOggData = null;
    private volatile long frameOffset = 0;

    private final Screen screen;
    private Thread playbackThread;

    // 音量因子（0.0 ~ 无穷，建议 0.0~1.0，可大于1但可能削波）
    private volatile float volume = 1.0f;
    // 播放速度因子（1.0 为原速，>1 快放升调，<1 慢放降调，仅在开始播放时生效）
    private volatile float speed = 1.0f;

    public OggPlayer(ResourceLocation location) {
        this(location, null);
    }

    public OggPlayer(ResourceLocation location, Screen playScreen) {
        this(location, playScreen, 1f, 1f);
    }

    public OggPlayer(ResourceLocation location, Screen playScreen, float volume) {
        this(location, playScreen, volume, 1f);
    }

    public OggPlayer(ResourceLocation location, Screen playScreen, float volume, float speed) {
        this.oggLocation = location;
        this.minecraft = Minecraft.getInstance();
        this.screen = playScreen;
        this.volume = Math.max(0f, volume);
        this.speed = Math.max(0.1f, Math.min(speed, 4.0f));
    }
    // ---------- 参数设置 ----------

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, volume);
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.1f, Math.min(speed, 4.0f)); // 限制在合理范围
    }

    public float getVolume() {
        return volume;
    }

    public float getSpeed() {
        return speed;
    }

    // ---------- 预加载 ----------

    public void preloadRaw() {
        try (InputStream input = minecraft.getResourceManager()
                .getResource(oggLocation).orElseThrow().open()) {
            rawOggData = input.readAllBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- 播放控制 ----------

    public void play() {
        if (playing.get() || stopped)
            return;

        playing.set(true);
        paused.set(false);
        stopped = false;
        stopping.set(false);

        ACTIVE_PLAYERS.add(this);
        playbackThread = new Thread(this::playbackLoop, "OggPlayer-" + oggLocation);
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    public void pause() {
        if (!playing.get() || paused.get())
            return;
        paused.set(true);
        if (line != null && line.isOpen()) {
            line.stop();
        }
    }

    public void resume() {
        if (!playing.get() || !paused.get())
            return;
        paused.set(false);
        if (line != null && line.isOpen()) {
            line.start();
        }
    }

    public void stop() {
        if (!stopping.compareAndSet(false, true))
            return;
        stopped = true;
        playing.set(false);
        paused.set(false);

        if (line != null) {
            line.stop();
            line.close();
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
        }
        ACTIVE_PLAYERS.remove(this);
    }

    // ---------- 屏幕检查 ----------

    public void checkAndStopIfScreenChanged(Screen currentScreen) {
        if (screen != null && currentScreen != screen) {
            stop();
        }
    }

    public static void checker(Screen nowScreen) {
        for (OggPlayer player : ACTIVE_PLAYERS) {
            player.checkAndStopIfScreenChanged(nowScreen);
        }
    }

    // ---------- 状态查询 ----------

    public long getPositionMs() {
        if (line != null && line.isOpen() && playSampleRate > 0) {
            long frames = line.getLongFramePosition() - frameOffset;
            return frames * 1000 / (long) playSampleRate;
        }
        return 0;
    }

    public boolean isPlaying() {
        return playing.get() && !paused.get();
    }

    public boolean isStopped() {
        return stopped;
    }

    // ---------- 内部播放循环 ----------

    private void playbackLoop() {
        try {
            if (rawOggData != null) {
                try (InputStream mem = new ByteArrayInputStream(rawOggData)) {
                    playFromStream(mem);
                }
            } else {
                try (InputStream input = minecraft.getResourceManager()
                        .getResource(oggLocation).orElseThrow().open();
                        InputStream mem = new ByteArrayInputStream(input.readAllBytes())) {
                    playFromStream(mem);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (!stopping.get()) {
                if (line != null) {
                    line.drain();
                    line.close();
                }
                playing.set(false);
                stopped = true;
                ACTIVE_PLAYERS.remove(this);
            }
        }
    }

    private void playFromStream(InputStream rawStream) throws Exception {
        try (JOrbisAudioStream oggStream = new JOrbisAudioStream(rawStream)) {
            audioFormat = oggStream.getFormat();

            // 根据 speed 计算实际播放采样率（仅在开始时获取一次）
            float currentSpeed = Math.max(0.1f, this.speed);
            float baseSampleRate = audioFormat.getSampleRate();
            float targetSampleRate = baseSampleRate * currentSpeed;
            playSampleRate = targetSampleRate;

            AudioFormat pcmFmt = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    targetSampleRate,
                    16,
                    audioFormat.getChannels(),
                    audioFormat.getChannels() * 2,
                    targetSampleRate,
                    false);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFmt);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(pcmFmt, 16384);
            frameOffset = line.getLongFramePosition();
            line.start();

            byte[] buffer = new byte[8192];
            ByteBuffer pcmBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            while (!stopped) {
                while (paused.get() && !stopped) {
                    Thread.sleep(10);
                }
                if (stopped)
                    break;

                boolean hasMore = oggStream.readChunk(sample -> {
                    // 应用音量（线程安全读取 volatile volume）
                    float vol = volume;
                    int scaled = (int) (sample * 32767.0f * vol);
                    short s = (short) Math.max(-32768, Math.min(32767, scaled));
                    if (pcmBuffer.remaining() < 2) {
                        pcmBuffer.flip();
                        line.write(buffer, 0, pcmBuffer.limit());
                        pcmBuffer.clear();
                    }
                    pcmBuffer.putShort(s);
                });

                if (pcmBuffer.position() > 0) {
                    pcmBuffer.flip();
                    line.write(buffer, 0, pcmBuffer.limit());
                    pcmBuffer.clear();
                }

                if (!hasMore)
                    break;
            }
        }
    }
}