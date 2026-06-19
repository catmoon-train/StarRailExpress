package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.ClientEmbalmerState;
import org.agmas.noellesroles.init.ModEffects;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAL-based voice effects: Heavy Metal Voice (pitch) and Voice Echo (EFX).
 *
 * Replaces the old PCM-level pitch+echo processing with native OpenAL effects,
 * providing zero-latency, native-performance voice modification.
 *
 * - Heavy Metal Voice: uses AL10.alSourcef(AL_PITCH) to lower voice pitch.
 * - Voice Echo: uses OpenAL EFX ECHO effect via auxiliary effect slots.
 */
public class VoiceEffectsOpenALPlugin implements VoicechatPlugin {

    // EFX constants (defined explicitly for compatibility)
    private static final int AL_EFFECT_ECHO = 0x0003;
    private static final int AL_EFFECT_TYPE = 0x8001;
    private static final int AL_EFFECTSLOT_EFFECT = 0x0001;
    private static final int AL_ECHO_DELAY = 0x0001;
    private static final int AL_ECHO_LRDELAY = 0x0002;
    private static final int AL_ECHO_DAMPING = 0x0003;
    private static final int AL_ECHO_FEEDBACK = 0x0004;
    private static final int AL_ECHO_SPREAD = 0x0005;
    private static final int AL_AUXILIARY_SEND_FILTER = 0x20006;

    /** Track per-player EFX resources: UUID -> {effectSlot, effectId} */
    private static final Map<UUID, int[]> ECHO_RESOURCES = new ConcurrentHashMap<>();

    /** Whether EFX extension is available */
    private static volatile boolean efxAvailable = true;

    @Override
    public String getPluginId() {
        return "noellesroles_voice_effects_openal";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(OpenALSoundEvent.Post.class, this::onOpenALSound);
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    private void onOpenALSound(OpenALSoundEvent.Post event) {
        UUID speakerId = event.getChannelId();
        if (speakerId == null) return;

        int source = event.getSource();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // This plugin is the single authority for the OpenAL source pitch so the
        // Heavy Metal effect and the Embalmer masquerade pitch never overwrite each
        // other (both used to set AL_PITCH independently and fought over fire order).
        //
        // Embalmer pitch is keyed by the speaker UUID and lives in client state even
        // for players the client cannot resolve as an entity, so it is read first.
        float embalmerPitch = ClientEmbalmerState.pitch(speakerId); // 1.0 when inactive
        float heavyMetalRatio = 1.0F;
        int echoCount = 0;

        Player player = mc.level.getPlayerByUUID(speakerId);
        if (player != null) {
            if (player.hasEffect(ModEffects.HEAVY_METAL_VOICE)) {
                heavyMetalRatio = ModEffects.getHeavyMetalPitchRatio(player);
            }
            echoCount = ModEffects.getVoiceEchoCount(player);
        }

        // ---- Combined pitch (always written so it resets to 1.0 when nothing applies) ----
        float pitch = Mth.clamp(embalmerPitch * heavyMetalRatio, 0.4F, 2.0F);
        try {
            AL10.alSourcef(source, AL10.AL_PITCH, pitch);
        } catch (Throwable ignored) {}

        // ---- Voice Echo: OpenAL EFX ECHO ----
        applyEchoEFX(source, speakerId, echoCount);
    }

    /**
     * Apply OpenAL EFX echo effect to the audio source.
     * Creates and caches auxiliary effect slots per player.
     *
     * @param source    OpenAL source ID
     * @param speakerId player UUID for resource tracking
     * @param echoCount number of echo repeats (0 = no echo, max 5)
     */
    private void applyEchoEFX(int source, UUID speakerId, int echoCount) {
        if (echoCount <= 0) {
            // Remove echo effect if present
            removeEcho(source, speakerId);
            return;
        }

        if (!efxAvailable) return;

        try {
            int[] res = ECHO_RESOURCES.computeIfAbsent(speakerId, k -> {
                try {
                    return createEchoEffect(echoCount);
                } catch (Throwable e) {
                    efxAvailable = false;
                    return null;
                }
            });

            if (res == null) return;

            int slot = res[0];
            // Update effect feedback/damping based on echo count
            try {
                int effectId = res[1];
                float feedback = Math.min(0.25f * echoCount, 0.75f);
                EXTEfx.alEffectf(effectId, AL_ECHO_FEEDBACK, feedback);
                EXTEfx.alEffectf(effectId, AL_ECHO_DELAY, 0.04f);
                EXTEfx.alEffectf(effectId, AL_ECHO_LRDELAY, 0.1f);
                EXTEfx.alEffectf(effectId, AL_ECHO_DAMPING, 0.5f);

                // Route source send 1 to the echo effect slot
                AL11.alSource3i(source, AL_AUXILIARY_SEND_FILTER, slot, 0, 0);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    /**
     * Remove echo routing from a source and clean up resources if no longer needed.
     */
    private void removeEcho(int source, UUID speakerId) {
        int[] res = ECHO_RESOURCES.get(speakerId);
        if (res != null) {
            try {
                AL11.alSource3i(source, AL_AUXILIARY_SEND_FILTER, 0, 0, 0);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Create an OpenAL EFX echo effect with an auxiliary effect slot.
     * @return int[2] { slotId, effectId }
     */
    private static int[] createEchoEffect(int echoCount) throws Exception {
        int slot = EXTEfx.alGenAuxiliaryEffectSlots();
        int effect = EXTEfx.alGenEffects();

        // Configure as ECHO effect
        EXTEfx.alEffecti(effect, AL_EFFECT_TYPE, AL_EFFECT_ECHO);
        EXTEfx.alEffectf(effect, AL_ECHO_DELAY, 0.04f);         // 40ms delay
        EXTEfx.alEffectf(effect, AL_ECHO_LRDELAY, 0.1f);        // 100ms LR offset
        EXTEfx.alEffectf(effect, AL_ECHO_DAMPING, 0.5f);        // 50% high-freq damping
        float feedback = Math.min(0.25f * echoCount, 0.75f);
        EXTEfx.alEffectf(effect, AL_ECHO_FEEDBACK, feedback);   // echoCount * 25% feedback
        EXTEfx.alEffectf(effect, AL_ECHO_SPREAD, -1.0f);        // auto spread

        // Bind effect to slot
        EXTEfx.alAuxiliaryEffectSloti(slot, AL_EFFECTSLOT_EFFECT, effect);

        return new int[] { slot, effect };
    }

    /**
     * Clean up all EFX resources (called on plugin unload or game exit).
     */
    public static void cleanupAll() {
        for (Map.Entry<UUID, int[]> entry : ECHO_RESOURCES.entrySet()) {
            int[] res = entry.getValue();
            if (res != null) {
                try {
                    EXTEfx.alDeleteAuxiliaryEffectSlots(res[0]);
                    EXTEfx.alDeleteEffects(res[1]);
                } catch (Throwable ignored) {}
            }
        }
        ECHO_RESOURCES.clear();
    }
}
