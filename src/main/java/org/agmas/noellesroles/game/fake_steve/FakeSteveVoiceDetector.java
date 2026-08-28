package org.agmas.noellesroles.game.fake_steve;

import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Decodes microphone frames only while the event is active and reports 0.6 s of loud speech. */
public final class FakeSteveVoiceDetector {
    private static final double THRESHOLD_DBFS = -35.0;
    private static final long REQUIRED_NANOS = 600_000_000L;
    private static final Map<UUID, Meter> METERS = new ConcurrentHashMap<>();

    private FakeSteveVoiceDetector() {
    }

    public static void onMicrophone(MicrophonePacketEvent event, ServerPlayer speaker) {
        if (!FakeSteveDirector.isActive(speaker.serverLevel()) || FakeSteveDirector.isReplaced(speaker)) {
            return;
        }
        Meter meter = METERS.computeIfAbsent(speaker.getUUID(), ignored ->
                new Meter(event.getVoicechat().createDecoder()));
        try {
            short[] samples = meter.decoder.decode(event.getPacket().getOpusEncodedData());
            long now = System.nanoTime();
            long elapsed = meter.lastFrameNanos == 0L ? 20_000_000L
                    : Math.min(100_000_000L, Math.max(0L, now - meter.lastFrameNanos));
            meter.lastFrameNanos = now;
            if (dbfs(samples) > THRESHOLD_DBFS) {
                meter.loudNanos += elapsed;
                if (!meter.triggered && meter.loudNanos >= REQUIRED_NANOS) {
                    meter.triggered = true;
                    speaker.server.execute(() -> FakeSteveAi.onLoudVoice(speaker));
                }
            } else {
                meter.loudNanos = 0L;
                meter.triggered = false;
            }
        } catch (RuntimeException ignored) {
            meter.decoder.resetState();
            meter.loudNanos = 0L;
        }
    }

    public static void clear() {
        for (Meter meter : METERS.values()) {
            if (!meter.decoder.isClosed()) meter.decoder.close();
        }
        METERS.clear();
    }

    private static double dbfs(short[] samples) {
        if (samples == null || samples.length == 0) return Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        for (short sample : samples) {
            double normalized = sample / 32768.0;
            sum += normalized * normalized;
        }
        double rms = Math.sqrt(sum / samples.length);
        return rms <= 0.0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10(rms);
    }

    private static final class Meter {
        private final OpusDecoder decoder;
        private long lastFrameNanos;
        private long loudNanos;
        private boolean triggered;

        private Meter(OpusDecoder decoder) {
            this.decoder = decoder;
        }
    }
}
