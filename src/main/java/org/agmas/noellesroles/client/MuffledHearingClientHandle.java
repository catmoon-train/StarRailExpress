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
import org.agmas.noellesroles.init.ModEffects;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

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
    private static volatile boolean efxUnavailable;

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
            return;
        }

        int filter = getOrCreateFilter();
        if (filter == 0) {
            return;
        }

        try {
            // Keep the overall loudness almost unchanged and remove the
            // high-frequency detail instead.  A low GAINHF is what makes the
            // sound dull/muffled; changing only the category volume merely
            // makes it quieter.
            EXTEfx.alFilteri(filter, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(filter, AL_FILTER_LOWPASS_GAIN, 0.98f);
            // Level I starts at the old level-V value, then keeps removing
            // high-frequency detail for higher amplifiers.
            float highFrequencyGain = 0.03f * (float) Math.pow(0.65f, Math.max(0, level - 1));
            EXTEfx.alFilterf(filter, AL_FILTER_LOWPASS_GAINHF, Math.max(0.001f, highFrequencyGain));
            AL11.alSourcei(source, AL_DIRECT_FILTER, filter);
            applyDiffuseRoom(source);
        } catch (Throwable ignored) {
            efxUnavailable = true;
        }
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
        return lowPassFilter != 0;
    }

    /** OpenAL 设备重载后 source/filter id 会失效，交给下一个音频通道重新创建。 */
    public static void resetOpenALState() {
        lowPassFilter = 0;
        efxUnavailable = false;
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
    }
}
