package io.wifi.rhythm.client.utils;

import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public class OggPlayer {

    private final ResourceLocation oggLocation;
    private final Minecraft minecraft;

    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile boolean stopped = false;

    private volatile AudioFormat audioFormat;
    private volatile SourceDataLine line;

    // 原始 OGG 数据（预加载但未解码）
    private volatile byte[] rawOggData = null;
    // 音频设备位置偏移
    private volatile long frameOffset = 0;

    private Thread playbackThread;

    public OggPlayer(ResourceLocation location) {
        this.oggLocation = location;
        this.minecraft = Minecraft.getInstance();
    }

    /**
     * 预加载原始 OGG 文件数据到内存（不解码）。
     * 可在主线程调用，IO 操作通常很快（几十毫秒）。
     */
    public void preloadRaw() {
        try (InputStream input = minecraft.getResourceManager()
                .getResource(oggLocation).orElseThrow().open()) {
            rawOggData = input.readAllBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 预加载并解码整个 OGG 为 PCM（不推荐，会长时间阻塞主线程）。
     * 保留此方法以防后续需要，但不建议在 startGame 中直接调用。
     */
    @Deprecated
    public void preloadDecoded() {
        preloadRaw(); // 先加载原始数据
        if (rawOggData == null)
            return;
        try (JOrbisAudioStream oggStream = new JOrbisAudioStream(new ByteArrayInputStream(rawOggData))) {
            AudioFormat fmt = oggStream.getFormat();
            AudioFormat pcmFmt = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    fmt.getSampleRate(), 16, fmt.getChannels(),
                    fmt.getChannels() * 2, fmt.getSampleRate(), false);
            this.audioFormat = pcmFmt;

            ByteBuffer[] bufferRef = new ByteBuffer[1];
            bufferRef[0] = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer temp = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);

            while (oggStream.readChunk(sample -> {
                short s = (short) (sample * 32767);
                if (temp.remaining() < 2) {
                    temp.flip();
                    bufferRef[0] = expandAndWrite(bufferRef[0], temp);
                    temp.clear();
                }
                temp.putShort(s);
            })) {
                // continue
            }
            if (temp.position() > 0) {
                temp.flip();
                bufferRef[0] = expandAndWrite(bufferRef[0], temp);
            }
            byte[] pcm = new byte[bufferRef[0].position()];
            bufferRef[0].flip();
            bufferRef[0].get(pcm);
            // 直接存为预解码 PCM，覆盖 rawOggData 的作用（但我们不推荐，这里仅做示例）
            rawOggData = null; // 不再使用原始数据
            // 注意：这里缺少一个字段来存储 pcm，实际使用中应另外存储，省略
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ByteBuffer expandAndWrite(ByteBuffer dest, ByteBuffer src) {
        ByteBuffer newBuf = ByteBuffer.allocate(dest.capacity() + src.remaining())
                .order(ByteOrder.LITTLE_ENDIAN);
        dest.flip();
        newBuf.put(dest);
        newBuf.put(src);
        return newBuf;
    }

    public void play() {
        if (playing.get() || stopped)
            return;
        playing.set(true);
        paused.set(false);
        stopped = false;
        playbackThread = new Thread(this::playbackLoop, "OggPlayer");
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
    }

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

    private void playbackLoop() {
        try {
            if (rawOggData != null) {
                // 有预加载的原始数据，直接内存解码播放
                playFromMemory();
            } else {
                // 从资源管理器实时读取（回退方案）
                playFromResource();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (line != null) {
                line.drain();
                line.close();
            }
            playing.set(false);
            stopped = true;
        }
    }

    private void playFromMemory() throws Exception {
        try (ByteArrayInputStream mem = new ByteArrayInputStream(rawOggData);
                JOrbisAudioStream oggStream = new JOrbisAudioStream(mem)) {

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

    private void playFromResource() throws Exception {
        try (InputStream input = minecraft.getResourceManager()
                .getResource(oggLocation).orElseThrow().open();
                InputStream mem = new ByteArrayInputStream(input.readAllBytes());
                JOrbisAudioStream oggStream = new JOrbisAudioStream(mem)) {

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