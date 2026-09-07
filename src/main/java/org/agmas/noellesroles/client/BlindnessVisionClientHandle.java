package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

/**
 * Fabric-side sound source collector for the Blindness mod's vision model.
 *
 * <p>The original mod keeps sound sources for two seconds and uploads a 384-entry
 * std140 block. The Fabric port keeps the same values and lifetime, while using a
 * small set of ordinary vec4 uniforms so the render path remains portable across
 * the OpenGL drivers used by Minecraft.</p>
 */
public final class BlindnessVisionClientHandle {
    /** Uniforms are deliberately kept small and portable; this avoids vendor-specific GL buffer uploads. */
    public static final int MAX_SOURCES = 16;
    private static final long SOURCE_LIFETIME_MS = 2_000L;
    private static final int MAX_QUEUED_SOURCES = 64;

    public static volatile boolean active;

    private static final Queue<PendingSource> PENDING = new ArrayDeque<>();
    private static final List<Source> SOURCES = new ArrayList<>(MAX_SOURCES);
    private static final float[] VIEW_DATA = new float[MAX_SOURCES * 8];
    private static final SoundEventListener SOUND_LISTENER = BlindnessVisionClientHandle::onSoundEvent;

    private static boolean listenerRegistered;

    private BlindnessVisionClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BlindnessVisionClientHandle::clientTick);
    }

    private static void clientTick(Minecraft client) {
        LocalPlayer player = client.player;
        active = player != null && player.hasEffect(ModEffects.BLIND_VISION);

        if (!listenerRegistered && client.getSoundManager() != null) {
            client.getSoundManager().addListener(SOUND_LISTENER);
            listenerRegistered = true;
        }
    }

    private static void onSoundEvent(SoundInstance sound, WeighedSoundEvents ignored, float ignoredRange) {
        recordSound(sound, false);
    }

    /** Called by the SoundEngine mixin for sounds that remain active between play callbacks. */
    public static void recordLoopingSound(SoundInstance sound) {
        recordSound(sound, true);
    }

    /** Direct fallback for the SoundEngine play hook; it does not depend on SoundManager listeners. */
    public static void recordPlayedSound(SoundInstance sound) {
        recordSound(sound, false);
    }

    private static void recordSound(SoundInstance sound, boolean longLived) {
        if (sound == null) {
            return;
        }
        if (sound.isRelative()) {
            return;
        }
        if (sound.getAttenuation() != SoundInstance.Attenuation.LINEAR) {
            return;
        }

        try {
            var resolved = sound.getSound();
            if (resolved == null) {
                return;
            }

            float soundVolume = Math.max(0.0f, sound.getVolume());
            if (soundVolume <= 0.0f) {
                return;
            }

            float range = Math.max(1.0f, soundVolume) * resolved.getAttenuationDistance();
            float volume = Math.min(1.0f, soundVolume);
            if (longLived) {
                Minecraft client = Minecraft.getInstance();
                if (client.options.getSoundSourceVolume(sound.getSource()) <= 0.0f) {
                    return;
                }
                Vec3 listener = client.getSoundManager().getListenerTransform().position();
                Vec3 source = new Vec3(sound.getX(), sound.getY(), sound.getZ());
                if (listener.distanceToSqr(source) >= range * range) {
                    return;
                }
            }
            if (longLived || sound.getSource() == SoundSource.RECORDS) {
                volume *= 0.1f;
            }

            synchronized (PENDING) {
                if (PENDING.size() >= MAX_QUEUED_SOURCES) {
                    PENDING.poll();
                }
                PENDING.add(new PendingSource(sound.getX(), sound.getY(), sound.getZ(), range, volume,
                        Util.getMillis()));
            }
        } catch (RuntimeException ignored) {
            // A sound can be unloaded during resource reload; it is not a vision source then.
        }
    }

    /** Prepares uniform rows in world space for the current post-process pass. */
    public static void prepareForRender(Camera camera) {
        long now = Util.getMillis();
        synchronized (PENDING) {
            PendingSource pending;
            while ((pending = PENDING.poll()) != null) {
                PendingSource current = pending;
                // tickNonPaused samples looping sounds every tick. Replace the
                // previous record at the same position instead of allowing one
                // looping sound to consume all uniform slots.
                SOURCES.removeIf(source -> source.samePosition(current.x, current.y, current.z));
                if (SOURCES.size() >= MAX_SOURCES) {
                    SOURCES.remove(0);
                }
                SOURCES.add(new Source(current.x, current.y, current.z, current.range, current.volume,
                        current.time));
            }
        }

        for (Iterator<Source> it = SOURCES.iterator(); it.hasNext();) {
            if (now - it.next().time > SOURCE_LIFETIME_MS) {
                it.remove();
            }
        }

        int count = Math.min(MAX_SOURCES, SOURCES.size());
        for (int i = 0; i < count; i++) {
            Source source = SOURCES.get(i);
            int offset = i * 8;
            // Keep the original Forge representation: Source.pos is an absolute
            // world position. The fragment shader converts its depth sample back
            // to world space before comparing it with this value.
            VIEW_DATA[offset] = (float) source.x;
            VIEW_DATA[offset + 1] = (float) source.y;
            VIEW_DATA[offset + 2] = (float) source.z;
            VIEW_DATA[offset + 3] = source.range;
            float age = Mth.clamp((now - source.time) / (float) SOURCE_LIFETIME_MS, 0.0f, 1.0f);
            // Match the Forge std140 Source.cfg layout exactly:
            // cfg[0] is padding, cfg[1] is volume, cfg[2] is age, cfg[3] is random.
            VIEW_DATA[offset + 4] = 0.0f;
            VIEW_DATA[offset + 5] = source.volume;
            VIEW_DATA[offset + 6] = age;
            VIEW_DATA[offset + 7] = 0.0f;
        }
        for (int i = count * 8; i < VIEW_DATA.length; i++) {
            VIEW_DATA[i] = 0.0f;
        }

    }

    public static float sourceValue(int source, int value) {
        if (source < 0 || source >= MAX_SOURCES || value < 0 || value >= 8) {
            return 0.0f;
        }
        return VIEW_DATA[source * 8 + value];
    }

    private record PendingSource(double x, double y, double z, float range, float volume, long time) {
    }

    private record Source(double x, double y, double z, float range, float volume, long time) {
        private boolean samePosition(double otherX, double otherY, double otherZ) {
            double dx = x - otherX;
            double dy = y - otherY;
            double dz = z - otherZ;
            return dx * dx + dy * dy + dz * dz < 0.0625;
        }
    }
}
