/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.client.utils.ModSoundManager;
import org.agmas.noellesroles.init.ModEffects;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 客户端听觉模糊效果：普通游戏音效的 OpenAL 低通滤波与轻微衰减。 */
@Environment(EnvType.CLIENT)
public final class MuffledHearingClientHandle {
    private static final int AL_FILTER_TYPE = 0x8001;
    private static final int AL_FILTER_LOWPASS = 0x0003;
    private static final int AL_FILTER_LOWPASS_GAIN = 0x0001;
    private static final int AL_FILTER_LOWPASS_GAINHF = 0x0002;
    private static final int AL_DIRECT_FILTER = 0x20005;
    private static final int AL_FILTER_NULL = 0;
    private static final int AL_AUXILIARY_SEND_FILTER = 0x20006;
    private static final int AL_FORMAT_MONO16 = 0x1101;
    private static final int AL_BUFFER = 0x1009;
    private static final int AL_LOOPING = 0x1007;
    private static final int AL_GAIN = 0x100A;
    private static final int AL_SOURCE_RELATIVE = 0x0202;
    private static final int AL_EFFECT_TYPE = 0x8001;
    private static final int AL_EFFECT_REVERB = 0x0004;
    private static final int AL_EFFECTSLOT_EFFECT = 0x0001;
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

    /** 由音频线程读取，因此必须是 volatile。 */
    public static volatile boolean active;
    private static volatile int level;
    private static volatile int lowPassFilter;
    private static volatile int reverbSlot;
    private static volatile int reverbEffect;
    private static volatile int noiseSource;
    private static volatile int noiseBuffer;
    private static volatile boolean efxUnavailable;
    private static boolean masterVolumeApplied;

    private MuffledHearingClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MuffledHearingClientHandle::tick);
    }

    public static boolean isLocalPlayerMuffled() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(ModEffects.MUFFLED_HEARING);
    }

    /** 在 Minecraft 的音频线程中为一个 OpenAL 声源应用或移除滤波器。 */
    public static void applyToSource(int source) {
        if (!active) {
            if (lowPassFilter != 0) {
                try {
                    AL11.alSourcei(source, AL_DIRECT_FILTER, AL_FILTER_NULL);
                } catch (Throwable ignored) {
                }
            }
            if (reverbSlot != 0) {
                try {
                    AL11.alSource3i(source, AL_AUXILIARY_SEND_FILTER, 0, 0, 0);
                } catch (Throwable ignored) {
                }
            }
            stopNoiseSource();
            return;
        }

        int filter = getOrCreateFilter();
        if (filter != 0) {
            try {
                // Keep the overall loudness mostly intact and remove the
                // high-frequency detail instead.
                EXTEfx.alFilteri(filter, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
                EXTEfx.alFilterf(filter, AL_FILTER_LOWPASS_GAIN, 0.98f);
                // Level I starts at the old level-V value.
                float highFrequencyGain = 0.03f * (float) Math.pow(0.65f, Math.max(0, level - 1));
                EXTEfx.alFilterf(filter, AL_FILTER_LOWPASS_GAINHF,
                        Math.max(0.001f, highFrequencyGain));
                AL11.alSourcei(source, AL_DIRECT_FILTER, filter);
                applyDiffuseRoom(source);
            } catch (Throwable ignored) {
                efxUnavailable = true;
            }
        }
        ensureNoiseSource();
    }

    public static float getVolumeMultiplier() {
        // Level I is 15% of the normal MASTER volume. Each level removes
        // another 3 percentage points until level VI becomes completely silent.
        return Math.max(0.0f, 0.15f - Math.max(0, level - 1) * 0.03f);
    }

    /** 给声音加很轻的扩散残响，让音源边缘变散，产生混沌感。 */
    private static void applyDiffuseRoom(int source) {
        if (efxUnavailable) {
            return;
        }
        try {
            int slot = getOrCreateReverbSlot();
            if (slot != 0) {
                AL11.alSource3i(source, AL_AUXILIARY_SEND_FILTER, slot, 0, 0);
            }
        } catch (Throwable ignored) {
            efxUnavailable = true;
        }
    }

    public static boolean hasFilter() {
        return lowPassFilter != 0 || noiseSource != 0 || reverbSlot != 0;
    }

    /** OpenAL 设备重载后 source/filter id 会失效，交给下一个音频通道重新创建。 */
    public static void resetOpenALState() {
        lowPassFilter = 0;
        noiseSource = 0;
        noiseBuffer = 0;
        reverbSlot = 0;
        reverbEffect = 0;
        efxUnavailable = false;
    }

    /** 创建一个循环播放的极低电平粉红噪声底，让听觉剥夺不只发生在有声音时。 */
    private static void ensureNoiseSource() {
        try {
            if (noiseSource == 0) {
                noiseBuffer = AL10.alGenBuffers();
                ByteBuffer data = createPinkNoiseBuffer(48000);
                AL10.alBufferData(noiseBuffer, AL_FORMAT_MONO16, data, 48000);
                noiseSource = AL10.alGenSources();
                AL10.alSourcei(noiseSource, AL_BUFFER, noiseBuffer);
                AL10.alSourcei(noiseSource, AL_LOOPING, AL10.AL_TRUE);
                AL10.alSourcei(noiseSource, AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(noiseSource, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f);
            }
            float gain = Math.min(0.02f, 0.008f + Math.max(0, level - 1) * 0.0015f)
                    * getVolumeMultiplier();
            AL10.alSourcef(noiseSource, AL_GAIN, gain);
            if (gain <= 0.0f) {
                stopNoiseSource();
            } else if (AL10.alGetSourcei(noiseSource, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alSourcePlay(noiseSource);
            }
        } catch (Throwable ignored) {
            deleteNoiseSource();
        }
    }

    private static void stopNoiseSource() {
        if (noiseSource != 0) {
            try {
                AL10.alSourceStop(noiseSource);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void deleteNoiseSource() {
        if (noiseSource != 0) {
            try {
                AL10.alDeleteSources(noiseSource);
            } catch (Throwable ignored) {
            }
        }
        if (noiseBuffer != 0) {
            try {
                AL10.alDeleteBuffers(noiseBuffer);
            } catch (Throwable ignored) {
            }
        }
        noiseSource = 0;
        noiseBuffer = 0;
    }

    private static ByteBuffer createPinkNoiseBuffer(int sampleCount) {
        ByteBuffer data = BufferUtils.createByteBuffer(sampleCount * 2).order(ByteOrder.nativeOrder());
        long state = 0x1B873593L;
        float p0 = 0.0f;
        float p1 = 0.0f;
        float p2 = 0.0f;
        float p3 = 0.0f;
        float p4 = 0.0f;
        float p5 = 0.0f;
        float p6 = 0.0f;
        for (int i = 0; i < sampleCount; i++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            float white = (float) ((state >>> 40) / (double) (1L << 24) - 0.5);
            p0 = p0 * 0.99886f + white * 0.0555179f;
            p1 = p1 * 0.99332f + white * 0.0750759f;
            p2 = p2 * 0.96900f + white * 0.1538520f;
            p3 = p3 * 0.86650f + white * 0.3104856f;
            p4 = p4 * 0.55000f + white * 0.5329522f;
            p5 = p5 * -0.7616f - white * 0.0168980f;
            p6 = white * 0.115926f;
            short sample = (short) Math.round((p0 + p1 + p2 + p3 + p4 + p5 + p6) * 2800.0f);
            data.putShort(sample);
        }
        data.flip();
        return data;
    }

    private static int getOrCreateFilter() {
        int filter = lowPassFilter;
        if (filter != 0 || efxUnavailable) {
            return filter;
        }
        try {
            filter = EXTEfx.alGenFilters();
            lowPassFilter = filter;
            return filter;
        } catch (Throwable ignored) {
            efxUnavailable = true;
            return 0;
        }
    }

    private static int getOrCreateReverbSlot() {
        if (reverbSlot != 0) {
            return reverbSlot;
        }
        int slot = 0;
        int effect = 0;
        try {
            slot = EXTEfx.alGenAuxiliaryEffectSlots();
            effect = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(effect, AL_EFFECT_TYPE, AL_EFFECT_REVERB);
            EXTEfx.alEffectf(effect, AL_REVERB_DENSITY, 0.82f);
            EXTEfx.alEffectf(effect, AL_REVERB_DIFFUSION, 0.88f);
            EXTEfx.alEffectf(effect, AL_REVERB_GAIN, 0.12f + level * 0.025f);
            EXTEfx.alEffectf(effect, AL_REVERB_GAINHF, 0.16f);
            EXTEfx.alEffectf(effect, AL_REVERB_DECAY_TIME, 0.28f + level * 0.035f);
            EXTEfx.alEffectf(effect, AL_REVERB_DECAY_HFRATIO, 0.25f);
            EXTEfx.alEffectf(effect, AL_REVERB_REFLECTIONS_GAIN, 0.08f);
            EXTEfx.alEffectf(effect, AL_REVERB_REFLECTIONS_DELAY, 0.007f);
            EXTEfx.alEffectf(effect, AL_REVERB_LATE_REVERB_GAIN, 0.10f);
            EXTEfx.alEffectf(effect, AL_REVERB_LATE_REVERB_DELAY, 0.018f);
            EXTEfx.alAuxiliaryEffectSloti(slot, AL_EFFECTSLOT_EFFECT, effect);
            reverbEffect = effect;
            reverbSlot = slot;
            return slot;
        } catch (Throwable ignored) {
            if (effect != 0) {
                try {
                    EXTEfx.alDeleteEffects(effect);
                } catch (Throwable ignoredDelete) {
                }
            }
            if (slot != 0) {
                try {
                    EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
                } catch (Throwable ignoredDelete) {
                }
            }
            efxUnavailable = true;
            return 0;
        }
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        int newLevel = player == null ? 0 : ModEffects.getMuffledHearingLevel(player);
        active = newLevel > 0;
        level = newLevel;
        if (active) {
            // Same mechanism as Wind Yaose: refresh every currently playing
            // MASTER sound source to the selected fraction of its normal volume.
            ModSoundManager.setGameSoundLevel(getVolumeMultiplier());
            masterVolumeApplied = true;
        } else if (masterVolumeApplied) {
            ModSoundManager.resetGameSoundLevel();
            masterVolumeApplied = false;
        }
    }
}
