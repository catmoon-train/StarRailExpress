package org.agmas.noellesroles.client;

import org.agmas.noellesroles.game.command_block.item.ReaperScytheItem;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 镰刀蓄力挥出后的大弧。一个包，本地连着画几下。 */
@Environment(EnvType.CLIENT)
public final class ReaperScytheClientFx {
    private static final DustParticleOptions BLADE = new DustParticleOptions(new Vector3f(0.72F, 0.72F, 0.78F), 1.6F);
    private static final DustParticleOptions VOID = new DustParticleOptions(new Vector3f(0.28F, 0.04F, 0.42F), 1.8F);

    private static Vec3 origin = Vec3.ZERO;
    private static float yaw;
    private static float pitch;
    private static float reach;
    private static int ticks;

    private ReaperScytheClientFx() {
    }

    public static void register() {
        CustomParticleHandlers.register(ReaperScytheItem.SWING, ReaperScytheClientFx::play);
        ClientTickEvents.END_CLIENT_TICK.register(ReaperScytheClientFx::tick);
    }

    private static void play(ClientLevel level, Vec3 at, int durationTicks, float[] params) {
        origin = at;
        yaw = params.length > 0 ? params[0] : 0.0F;
        float lookX = params.length > 1 ? params[1] : 0.0F;
        float lookY = params.length > 2 ? params[2] : 0.0F;
        float lookZ = params.length > 3 ? params[3] : 1.0F;
        reach = params.length > 4 ? Math.max(2.4F, params[4]) : 6.5F;
        pitch = lookPitch(lookX, lookY, lookZ);
        ticks = Math.max(8, durationTicks <= 0 ? 10 : durationTicks + 8);
        paint(level, 0);
    }

    private static void tick(Minecraft client) {
        if (ticks <= 0 || client.level == null) {
            return;
        }
        ticks--;
        paint(client.level, 10 - ticks);
    }

    private static void paint(ClientLevel level, int age) {
        Vec3 forward = look(yaw, pitch);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = forward.cross(up);
        if (side.lengthSqr() < 1.0E-4D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        Vec3 lift = side.cross(forward).normalize();
        int steps = 22;
        double sweep = Mth.clamp(age / 8.0D, 0.15D, 1.0D);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            if (t > sweep + 0.08D) {
                break;
            }
            double along = 1.2D + t * reach;
            double arc = Math.sin(t * Math.PI) * (1.6D + reach * 0.18D);
            double wave = (t - 0.5D) * 1.4D;
            Vec3 pos = origin.add(forward.scale(along)).add(side.scale(wave * 2.2D)).add(lift.scale(arc * 0.35D));
            level.addParticle(i % 2 == 0 ? BLADE : VOID, pos.x, pos.y, pos.z, side.x * 0.04D, 0.02D, side.z * 0.04D);
            if (i % 3 == 0) {
                level.addParticle(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
                level.addParticle(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 0.0D, 0.04D, 0.0D);
            }
            if (i % 5 == 0) {
                level.addParticle(ParticleTypes.SQUID_INK, pos.x, pos.y, pos.z, 0.0D, 0.01D, 0.0D);
            }
        }
    }

    private static float lookPitch(float x, float y, float z) {
        double horizontal = Math.sqrt(x * x + z * z);
        return (float) Math.toDegrees(-Math.atan2(y, horizontal));
    }

    private static Vec3 look(float yawDeg, float pitchDeg) {
        float yawRad = yawDeg * Mth.DEG_TO_RAD;
        float pitchRad = pitchDeg * Mth.DEG_TO_RAD;
        float cos = Mth.cos(pitchRad);
        return new Vec3(-Mth.sin(yawRad) * cos, -Mth.sin(pitchRad), Mth.cos(yawRad) * cos);
    }
}
