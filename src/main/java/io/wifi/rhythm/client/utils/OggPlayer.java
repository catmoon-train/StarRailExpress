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
    private final AtomicBoolean stopping = new AtomicBoolean(false); // 防止重复停止
    private volatile boolean stopped = false;

    private volatile AudioFormat audioFormat;
    private volatile SourceDataLine line;

    private volatile byte[] rawOggData = null;
    private volatile long frameOffset = 0;

    private final Screen screen;
    private Thread playbackThread;

    public OggPlayer(ResourceLocation location) {
        this(location, null);
    }

    public OggPlayer(ResourceLocation location, Screen playScreen) {
        this.oggLocation = location;
        this.minecraft = Minecraft.getInstance();
        this.screen = playScreen;
    }

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
            return; // 只允许一次停止
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

    // ---------- 外部检查 ----------

    /**
     * 由主线程调用，若当前屏幕不是构造时绑定的屏幕则停止播放。
     */
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
        if (line != null && line.isOpen()) {
            long frames = line.getLongFramePosition() - frameOffset;
            if (audioFormat != null) {
                return frames * 1000 / (long) audioFormat.getSampleRate();
            }
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
            // 播放结束（自然结束或异常），保证资源释放
            if (!stopping.get()) { // 如果还没被stop()清理
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

    /**
     * 统一的解码与播放逻辑，无论数据来源。
     */
    private void playFromStream(InputStream rawStream) throws Exception {
        try (JOrbisAudioStream oggStream = new JOrbisAudioStream(rawStream)) {
            audioFormat = oggStream.getFormat();
            AudioFormat pcmFmt = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    audioFormat.getSampleRate(), 16, audioFormat.getChannels(),
                    audioFormat.getChannels() * 2, audioFormat.getSampleRate(), false);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFmt);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(pcmFmt, 16384);
            frameOffset = line.getLongFramePosition();
            line.start();

            byte[] buffer = new byte[8192];
            ByteBuffer pcmBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            while (!stopped) {
                // 暂停循环
                while (paused.get() && !stopped) {
                    Thread.sleep(10);
                }
                if (stopped)
                    break;

                boolean hasMore = oggStream.readChunk(sample -> {
                    short s = (short) (sample * 32767);
                    if (pcmBuffer.remaining() < 2) {
                        pcmBuffer.flip();
                        line.write(buffer, 0, pcmBuffer.limit());
                        pcmBuffer.clear();
                    }
                    pcmBuffer.putShort(s);
                });

                // 写入剩余数据
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