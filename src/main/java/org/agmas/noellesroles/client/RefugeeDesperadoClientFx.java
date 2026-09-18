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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modifier.refugee.RefugeeDesperadoFx;
import org.joml.Vector3f;

/**
 * 难民亡命徒出场 / 剑气 / 枪束的客户端形状与微震。
 */
public final class RefugeeDesperadoClientFx {
    private static final DustParticleOptions RED_DUST = new DustParticleOptions(new Vector3f(0.92F, 0.08F, 0.08F), 1.15F);
    private static final DustParticleOptions CRIMSON_DUST = new DustParticleOptions(new Vector3f(0.55F, 0.04F, 0.08F), 1.4F);

    /** 登场演出滤镜的血色（暗红），随演出推进逐渐浸透视野。 */
    private static final int RED_FILTER_RGB = 0xBE1414;
    /** 滤镜最浓时的不透明度。 */
    private static final float RED_FILTER_MAX_ALPHA = 0.62F;
    /** 前 80% 时长逐渐变红，最后 20% 快速褪回。 */
    private static final float RED_FILTER_RAMP_END = 0.8F;
    private static final float RED_FILTER_FADE_START = 0.2F;

    private static int spawnTicks;
    private static int spawnDuration;
    private static Vec3 spawnOrigin = Vec3.ZERO;
    private static int tremorTicks;
    private static int tremorDuration;

    private RefugeeDesperadoClientFx() {
    }

    public static void register() {
        CustomParticleHandlers.register(RefugeeDesperadoFx.SPAWN_ID, (level, origin, durationTicks, params) -> {
            startSpawn(origin, durationTicks <= 0 ? RefugeeDesperadoFx.SPAWN_FOCUS_TICKS : durationTicks);
            paintSpawnFrame(level, origin, 0, spawnDuration);
        });
        CustomParticleHandlers.register(RefugeeDesperadoFx.SLASH_ID, RefugeeDesperadoClientFx::paintSlash);
        CustomParticleHandlers.register(RefugeeDesperadoFx.BEAM_ID, RefugeeDesperadoClientFx::paintBeam);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    public static boolean isTremorActive() {
        return tremorTicks > 0;
    }

    public static CameraJitter computeCamera(float tickDelta) {
        if (tremorTicks <= 0 || SREClientConfig.instance().disableScreenShake) {
            return CameraJitter.NONE;
        }
        float t = tremorDuration - tremorTicks + tickDelta;
        float intensity = Mth.clamp((float) tremorTicks / Math.max(1, tremorDuration), 0.15F, 1.0F) * 0.42F;
        return new CameraJitter(
                Mth.sin(t * 1.55F) * 0.55F * intensity,
                Mth.cos(t * 1.9F) * 0.38F * intensity,
                Mth.sin(t * 2.2F) * 0.045D * intensity,
                Mth.cos(t * 1.7F) * 0.03D * intensity,
                Mth.sin(t * 1.25F) * 0.045D * intensity);
    }

    public record CameraJitter(float yaw, float pitch, double x, double y, double z) {
        static final CameraJitter NONE = new CameraJitter(0, 0, 0, 0, 0);
    }

    /**
     * 亡命徒登场期间的血色滤镜：视野由浅入深逐渐变红，演出尾声褪回。
     * 由 HUD 渲染回调驱动，与运镜黑边同层绘制。
     */
    public static void renderOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (spawnTicks <= 0 || spawnDuration <= 0) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        float partial = deltaTracker == null ? 0.0F
                : Mth.clamp(deltaTracker.getGameTimeDeltaPartialTick(true), 0.0F, 1.0F);
        float progress = Mth.clamp((spawnDuration - spawnTicks + partial) / (float) spawnDuration, 0.0F, 1.0F);
        float ramp = Mth.clamp(progress / RED_FILTER_RAMP_END, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((1.0F - progress) / RED_FILTER_FADE_START, 0.0F, 1.0F);
        float alpha = RED_FILTER_MAX_ALPHA * ramp * ramp * fadeOut;
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        if (a <= 1) {
            return;
        }
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        guiGraphics.fill(0, 0, width, height, (a << 24) | RED_FILTER_RGB);
        // 上下边缘额外加深，形成血色压视野的暗角。
        int edgeAlpha = (int) (a * 0.75F);
        if (edgeAlpha > 1) {
            int barHeight = Math.max(14, Math.round(height * 0.16F));
            int edgeColor = (edgeAlpha << 24) | RED_FILTER_RGB;
            guiGraphics.fill(0, 0, width, barHeight, edgeColor);
            guiGraphics.fill(0, height - barHeight, width, height, edgeColor);
        }
    }

    private static void startSpawn(Vec3 origin, int durationTicks) {
        spawnOrigin = origin;
        spawnDuration = Math.max(20, durationTicks);
        spawnTicks = spawnDuration;
        tremorDuration = spawnDuration;
        tremorTicks = spawnDuration;
    }

    private static void tick(Minecraft client) {
        if (tremorTicks > 0) {
            tremorTicks--;
        }
        if (spawnTicks <= 0 || client.level == null) {
            return;
        }
        int elapsed = spawnDuration - spawnTicks;
        paintSpawnFrame(client.level, spawnOrigin, elapsed, spawnDuration);
        spawnTicks--;
    }

    private static void paintSpawnFrame(ClientLevel level, Vec3 origin, int elapsed, int duration) {
        float progress = duration <= 0 ? 1.0F : Mth.clamp(elapsed / (float) duration, 0.0F, 1.0F);
        double radius = 0.55D + progress * 2.4D;
        int ringCount = 20;
        for (int i = 0; i < ringCount; i++) {
            double angle = (Math.PI * 2.0D * i) / ringCount + elapsed * 0.08D;
            double x = origin.x + Math.cos(angle) * radius;
            double z = origin.z + Math.sin(angle) * radius;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, origin.y + 0.05D, z, 0.0D, 0.04D, 0.0D);
            if (i % 2 == 0) {
                level.addParticle(ParticleTypes.SMOKE, x, origin.y + 0.02D, z, 0.0D, 0.02D, 0.0D);
            }
        }
        if (elapsed % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = level.random.nextDouble() * Math.PI * 2.0D;
                double r = level.random.nextDouble() * (0.4D + progress * 1.2D);
                level.addParticle(ParticleTypes.SOUL,
                        origin.x + Math.cos(a) * r,
                        origin.y + 0.1D,
                        origin.z + Math.sin(a) * r,
                        0.0D, 0.12D + progress * 0.18D, 0.0D);
            }
        }
        if (elapsed % 3 == 0) {
            level.addParticle(ParticleTypes.LAVA, origin.x, origin.y + 0.1D, origin.z, 0.0D, 0.0D, 0.0D);
            level.addParticle(CRIMSON_DUST, origin.x, origin.y + 0.2D + progress, origin.z, 0.0D, 0.05D, 0.0D);
        }
        if (progress > 0.55F) {
            double burst = 1.1D + (progress - 0.55F) * 2.0D;
            for (int i = 0; i < 8; i++) {
                double a = (Math.PI * 2.0D * i) / 8.0D;
                level.addParticle(ParticleTypes.FLAME,
                        origin.x + Math.cos(a) * burst,
                        origin.y + 0.9D,
                        origin.z + Math.sin(a) * burst,
                        Math.cos(a) * 0.04D, 0.02D, Math.sin(a) * 0.04D);
            }
        }
    }

    private static void paintSlash(ClientLevel level, Vec3 origin, int durationTicks, float[] params) {
        float yaw = params.length > 0 ? params[0] : 0.0F;
        float pitch = params.length > 1 ? params[1] : 0.0F;
        double length = params.length > 2 ? params[2] : 3.45D;
        float strength = params.length > 3 ? params[3] : 1.0F;
        Vec3 forward = lookFromYawPitch(yaw, pitch);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = forward.cross(up);
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        int steps = 10;
        int arcs = strength > 0.6F ? 3 : 2;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 along = origin.add(forward.scale(t * length));
            double arc = Math.sin(t * Math.PI) * (0.28D + strength * 0.55D);
            for (int a = 0; a < arcs; a++) {
                double offset = arcs <= 1 ? 0.0D : (a / (double) (arcs - 1) - 0.5D) * 1.35D * arc;
                Vec3 pos = along.add(side.scale(offset)).add(0.0D, Math.sin(t * Math.PI) * 0.12D, 0.0D);
                if (i % 2 == 0) {
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 0.0D, 0.01D, 0.0D);
                } else {
                    level.addParticle(CRIMSON_DUST, pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private static void paintBeam(ClientLevel level, Vec3 origin, int durationTicks, float[] params) {
        float yaw = params.length > 0 ? params[0] : 0.0F;
        float pitch = params.length > 1 ? params[1] : 0.0F;
        double length = params.length > 2 ? params[2] : 20.0D;
        Vec3 forward = lookFromYawPitch(yaw, pitch);
        int steps = 28;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 pos = origin.add(forward.scale(t * length));
            level.addParticle(RED_DUST, pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
            if (i % 2 == 0) {
                level.addParticle(ParticleTypes.CRIMSON_SPORE, pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static Vec3 lookFromYawPitch(float yaw, float pitch) {
        float yawRad = yaw * Mth.DEG_TO_RAD;
        float pitchRad = pitch * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitchRad);
        return new Vec3(-Mth.sin(yawRad) * cosPitch, -Mth.sin(pitchRad), Mth.cos(yawRad) * cosPitch);
    }
}
