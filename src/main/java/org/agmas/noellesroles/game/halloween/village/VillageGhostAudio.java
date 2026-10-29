package org.agmas.noellesroles.game.halloween.village;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 听者侧 PCM 鬼声：两级低通、环形调制及短回声。绝不挂载可泄漏到音效的 OpenAL 滤波器。 */
public final class VillageGhostAudio {
    private static volatile Set<UUID> allowed = Set.of();
    private static final Map<UUID, Filter> FILTERS = new ConcurrentHashMap<>();
    private VillageGhostAudio() {}

    private static final long GHOST_CHANNEL = 0x48414C4C4F574545L;
    public static UUID channel(UUID speaker) {
        return new UUID(GHOST_CHANNEL, speaker.getMostSignificantBits() ^ speaker.getLeastSignificantBits());
    }
    public static boolean isGhostChannel(UUID id) { return id != null && id.getMostSignificantBits() == GHOST_CHANNEL; }

    public static void setAllowed(Set<UUID> souls) {
        allowed = Set.copyOf(souls);
        FILTERS.keySet().removeIf(uuid -> !souls.contains(uuid) && !isGhostChannel(uuid));
    }
    public static void clear() { allowed = Set.of(); FILTERS.clear(); }

    /** 标记频道不等待实体/夜晚状态包，以免新死者第一帧漏出清晰原声。 */
    public static short[] transform(UUID speaker, short[] pcm) {
        if (speaker == null || pcm == null || (!isGhostChannel(speaker) && !allowed.contains(speaker))) return pcm;
        Filter state = FILTERS.computeIfAbsent(speaker, ignored -> new Filter());
        short[] result = new short[pcm.length];
        synchronized (state) {
            for (int i = 0; i < pcm.length; i++) {
                state.low1 += .065F * (pcm[i] - state.low1);
                state.low2 += .065F * (state.low1 - state.low2);
                float delayed = state.delay[state.cursor];
                float wet = state.low2 + delayed * .5F;
                state.delay[state.cursor] = wet * .65F;
                state.cursor = (state.cursor + 1) % state.delay.length;
                double ring = .3 + .7 * Math.sin(state.phase);
                state.phase = (state.phase + 2 * Math.PI * 31 / 48000) % (2 * Math.PI);
                double output = Math.tanh(wet / 12000 * ring) * 15500;
                result[i] = (short) Math.clamp(Math.round(output), -32768, 32767);
            }
        }
        return result;
    }

    private static final class Filter {
        final float[] delay = new float[5760];
        float low1, low2;
        double phase;
        int cursor;
    }
}
