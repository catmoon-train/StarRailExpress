package org.agmas.noellesroles.client;

import org.agmas.noellesroles.game.command_block.NullFx;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Null 的注销特效。客户端本地摆点，不额外发包。
 * <p>
 * 三种形状：前摇标记（一圈灰白）、抹除命中（三重环 + 灵魂柱 + 白闪）、涂黑（电弧环）。
 */
public final class NullRiftClientFx {
    private static final DustParticleOptions ASH = dust(0.36F, 0.36F, 0.44F, 1.5F);
    private static final DustParticleOptions BONE = dust(0.86F, 0.86F, 0.92F, 1.2F);
    private static final DustParticleOptions COLD = dust(0.52F, 0.70F, 1.00F, 1.1F);

    private NullRiftClientFx() {
    }

    public static void register() {
        CustomParticleHandlers.register(NullFx.RIFT, NullRiftClientFx::play);
    }

    private static void play(ClientLevel level, Vec3 origin, int durationTicks, float[] params) {
        float radius = params.length > 0 ? params[0] : 1.6F;
        float style = params.length > 1 ? params[1] : 0.0F;
        int kind = Math.round(style);
        switch (kind) {
            case 1 -> erase(level, origin, radius);
            case 2 -> redact(level, origin, radius);
            default -> mark(level, origin, radius);
        }
    }

    /** 前摇：脚下一圈很薄的灰环，越靠近中心越暗。 */
    private static void mark(ClientLevel level, Vec3 origin, float radius) {
        ring(level, origin, radius, 28, ASH);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D;
            level.addParticle(ParticleTypes.SQUID_INK,
                    origin.x + Math.cos(angle) * radius * 0.7D,
                    origin.y + 0.2D,
                    origin.z + Math.sin(angle) * radius * 0.7D,
                    0.0D, 0.02D, 0.0D);
        }
    }

    /** 命中：三重扩散环 + 上冲的灵魂柱 + 一次白闪。 */
    private static void erase(ClientLevel level, Vec3 origin, float radius) {
        ring(level, origin, radius, 40, ASH);
        ring(level, origin, radius * 1.7F, 32, COLD);
        ring(level, origin, radius * 2.4F, 24, BONE);

        // 灵魂柱：从脚底一路抽到头顶以上，表示「被从名单里抽走」
        for (int i = 0; i < 22; i++) {
            double y = origin.y + i * 0.14D;
            double spread = 0.35D * (1.0D - i / 26.0D);
            level.addParticle(ParticleTypes.SOUL,
                    origin.x + (level.random.nextDouble() - 0.5D) * spread,
                    y,
                    origin.z + (level.random.nextDouble() - 0.5D) * spread,
                    0.0D, 0.10D, 0.0D);
            if (i % 4 == 0) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        origin.x, y, origin.z, 0.0D, 0.12D, 0.0D);
            }
        }
        // 反向传送门碎屑向外飞散，像被抹掉的字迹
        for (int i = 0; i < 26; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double speed = 0.12D + level.random.nextDouble() * 0.22D;
            level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    origin.x, origin.y + 0.6D, origin.z,
                    Math.cos(angle) * speed, 0.06D + level.random.nextDouble() * 0.10D, Math.sin(angle) * speed);
        }
        level.addParticle(ParticleTypes.FLASH, origin.x, origin.y + 1.1D, origin.z, 0.0D, 0.0D, 0.0D);
    }

    /** 涂黑：一圈电弧 + 一圈灰环，表现「拉闸」。 */
    private static void redact(ClientLevel level, Vec3 origin, float radius) {
        ring(level, origin, radius, 48, COLD);
        int arcs = Math.max(16, (int) (radius * 2.0F));
        for (int i = 0; i < arcs; i++) {
            double angle = Math.PI * 2.0D * i / arcs;
            double r = radius * (0.35D + level.random.nextDouble() * 0.65D);
            double y = origin.y + 0.15D + level.random.nextDouble() * 2.2D;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    origin.x + Math.cos(angle) * r,
                    y,
                    origin.z + Math.sin(angle) * r,
                    0.0D, 0.04D, 0.0D);
            if (i % 5 == 0) {
                level.addParticle(ParticleTypes.ASH,
                        origin.x + Math.cos(angle) * r,
                        y,
                        origin.z + Math.sin(angle) * r,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void ring(ClientLevel level, Vec3 origin, float radius, int count, DustParticleOptions dust) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            level.addParticle(dust,
                    origin.x + Math.cos(angle) * radius,
                    origin.y + 0.08D,
                    origin.z + Math.sin(angle) * radius,
                    0.0D, 0.03D, 0.0D);
        }
    }

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }
}
