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

package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一存放“说话者侧”额外语音药水效果的客户端处理：
 *
 * <ul>
 *   <li>OpenAL 源级效果（{@code OpenALSoundEvent.Post}，零延迟、原生性能）：
 *     <ul>
 *       <li>{@code VOICE_HELMET}     远处/头盔声：直接低通滤波（GAINHF 0.3~0.5）</li>
 *       <li>{@code VOICE_UNDERWATER} 水下声：低通（0.3~0.6）+ 降低增益 0.7</li>
 *       <li>{@code VOICE_REVERB}     混响：EFX REVERB 效果经辅助效果槽（aux send 1）路由</li>
 *     </ul>
 *   </li>
 *   <li>PCM 级效果（{@code ClientReceiveSoundEvent}，在原始音频上传到 OpenAL 之前处理）：
 *     <ul>
 *       <li>{@code VOICE_SYNTH}      合成人声 / 自动调音：基频检测 + 量化到最近半音 + WSOLA 变调</li>
 *       <li>{@code VOICE_DISTORTION} 失真：tanh 软削波</li>
 *       <li>{@code VOICE_CHORUS}     合唱：延迟线 + LFO 调制</li>
 *       <li>{@code VOICE_TREMOLO}    颤音：幅度 LFO 调制</li>
 *       <li>{@code VOICE_STUTTER}    口吃：重复小段音频</li>
 *       <li>{@code VOICE_REVERSE}    倒放：分块缓冲后反向播放</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>说话者效果由 {@link org.agmas.noellesroles.voice.VoiceEffectSync} 广播到听者客户端，
 * 因此本插件直接在听者侧查 {@code level.getPlayerByUUID(speaker).hasEffect(...)} 即可生效。</p>
 */
public class VoiceExtraEffectsPlugin implements VoicechatPlugin {

    private static final int SAMPLE_RATE = 48000;

    // ---- OpenAL / EFX 常量（显式定义以兼容） ----
    private static final int AL_FILTER_TYPE = 0x8001;
    private static final int AL_FILTER_LOWPASS = 0x0003;
    private static final int AL_FILTER_LOWPASS_GAINHF = 0x0002;
    private static final int AL_DIRECT_FILTER = 0x20005;
    private static final int AL_FILTER_NULL = 0;
    private static final int AL_EFFECT_TYPE = 0x8001;
    private static final int AL_EFFECT_REVERB = 0x0004;
    private static final int AL_EFFECTSLOT_EFFECT = 0x0001;
    private static final int AL_AUXILIARY_SEND_FILTER = 0x20006;

    // EFX REVERB 参数
    private static final int AL_REVERB_DENSITY = 0x0001;
    private static final int AL_REVERB_DIFFUSION = 0x0002;
    private static final int AL_REVERB_GAIN = 0x0003;
    private static final int AL_REVERB_GAINHF = 0x0004;
    private static final int AL_REVERB_DECAY_TIME = 0x0005;
    private static final int AL_REVERB_DECAY_HFRATIO = 0x0006;
    private static final int AL_REVERB_REFLECTIONS_GAIN = 0x0007;
    private static final int AL_REVERB_REFLECTIONS_DELAY = 0x0008;
    private static final int AL_REVERB_LATE_REVERB_GAIN = 0x0009;
    private static final int AL_REVERB_LATE_REVERB_DELAY = 0x000A;
    private static final int AL_REVERB_AIR_ABSORPTION_GAINHF = 0x000B;
    private static final int AL_REVERB_ROOM_ROLLOFF_FACTOR = 0x000C;

    /** EFX 是否可用（不可用时跳过所有 OpenAL 效果）。 */
    private static volatile boolean efxAvailable = true;

    // ---- 每个说话者的 EFX 资源 ----
    private static final Map<UUID, Integer> LOWPASS_FILTERS = new ConcurrentHashMap<>();
    private static final Map<UUID, int[]> REVERB_RESOURCES = new ConcurrentHashMap<>(); // {slot, effect}

    // ---- 每个说话者的 PCM 状态 ----
    private static final Map<UUID, HeliumPitchShifter> HELIUM_SHIFTERS = new ConcurrentHashMap<>();
    private static final Map<UUID, HeliumPitchShifter> SYNTH_SHIFTERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> SYNTH_RATIO = new ConcurrentHashMap<>();
    private static final Map<UUID, ChorusState> CHORUS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> TREMOLO_PHASE = new ConcurrentHashMap<>();
    private static final Map<UUID, StutterState> STUTTER = new ConcurrentHashMap<>();
    private static final Map<UUID, ReverseState> REVERSE = new ConcurrentHashMap<>();
    private static final Map<UUID, EchoState> ECHO = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return "noellesroles_voice_extra_effects";
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // 所有处理都在听者客户端完成，仅客户端注册。
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        registration.registerEvent(OpenALSoundEvent.Post.class, this::onOpenALSound);
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::onClientSound);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, this::onClientSound);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, this::onClientSound);
    }

    // =========================================================================
    //  OpenAL 源级效果（头盔 / 水下 / 混响）
    // =========================================================================

    private void onOpenALSound(OpenALSoundEvent.Post event) {
        UUID speaker = event.getChannelId();
        if (speaker == null) return;
        int source = event.getSource();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Player player = mc.level.getPlayerByUUID(speaker);
        if (player == null) {
            cleanupSpeaker(speaker);
            return;
        }

        int helmet = ModEffects.getVoiceHelmetLevel(player);
        int underwater = ModEffects.getVoiceUnderwaterLevel(player);
        int reverb = ModEffects.getVoiceReverbLevel(player);

        applyLowPassGain(source, speaker, helmet, underwater);
        applyReverb(source, speaker, reverb);
    }

    /**
     * 头盔/水下：低通直接滤波。
     */
    private static void applyLowPassGain(int source, UUID speaker, int helmet, int underwater) {
        if (helmet <= 0 && underwater <= 0) {
            try {
                AL11.alSourcei(source, AL_DIRECT_FILTER, AL_FILTER_NULL);
            } catch (Throwable ignored) {}
            return;
        }
        if (!efxAvailable) {
            // EFX 不可用：无法做低通，也不要写 AL_GAIN（会锁死滑块）。
            return;
        }

        try {
            int filter = LOWPASS_FILTERS.computeIfAbsent(speaker, k -> EXTEfx.alGenFilters());

            float hf = 1.0f;
            if (helmet > 0) hf = Math.min(hf, 0.5f - (helmet - 1) * 0.05f);      // 0.5 -> 0.3
            if (underwater > 0) hf = Math.min(hf, 0.6f - (underwater - 1) * 0.075f); // 0.6 -> 0.3
            EXTEfx.alFilteri(filter, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(filter, AL_FILTER_LOWPASS_GAINHF, hf);
            AL11.alSourcei(source, AL_DIRECT_FILTER, filter);
        } catch (Throwable t) {
            efxAvailable = false;
        }
    }

    /** 混响：EFX REVERB 经辅助效果槽（aux send 1）路由。 */
    private static void applyReverb(int source, UUID speaker, int reverb) {
        if (reverb <= 0) {
            try {
                AL11.alSource3i(source, AL_AUXILIARY_SEND_FILTER, AL_FILTER_NULL, 1, 0);
            } catch (Throwable ignored) {}
            return;
        }
        if (!efxAvailable) return;

        try {
            int[] res = REVERB_RESOURCES.computeIfAbsent(speaker, k -> createReverbEffect());
            if (res == null) return;
            int slot = res[0];
            int effect = res[1];

            EXTEfx.alEffectf(effect, AL_REVERB_DENSITY, 0.5f + reverb * 0.1f);
            EXTEfx.alEffectf(effect, AL_REVERB_DIFFUSION, 0.6f + reverb * 0.08f);
            EXTEfx.alEffectf(effect, AL_REVERB_GAIN, 0.4f + reverb * 0.05f);   // 湿声量
            EXTEfx.alEffectf(effect, AL_REVERB_GAINHF, 0.6f);
            EXTEfx.alEffectf(effect, AL_REVERB_DECAY_TIME, 0.6f + reverb * 0.28f);
            EXTEfx.alEffectf(effect, AL_REVERB_DECAY_HFRATIO, 0.6f);
            EXTEfx.alEffectf(effect, AL_REVERB_REFLECTIONS_GAIN, 0.18f + reverb * 0.04f);
            EXTEfx.alEffectf(effect, AL_REVERB_REFLECTIONS_DELAY, 0.02f);
            EXTEfx.alEffectf(effect, AL_REVERB_LATE_REVERB_GAIN, 0.3f + reverb * 0.06f);
            EXTEfx.alEffectf(effect, AL_REVERB_LATE_REVERB_DELAY, 0.03f + reverb * 0.01f);
            EXTEfx.alEffectf(effect, AL_REVERB_AIR_ABSORPTION_GAINHF, 0.1f);
            EXTEfx.alEffectf(effect, AL_REVERB_ROOM_ROLLOFF_FACTOR, 0.0f);

            AL11.alSource3i(source, AL_AUXILIARY_SEND_FILTER, slot, 1, 0);
        } catch (Throwable t) {
            efxAvailable = false;
        }
    }

    private static int[] createReverbEffect() {
        int slot = EXTEfx.alGenAuxiliaryEffectSlots();
        int effect = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(effect, AL_EFFECT_TYPE, AL_EFFECT_REVERB);
        EXTEfx.alAuxiliaryEffectSloti(slot, AL_EFFECTSLOT_EFFECT, effect);
        return new int[] { slot, effect };
    }

    // =========================================================================
    //  PCM 级效果（合成 / 失真 / 合唱 / 颤音 / 口吃 / 倒放）
    // =========================================================================

    private void onClientSound(ClientReceiveSoundEvent event) {
        if (event.isCancelled()) return;
        short[] pcm = event.getRawAudio();
        if (pcm == null || pcm.length == 0) return;

        UUID speaker = event.getId();
        if (speaker == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Player player = mc.level.getPlayerByUUID(speaker);
        if (player == null) {
            cleanupSpeaker(speaker);
            return;
        }

        int synth = ModEffects.getVoiceSynthLevel(player);
        int dist = ModEffects.getVoiceDistortionLevel(player);
        int chorus = ModEffects.getVoiceChorusLevel(player);
        int trem = ModEffects.getVoiceTremoloLevel(player);
        int stut = ModEffects.getVoiceStutterLevel(player);
        int rev = ModEffects.getVoiceReverseLevel(player);
        int helium = ModEffects.getVoiceHeliumLevel(player);
        int underwater = ModEffects.getVoiceUnderwaterLevel(player);
        int echo = ModEffects.getVoiceEchoCount(player);

        // 注意：多个效果可叠加，按固定顺序串联处理。
        // 升调（氦气）最先处理，作用在原始信号上，使其余效果叠加在变调后的音频上。
        if (underwater > 0) pcm = underwaterTransform(pcm, underwater);
        if (helium > 0) pcm = heliumTransform(pcm, speaker, helium);
        if (rev > 0) pcm = reverseTransform(pcm, speaker, rev);
        if (synth > 0) pcm = synthTransform(pcm, speaker, synth);
        if (chorus > 0) pcm = chorusTransform(pcm, speaker, chorus);
        if (dist > 0) pcm = distortionTransform(pcm, speaker, dist);
        if (trem > 0) pcm = tremoloTransform(pcm, speaker, trem);
        if (echo > 0) pcm = echoTransform(pcm, speaker, echo);
        if (stut > 0) pcm = stutterTransform(pcm, speaker, stut);

        event.setRawAudio(pcm);
    }

    /** 合成人声 / 自动调音：检测基频，量化到最近半音，用 WSOLA 变调（复用 HeliumPitchShifter）。 */
    private static short[] synthTransform(short[] pcm, UUID speaker, int level) {
        double f0 = detectPitch(pcm);
        double ratio = 1.0;
        if (f0 >= 70.0 && f0 <= 1200.0) {
            double note = Math.round(12.0 * Math.log(f0 / 440.0) / Math.log(2.0));
            double snapped = 440.0 * Math.pow(2.0, note / 12.0);
            ratio = snapped / f0;
        }
        ratio = Math.max(0.5, Math.min(2.0, ratio));
        // 等级越高：在自动调音基础上额外整体上移音高（每级 +2 半音），越来越"电子歌姬/尖细"。
        double transpose = Math.pow(2.0, (level - 1) * 2.0 / 12.0);
        ratio = Math.max(0.5, Math.min(2.0, ratio * transpose));
        // 平滑避免抖动
        double prev = SYNTH_RATIO.getOrDefault(speaker, 1.0);
        double smoothed = prev + 0.3 * (ratio - prev);
        SYNTH_RATIO.put(speaker, smoothed);
        HeliumPitchShifter shifter = SYNTH_SHIFTERS.computeIfAbsent(speaker, k -> new HeliumPitchShifter());
        return shifter.process(pcm, (float) smoothed);
    }

    /**
     * 水下语音的“降音量”部分（PCM 级衰减）。
     * <p>衰减系数与原本一致：1 级≈0.7，最高 5 级≈0.46。</p>
     */
    private static short[] underwaterTransform(short[] pcm, int level) {
        float gain = Math.max(0.4f, 0.7f - (level - 1) * 0.06f);
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = clamp(pcm[i] * gain);
        }
        return pcm;
    }

    /**
     * 氦气变声：WSOLA 实时升调。变调倍率随等级提升（等级越高声音越尖）。
     * ratio：1 级≈1.3，每升 1 级 +0.25，最高 2.5（HeliumPitchShifter 上限）。
     */
    private static short[] heliumTransform(short[] pcm, UUID speaker, int level) {
        float ratio = (float) Math.min(2.5, 1.3 + (level - 1) * 0.25);
        HeliumPitchShifter shifter = HELIUM_SHIFTERS.computeIfAbsent(speaker, k -> new HeliumPitchShifter());
        return shifter.process(pcm, ratio);
    }

    /**
     * 失真：预增益 + 硬削波（hard clip）。
     * <p>相较 tanh 软削波，硬削波在小信号时也放大、到达阈值后直接截平，
     * 1 级即可明显听出"破音/电吉他"质感；等级越高驱动越强、削波越狠。</p>
     */
    private static short[] distortionTransform(short[] pcm, UUID speaker, int level) {
        double drive = 5.0 + (level - 1) * 1.5;            // 5.0 -> 11.0
        double threshold = 0.75 - (level - 1) * 0.06;      // 削波阈值，等级越高越早截平
        for (int i = 0; i < pcm.length; i++) {
            double s = ((double) pcm[i]) * drive / 32767.0;
            s = Math.max(-threshold, Math.min(threshold, s));
            pcm[i] = clamp(s / threshold * 32767.0);
        }
        return pcm;
    }

    /** 合唱：延迟线 + 正弦 LFO 调制延迟，干/湿混合。 */
    private static short[] chorusTransform(short[] pcm, UUID speaker, int level) {
        ChorusState st = CHORUS.computeIfAbsent(speaker, k -> new ChorusState());
        int baseDelay = (int) (0.018 * SAMPLE_RATE);            // 18ms
        int depth = (int) ((2.0 + level) * 0.001 * SAMPLE_RATE); // 调制深度 ~2~7ms
        double lfoRate = 0.5 + level * 0.1;                      // Hz
        double wet = Math.min(0.8, 0.4 + level * 0.05);
        for (int i = 0; i < pcm.length; i++) {
            double lfo = Math.sin(st.phase);
            st.phase += 2.0 * Math.PI * lfoRate / SAMPLE_RATE;
            int d = baseDelay + (int) (depth * lfo);
            if (d < 1) d = 1;
            if (d >= st.buf.length) d = st.buf.length - 1;
            int readPos = (st.bufPos - d + st.buf.length) % st.buf.length;
            float delayed = st.buf[readPos];
            float s = pcm[i];
            float out = (float) ((1.0 - wet) * s + wet * delayed);
            st.buf[st.bufPos] = s;
            st.bufPos = (st.bufPos + 1) % st.buf.length;
            pcm[i] = clamp(out);
        }
        return pcm;
    }

    /** 颤音：幅度 LFO 调制。 */
    private static short[] tremoloTransform(short[] pcm, UUID speaker, int level) {
        double phase = TREMOLO_PHASE.getOrDefault(speaker, 0.0);
        double rate = 5.0 + level * 1.5;                          // Hz
        double depth = Math.min(0.95, 0.55 + level * 0.1);      // 1 级≈0.65，5 级≈0.95
        for (int i = 0; i < pcm.length; i++) {
            double factor = 1.0 - depth * (0.5 - 0.5 * Math.sin(phase));
            phase += 2.0 * Math.PI * rate / SAMPLE_RATE;
            pcm[i] = clamp(pcm[i] * factor);
        }
        TREMOLO_PHASE.put(speaker, phase);
        return pcm;
    }

    /**
     * 回响（echo）：单条反馈延迟线，产生清晰、可分辨的重复回声。
     * <p>与混响（多条线融合成空间尾音）不同，回响是"明显的延迟重复"：
     * 声音延迟 delay 后原样复读，并逐次衰减。等级越高延迟越长、回声越多越明显。</p>
     */
    private static short[] echoTransform(short[] pcm, UUID speaker, int level) {
        EchoState st = getEchoState(speaker, level);
        float feedback = Math.min(0.8f, 0.5f + level * 0.06f);   // 0.56 -> 0.8，回声衰减
        float wet = Math.min(0.7f, 0.45f + level * 0.05f);       // 湿声比例 0.5 -> 0.7
        for (int i = 0; i < pcm.length; i++) {
            int readPos = (st.bufPos - st.delay + st.buf.length) % st.buf.length;
            float delayed = st.buf[readPos];
            st.buf[st.bufPos] = pcm[i] + delayed * feedback;
            st.bufPos = (st.bufPos + 1) % st.buf.length;
            pcm[i] = clamp(pcm[i] * (1f - wet) + delayed * wet);
        }
        return pcm;
    }

    /** 口吃：填充一小段后重复播放该段 level 次（level=1 即至少重复 1 次，保证默认等级可听）。 */
    private static short[] stutterTransform(short[] pcm, UUID speaker, int level) {
        StutterState st = STUTTER.computeIfAbsent(speaker, k -> new StutterState((int) (0.04 * SAMPLE_RATE)));
        short[] out = new short[pcm.length];
        for (int i = 0; i < pcm.length; i++) {
            if (st.repeatLeft > 0) {
                out[i] = st.chunk[st.rIdx++];
                if (st.rIdx >= st.size) {
                    st.rIdx = 0;
                    st.repeatLeft--;
                }
            } else {
                st.chunk[st.wIdx++] = pcm[i];
                out[i] = pcm[i];
                if (st.wIdx >= st.size) {
                st.wIdx = 0;
                st.rIdx = 0;
                st.repeatLeft = level;
                }
            }
        }
        return out;
    }

    /** 倒放：累积一个固定块后整体反向播放（双缓冲，保证输出长度 == 输入长度）。 */
    private static short[] reverseTransform(short[] pcm, UUID speaker, int level) {
        ReverseState st = getReverseState(speaker, level);
        short[] out = new short[pcm.length];
        int outPos = 0;
        if (st.ready) {
            while (st.outPos < st.outBuf.length && outPos < pcm.length) {
                out[outPos++] = st.outBuf[st.outPos++];
            }
            if (st.outPos >= st.outBuf.length) st.ready = false;
        }
        for (int i = outPos; i < pcm.length; i++) {
            st.inBuf[st.inLen++] = pcm[i];
            if (st.inLen >= st.inBuf.length) {
                for (int k = 0; k < st.inBuf.length; k++) {
                    st.outBuf[k] = st.inBuf[st.inBuf.length - 1 - k];
                }
                st.outPos = 0;
                st.inLen = 0;
                st.ready = true;
                while (st.outPos < st.outBuf.length && outPos < pcm.length) {
                    out[outPos++] = st.outBuf[st.outPos++];
                }
                if (st.outPos >= st.outBuf.length) st.ready = false;
            }
        }
        return out;
    }

    // =========================================================================
    //  工具方法
    // =========================================================================

    /** 自相关基频检测，返回 Hz；无法判定（静音/非浊音）返回 0。 */
    private static double detectPitch(short[] pcm) {
        int n = Math.min(pcm.length, 1600);
        double sum = 0.0;
        for (int i = 0; i < n; i++) sum += (long) pcm[i] * pcm[i];
        double rms = Math.sqrt(sum / n);
        if (rms < 500.0) return 0.0;

        int minLag = 50;                                  // ~960Hz
        int maxLag = Math.min(700, n - 1);                // ~68Hz
        double best = -1.0;
        int bestLag = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            long s = 0;
            for (int i = 0; i + lag < n; i++) s += (long) pcm[i] * pcm[i + lag];
            if (s > best) {
                best = s;
                bestLag = lag;
            }
        }
        if (bestLag <= 0) return 0.0;
        return SAMPLE_RATE / (double) bestLag;
    }

    private static short clamp(double v) {
        if (v > 32767.0) return Short.MAX_VALUE;
        if (v < -32768.0) return Short.MIN_VALUE;
        return (short) Math.round(v);
    }

    /** 清理某说话者的全部状态与 EFX 资源（说话者离开时调用）。 */
    private static void cleanupSpeaker(UUID speaker) {
        HELIUM_SHIFTERS.remove(speaker);
        SYNTH_SHIFTERS.remove(speaker);
        SYNTH_RATIO.remove(speaker);
        CHORUS.remove(speaker);
        TREMOLO_PHASE.remove(speaker);
        STUTTER.remove(speaker);
        REVERSE.remove(speaker);
        ECHO.remove(speaker);

        Integer f = LOWPASS_FILTERS.remove(speaker);
        if (f != null) {
            try {
                EXTEfx.alDeleteFilters(f);
            } catch (Throwable ignored) {}
        }
        int[] r = REVERB_RESOURCES.remove(speaker);
        if (r != null) {
            try {
                EXTEfx.alDeleteAuxiliaryEffectSlots(r[0]);
                EXTEfx.alDeleteEffects(r[1]);
            } catch (Throwable ignored) {}
        }
    }

    /** 卸载时清理全部 EFX 资源。 */
    public static void cleanupAll() {
        for (Map.Entry<UUID, Integer> e : LOWPASS_FILTERS.entrySet()) {
            try {
                EXTEfx.alDeleteFilters(e.getValue());
            } catch (Throwable ignored) {}
        }
        LOWPASS_FILTERS.clear();
        for (Map.Entry<UUID, int[]> e : REVERB_RESOURCES.entrySet()) {
            int[] r = e.getValue();
            if (r != null) {
                try {
                    EXTEfx.alDeleteAuxiliaryEffectSlots(r[0]);
                    EXTEfx.alDeleteEffects(r[1]);
                } catch (Throwable ignored) {}
            }
        }
        REVERB_RESOURCES.clear();
        HELIUM_SHIFTERS.clear();
        SYNTH_SHIFTERS.clear();
        SYNTH_RATIO.clear();
        CHORUS.clear();
        TREMOLO_PHASE.clear();
        STUTTER.clear();
        REVERSE.clear();
        ECHO.clear();
    }

    // =========================================================================
    //  PCM 效果内部状态
    // =========================================================================

    private static final class ChorusState {
        final float[] buf = new float[2048];
        int bufPos = 0;
        double phase = 0.0;
    }

    private static final class StutterState {
        final short[] chunk;
        final int size;
        int wIdx = 0;
        int rIdx = 0;
        int repeatLeft = 0;

        StutterState(int size) {
            this.size = size;
            this.chunk = new short[size];
        }
    }

    private static final class ReverseState {
        final int blockSize;                          // 反向块大小，随等级增大（50ms * 等级）
        final short[] inBuf;
        final short[] outBuf;
        int inLen = 0;
        int outPos = 0;
        boolean ready = false;

        ReverseState(int blockSize) {
            this.blockSize = blockSize;
            this.inBuf = new short[blockSize];
            this.outBuf = new short[blockSize];
        }
    }

    /** 按等级取/建倒放状态：等级越高反向块越长（更明显的倒放感）。等级变化则重建。 */
    private static ReverseState getReverseState(UUID speaker, int level) {
        int blockSize = 2400 * level; // 50ms * 等级（1 级=50ms … 5 级=250ms）
        ReverseState st = REVERSE.get(speaker);
        if (st == null || st.blockSize != blockSize) {
            st = new ReverseState(blockSize);
            REVERSE.put(speaker, st);
        }
        return st;
    }

    private static final class EchoState {
        final int delay;                      // 延迟采样数，随等级增大
        final float[] buf;
        int bufPos = 0;

        EchoState(int level) {
            // 回响间隔：1 级 90ms，每级 +40ms（1 级可清晰分辨，5 级接近 250ms 明显拖尾）
            this.delay = (int) ((0.09 + (level - 1) * 0.04) * SAMPLE_RATE);
            this.buf = new float[delay + 1];
        }
    }

    /** 按等级取/建回响状态：等级越高回声间隔越长。等级变化则重建。 */
    private static EchoState getEchoState(UUID speaker, int level) {
        EchoState st = ECHO.get(speaker);
        if (st == null || st.delay != (int) ((0.09 + (level - 1) * 0.04) * SAMPLE_RATE)) {
            st = new EchoState(level);
            ECHO.put(speaker, st);
        }
        return st;
    }
}
