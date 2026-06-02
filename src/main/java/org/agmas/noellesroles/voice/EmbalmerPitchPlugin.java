package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import org.agmas.noellesroles.client.ClientEmbalmerState;
import org.lwjgl.openal.AL10;

import java.util.UUID;

/**
 * OpenAL-based voice pitch modification for Embalmer masquerade.
 * 
 * Directly sets the OpenAL source pitch via AL10.alSourcef(AL_PITCH),
 * providing native-performance voice pitch changes without PCM re-processing.
 * 
 * Based on GreysVoicechatPlugin from gexpress.
 */
public class EmbalmerPitchPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "noellesroles_embalmer_pitch";
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
        float pitch = clientPitchFor(event.getChannelId());
        try {
            AL10.alSourcef(event.getSource(), AL10.AL_PITCH, pitch);
        } catch (Throwable ignored) {
        }
    }

    private static float clientPitchFor(UUID playerId) {
        if (playerId == null) return 1.0F;
        float pitch = ClientEmbalmerState.pitch(playerId);
        return Math.max(0.5F, Math.min(2.0F, pitch));
    }
}
