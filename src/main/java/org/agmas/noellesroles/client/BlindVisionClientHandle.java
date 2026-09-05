/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    10| * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 盲视：采集客户端正在播放的声音，转成会向外传播的回声球，供后处理着色器读取。
 */
public final class BlindVisionClientHandle {
    public static final int MAX_SOURCES = 16;

    public static boolean active;

    private static final ConcurrentLinkedQueue<PendingEcho> PENDING = new ConcurrentLinkedQueue<>();
    private static final List<Echo> ECHOES = new ArrayList<>(MAX_SOURCES);
    private static final Echo[] UPLOAD = new Echo[MAX_SOURCES];
    private static final Vector3f REL_SCRATCH = new Vector3f();
    private static final Quaternionf ROT_SCRATCH = new Quaternionf();
    private static final Matrix4f WORLD_PROJ = new Matrix4f();
    private static final long TIME_ORIGIN = System.nanoTime();
    private static final float MERGE_DIST = 2.8f;
    private static final float ENERGY_DECAY = 0.16f;
    private static final float SELF_RADIUS = 4.2f;
    private static Echo selfEcho;
    private static float lastEchoTime;
    private static int moveHoldTicks;
    private static float capturedNear = 0.05f;
    private static float capturedFar = 256.0f;
    private static float capturedTanHalfFov = 0.7f;
    private static float capturedAspect = 1.777f;
    private static boolean haveProjection;

    private BlindVisionClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BlindVisionClientHandle::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BlindVisionClientHandle::captureMatrices);
    }

    public static void onSound(SoundInstance sound) {
        if (!active || sound == null) {
            return;
        }
        try {
            if (sound.isRelative()) {
                return;
            }
            SoundSource source = sound.getSource();
            if (source == SoundSource.MUSIC || source == SoundSource.RECORDS
                    || source == SoundSource.WEATHER) {
                return;
            }
            if (sound.getAttenuation() == SoundInstance.Attenuation.NONE) {
                return;
            }
            float volume = effectiveVolume(sound);
            if (volume < 0.02f) {
                return;
            }
            PENDING.add(new PendingEcho(sound.getX(), sound.getY(), sound.getZ(), volume, null));
        } catch (RuntimeException ignored) {
        }
    }

    public static void onVoice(UUID speaker, Player player, short[] pcm) {
        if (!active || player == null || pcm == null || pcm.length == 0) {
            return;
        }
        float rms = 0.0f;
        for (short sample : pcm) {
            float n = sample / 32768.0f;
            rms += n * n;
        }
        rms = (float) Math.sqrt(rms / pcm.length);
        if (rms < 0.018f) {
            return;
        }
        float volume = Mth.clamp(rms * 4.2f, 0.2f, 3.5f);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && speaker != null && speaker.equals(mc.player.getUUID())) {
            PENDING.add(new PendingEcho(player.getX(), player.getY() + 0.2, player.getZ(), volume, speaker));
            return;
        }
        PENDING.add(new PendingEcho(player.getX(), player.getY() + player.getEyeHeight() * 0.55,
                player.getZ(), volume, speaker));
    }

    public static int copyEchoes(Echo[] dest) {
        drainPending();
        tickEchoes(nowSeconds());
        int n = Math.min(ECHOES.size(), dest.length);
        for (int i = 0; i < n; i++) {
            dest[i] = ECHOES.get(i);
        }
        return n;
    }

    public static Echo[] uploadBuffer() {
        return UPLOAD;
    }

    public static Vector3f relScratch() {
        return REL_SCRATCH;
    }

    /**
     * 与 {@code AgentListenStepHandler.worldToScreen} 相同的相机空间（相机朝 -Z）。
     */
    public static Vector3f toViewSpace(double worldX, double worldY, double worldZ, Camera camera, Vector3f dest) {
        Vec3 cam = camera.getPosition();
        dest.set((float) (worldX - cam.x), (float) (worldY - cam.y), (float) (worldZ - cam.z));
        camera.rotation().conjugate(ROT_SCRATCH).transform(dest);
        return dest;
    }

    public static float cameraNear() {
        return capturedNear;
    }

    public static float cameraFar() {
        return capturedFar;
    }

    public static float tanHalfFov() {
        return capturedTanHalfFov;
    }

    public static float aspect() {
        return capturedAspect;
    }

    private static void captureMatrices(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx) {
        if (ctx.camera() == null || ctx.projectionMatrix() == null) {
            return;
        }
        WORLD_PROJ.set(ctx.projectionMatrix());
        readProjection(WORLD_PROJ);
        haveProjection = true;
    }

    public static void ensureProjection(Minecraft mc) {
        if (haveProjection) {
            return;
        }
        readProjection(mc.gameRenderer.getProjectionMatrix(mc.options.fov().get()));
    }

    private static void readProjection(Matrix4f proj) {
        float m00 = proj.m00();
        float m11 = proj.m11();
        if (Math.abs(m11) > 1.0e-5f) {
            capturedTanHalfFov = 1.0f / m11;
            if (Math.abs(m00) > 1.0e-5f) {
                capturedAspect = m11 / m00;
            }
        }
        float denomNear = proj.m22() - 1.0f;
        if (Math.abs(denomNear) > 1.0e-5f) {
            float near = proj.m32() / denomNear;
            if (near > 0.001f && near < 8.0f) {
                capturedNear = near;
            }
        }
        float denomFar = proj.m22() + 1.0f;
        if (Math.abs(denomFar) > 1.0e-5f) {
            float far = proj.m32() / denomFar;
            if (far > 16.0f && far < 100000.0f) {
                capturedFar = far;
            }
        }
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        active = player != null && player.hasEffect(ModEffects.BLIND_VISION);
        if (active && player != null && client.level != null) {
            double moved = player.getDeltaMovement().horizontalDistanceSqr();
            boolean stepping = player.walkDist - player.walkDistO > 0.008f || moved > 0.0006;
            if (stepping) {
                moveHoldTicks = 8;
            } else if (moveHoldTicks > 0) {
                moveHoldTicks--;
            }
            maintainSelfEcho(player, moveHoldTicks > 0);
        } else {
            moveHoldTicks = 0;
            if (selfEcho != null) {
                ECHOES.remove(selfEcho);
                selfEcho = null;
            }
        }
    }

    private static void maintainSelfEcho(LocalPlayer player, boolean stepping) {
        if (selfEcho == null || !ECHOES.contains(selfEcho)) {
            if (!stepping) {
                return;
            }
            selfEcho = new Echo();
            selfEcho.voiceId = player.getUUID();
            selfEcho.x = player.getX();
            selfEcho.y = player.getY() + 0.2;
            selfEcho.z = player.getZ();
            selfEcho.targetX = selfEcho.x;
            selfEcho.targetY = selfEcho.y;
            selfEcho.targetZ = selfEcho.z;
            selfEcho.bornAt = nowSeconds();
            selfEcho.displayRadius = 0.0f;
            ECHOES.add(selfEcho);
        }
        selfEcho.targetX = player.getX();
        selfEcho.targetY = player.getY() + 0.2;
        selfEcho.targetZ = player.getZ();
        selfEcho.x += (selfEcho.targetX - selfEcho.x) * 0.22;
        selfEcho.y += (selfEcho.targetY - selfEcho.y) * 0.22;
        selfEcho.z += (selfEcho.targetZ - selfEcho.z) * 0.22;
        selfEcho.maxRadius = SELF_RADIUS;
        selfEcho.speed = 18.0f;
        selfEcho.volume = 0.55f;
        float targetEnergy = stepping ? 1.0f : 0.0f;
        float follow = stepping ? 0.10f : 0.035f;
        selfEcho.energy += (targetEnergy - selfEcho.energy) * follow;
        float targetRadius = selfEcho.maxRadius * Mth.clamp(selfEcho.energy, 0.0f, 1.0f);
        selfEcho.displayRadius += (targetRadius - selfEcho.displayRadius) * 0.08f;
        if (selfEcho.energy < 0.012f && selfEcho.displayRadius < 0.35f) {
            ECHOES.remove(selfEcho);
            selfEcho = null;
        }
    }

    private static void drainPending() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        int amplifier = 0;
        if (player != null) {
            var effect = player.getEffect(ModEffects.BLIND_VISION);
            if (effect != null) {
                amplifier = effect.getAmplifier();
            }
        }
        float rangeMul = 1.0f + amplifier * 0.28f;
        PendingEcho pending;
        while ((pending = PENDING.poll()) != null) {
            absorb(pending, rangeMul);
        }
    }

    private static void absorb(PendingEcho pending, float rangeMul) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer local = mc.player;
        if (local != null) {
            double dx = pending.x - local.getX();
            double dy = pending.y - local.getY();
            double dz = pending.z - local.getZ();
            if (dx * dx + dy * dy + dz * dz > 96.0 * 96.0) {
                return;
            }
            boolean localVoice = pending.voiceId != null && pending.voiceId.equals(local.getUUID());
            boolean nearBody = dx * dx + dy * dy + dz * dz < 1.7;
            if (localVoice || (pending.voiceId == null && nearBody)) {
                if (active) {
                    maintainSelfEcho(local, true);
                    if (selfEcho != null) {
                        float addEnergy = Mth.clamp(0.40f + pending.volume * 0.30f, 0.28f, 1.0f);
                        selfEcho.energy = Math.max(selfEcho.energy, addEnergy);
                        selfEcho.maxRadius = Math.max(selfEcho.maxRadius,
                                Mth.clamp((5.0f + pending.volume * 4.0f) * rangeMul, SELF_RADIUS, 8.5f));
                    }
                }
                return;
            }
        }
        float maxRadius = Mth.clamp((4.5f + pending.volume * 7.5f) * rangeMul, 3.2f, 16.0f);
        float speed = 16.0f + pending.volume * 10.0f;
        float now = nowSeconds();
        float addEnergy = Mth.clamp(0.40f + pending.volume * 0.32f, 0.28f, 1.0f);
        if (pending.voiceId != null) {
            for (Echo echo : ECHOES) {
                if (pending.voiceId.equals(echo.voiceId)) {
                    stitch(echo, pending, maxRadius, speed, addEnergy, now, 0.32f);
                    return;
                }
            }
        }
        Echo nearest = null;
        double nearestDist = MERGE_DIST * MERGE_DIST;
        for (Echo echo : ECHOES) {
            if (echo.voiceId != null) {
                continue;
            }
            double dx = echo.targetX - pending.x;
            double dy = echo.targetY - pending.y;
            double dz = echo.targetZ - pending.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < nearestDist) {
                nearestDist = d2;
                nearest = echo;
            }
        }
        if (nearest != null) {
            stitch(nearest, pending, maxRadius, speed, addEnergy, now, 0.22f);
            return;
        }
        if (ECHOES.size() >= MAX_SOURCES) {
            int worst = 0;
            float worstScore = Float.MAX_VALUE;
            for (int i = 0; i < ECHOES.size(); i++) {
                Echo echo = ECHOES.get(i);
                if (echo == selfEcho) {
                    continue;
                }
                if (echo.energy < worstScore) {
                    worstScore = echo.energy;
                    worst = i;
                }
            }
            if (ECHOES.get(worst) == selfEcho) {
                return;
            }
            ECHOES.remove(worst);
        }
        Echo echo = new Echo();
        echo.x = pending.x;
        echo.y = pending.y;
        echo.z = pending.z;
        echo.targetX = pending.x;
        echo.targetY = pending.y;
        echo.targetZ = pending.z;
        echo.volume = pending.volume;
        echo.maxRadius = maxRadius;
        echo.displayRadius = 0.0f;
        echo.speed = speed;
        echo.bornAt = now;
        echo.energy = addEnergy * 0.55f;
        echo.voiceId = pending.voiceId;
        ECHOES.add(echo);
    }

    private static void stitch(Echo echo, PendingEcho pending, float maxRadius, float speed, float addEnergy, float now,
            float follow) {
        echo.targetX = pending.x;
        echo.targetY = pending.y;
        echo.targetZ = pending.z;
        echo.x += (pending.x - echo.x) * follow;
        echo.y += (pending.y - echo.y) * follow;
        echo.z += (pending.z - echo.z) * follow;
        echo.volume = Math.max(echo.volume * 0.88f, pending.volume);
        echo.maxRadius = Math.max(echo.maxRadius * 0.96f, maxRadius);
        echo.speed = Math.max(echo.speed, speed);
        echo.energy = Math.max(echo.energy, addEnergy);
        echo.bornAt = Math.min(echo.bornAt, now);
    }

    private static void tickEchoes(float now) {
        float dt = Mth.clamp(now - lastEchoTime, 0.0f, 0.05f);
        if (dt < 0.0005f && lastEchoTime != 0.0f) {
            return;
        }
        lastEchoTime = now;
        Iterator<Echo> it = ECHOES.iterator();
        while (it.hasNext()) {
            Echo echo = it.next();
            if (echo == selfEcho) {
                continue;
            }
            echo.x += (echo.targetX - echo.x) * (1.0f - (float) Math.exp(-dt / 0.22f));
            echo.y += (echo.targetY - echo.y) * (1.0f - (float) Math.exp(-dt / 0.22f));
            echo.z += (echo.targetZ - echo.z) * (1.0f - (float) Math.exp(-dt / 0.22f));
            float age = Math.max(0.0f, now - echo.bornAt);
            float traveled = Math.min(echo.maxRadius, age * echo.speed);
            float targetRadius = Math.min(echo.maxRadius,
                    Math.max(traveled, echo.maxRadius * Math.min(1.0f, echo.energy)));
            echo.displayRadius += (targetRadius - echo.displayRadius) * (1.0f - (float) Math.exp(-dt / 0.28f));
            echo.energy = Math.max(0.0f, echo.energy - ENERGY_DECAY * dt);
            if (echo.energy <= 0.02f && echo.displayRadius <= 0.35f) {
                it.remove();
            }
        }
    }

    private static float effectiveVolume(SoundInstance sound) {
        float volume = Math.max(0.0f, sound.getVolume());
        Minecraft mc = Minecraft.getInstance();
        volume *= mc.options.getSoundSourceVolume(sound.getSource());
        volume *= mc.options.getSoundSourceVolume(SoundSource.MASTER);
        try {
            var resolved = sound.getSound();
            if (resolved != null) {
                volume *= Mth.clamp(resolved.getAttenuationDistance() / 16.0f, 0.35f, 2.8f);
            }
        } catch (RuntimeException ignored) {
        }
        return Mth.clamp(volume, 0.0f, 4.0f);
    }

    public static float nowSeconds() {
        return (System.nanoTime() - TIME_ORIGIN) * 1.0e-9f;
    }

    public static final class Echo {
        public double x;
        public double y;
        public double z;
        public double targetX;
        public double targetY;
        public double targetZ;
        public float volume;
        public float maxRadius;
        public float displayRadius;
        public float speed;
        public float bornAt;
        public float energy;
        public UUID voiceId;

        public float currentRadius() {
            return displayRadius;
        }

        public float fade() {
            return Mth.clamp(energy, 0.0f, 1.0f);
        }

        public float clarity() {
            return Mth.clamp(0.45f + volume * 0.4f, 0.4f, 1.0f);
        }
    }

    private record PendingEcho(double x, double y, double z, float volume, UUID voiceId) {
    }
}
