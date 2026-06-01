package org.agmas.noellesroles.voice.client;

import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.voice.HeliumBuzzPlayerComponent;
import org.agmas.noellesroles.voice.HeliumPitchShifter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side receiver for processing voice chat audio with helium effect.
 * This modifies the pitch of voices from players who have the helium effect active.
 */
@Environment(EnvType.CLIENT)
public class HeliumBuzzClientReceiver {

    private static final float RAMP_OUT_TICKS = 10.0F;
    private static final Map<UUID, HeliumPitchShifter> SHIFTERS = new ConcurrentHashMap<>();

    /**
     * Base pitch ratio for helium effect (1.75 = 75% higher pitch)
     */
    private static final float BASE_HELIUM_RATIO = 1.75F;
    private static final int ECHO_DELAY_SAMPLES = 2_400; // ~50ms @48kHz

    /**
     * Register the client-side audio processing event.
     */
    public static void register(EventRegistration r) {
        r.registerEvent(ClientReceiveSoundEvent.EntitySound.class, HeliumBuzzClientReceiver::onReceiveEntity);
    }

    private static void onReceiveEntity(ClientReceiveSoundEvent.EntitySound event) {
        if (event.isCancelled()) {
            return;
        }

        short[] pcm = event.getRawAudio();
        if (pcm == null || pcm.length == 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        UUID speaker = event.getEntityId();
        if (speaker == null) {
            return;
        }

        Player player = mc.level.getPlayerByUUID(speaker);
        if (player == null) {
            SHIFTERS.remove(speaker);
            return;
        }

        HeliumBuzzPlayerComponent comp = ModComponents.HELIUM_BUZZ.get(player);
        boolean hasHelium = comp != null && comp.isActive();
        boolean hasHeavyMetal = player.hasEffect(ModEffects.HEAVY_METAL_VOICE);
        int echoCount = ModEffects.getVoiceEchoCount(player);
        if (!hasHelium && !hasHeavyMetal && echoCount <= 0) {
            SHIFTERS.remove(speaker);
            return;
        }

        float ratio = hasHelium ? pitchRatioFor(comp) : 1.0F;
        if (hasHeavyMetal) {
            ratio *= ModEffects.getHeavyMetalPitchRatio(player);
        }
        HeliumPitchShifter shifter = SHIFTERS.computeIfAbsent(speaker, k -> new HeliumPitchShifter());
        short[] shifted = shifter.process(pcm, ratio);
        event.setRawAudio(applyEcho(shifted, echoCount));
    }

    /**
     * Calculate the pitch ratio based on the remaining effect time.
     * Applies a fade-out effect when the effect is about to end.
     */
    private static float pitchRatioFor(HeliumBuzzPlayerComponent comp) {
        int remaining = comp.getTicksRemaining();
        if (remaining >= (int) RAMP_OUT_TICKS) {
            return BASE_HELIUM_RATIO;
        }
        float ramp = Math.max(0.0F, remaining / RAMP_OUT_TICKS);
        return 1.0F + (BASE_HELIUM_RATIO - 1.0F) * ramp;
    }

    private static short[] applyEcho(short[] pcm, int echoCount) {
        if (echoCount <= 0 || pcm.length == 0) {
            return pcm;
        }
        short[] output = pcm.clone();
        for (int echoIndex = 1; echoIndex <= Math.min(echoCount, 5); echoIndex++) {
            int delay = ECHO_DELAY_SAMPLES * echoIndex;
            float decay = 0.45F / echoIndex;
            for (int i = delay; i < output.length; i++) {
                int mixed = output[i] + (int) (pcm[i - delay] * decay);
                output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
            }
        }
        return output;
    }
}
