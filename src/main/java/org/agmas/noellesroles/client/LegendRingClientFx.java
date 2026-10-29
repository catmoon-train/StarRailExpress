package org.agmas.noellesroles.client;

import org.agmas.noellesroles.game.command_block.LegendFx;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 远境 / 白瞳的光环。本地摆点，不额外发包。 */
public final class LegendRingClientFx {
    private static final DustParticleOptions PURPLE = dust(0.62F, 0.22F, 1.0F, 1.3F);
    private static final DustParticleOptions AMBER = dust(1.0F, 0.62F, 0.15F, 1.2F);
    private static final DustParticleOptions WHITE = dust(0.95F, 0.95F, 1.0F, 1.4F);
    private static final DustParticleOptions GOLD = dust(0.95F, 0.78F, 0.25F, 1.1F);
    private static final DustParticleOptions PAPER = dust(0.96F, 0.93F, 0.84F, 1.2F);
    private static final DustParticleOptions FOG = dust(0.55F, 0.62F, 0.68F, 1.5F);
    private static final DustParticleOptions CANDLE = dust(1.0F, 0.55F, 0.12F, 1.3F);
    private static final DustParticleOptions DREAD = dust(0.35F, 0.05F, 0.45F, 1.4F);
    private static final List<RiftWarning> RIFTS = new ArrayList<>();

    private LegendRingClientFx() {
    }

    public static void register() {
        CustomParticleHandlers.register(LegendFx.RING, LegendRingClientFx::play);
        ClientTickEvents.END_CLIENT_TICK.register(LegendRingClientFx::tickRifts);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RIFTS.clear());
    }

    private static void play(ClientLevel level, Vec3 origin, int durationTicks, float[] params) {
        float radius = params.length > 0 ? params[0] : 2.0F;
        float style = params.length > 1 ? params[1] : 0.0F;
        int kind = Math.round(style);
        if (kind == Math.round(LegendFx.RIFT) && durationTicks > 0) {
            RiftWarning rift = new RiftWarning(level, origin, Math.max(0.2F, Math.min(radius, 8.0F)),
                    Math.max(1, Math.min(durationTicks <= 0 ? 30 : durationTicks, 60)));
            RIFTS.add(rift);
            paintRift(rift);
            return;
        }
        int count = kind == 3 ? 36 : 24;
        DustParticleOptions dust = switch (kind) {
            case 1 -> AMBER;
            case 2, 3 -> WHITE;
            case 4 -> GOLD;
            case 5 -> PAPER;
            case 6 -> FOG;
            case 7 -> GOLD;
            case 8 -> CANDLE;
            case 9 -> DREAD;
            default -> PURPLE;
        };
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            double x = origin.x + Math.cos(angle) * radius;
            double z = origin.z + Math.sin(angle) * radius;
            double y = origin.y + 0.08D;
            level.addParticle(dust, x, y, z, 0.0D, 0.03D, 0.0D);
            if (i % 3 == 0) {
                var spark = kind == 2 || kind == 4 || kind == 7 ? ParticleTypes.END_ROD
                        : kind == 6 ? ParticleTypes.CAMPFIRE_COSY_SMOKE
                        : kind == 8 ? ParticleTypes.FLAME
                        : kind == 5 ? ParticleTypes.CLOUD : ParticleTypes.PORTAL;
                level.addParticle(spark, x, y + 0.4D, z, 0.0D, 0.08D, 0.0D);
            }
        }
        if (kind == 2 || kind == 3) {
            level.addParticle(ParticleTypes.FLASH, origin.x, origin.y + 1.0D, origin.z, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 裂隙前摇持续脉冲，所有摆点均在客户端；换世界、断线或到期后不再补画。 */
    private static void tickRifts(Minecraft client) {
        RIFTS.removeIf(rift -> rift.level != client.level || client.player == null || ++rift.age >= rift.duration);
        for (RiftWarning rift : RIFTS) {
            if (rift.age % 4 == 0) {
                paintRift(rift);
            }
        }
    }

    private static void paintRift(RiftWarning rift) {
        Vec3 at = rift.origin;
        // 用真实判定半径描边，不向外扩圈误导玩家；金白交替提高暗处辨识度。
        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2.0D * i / 48.0D;
            double x = at.x + Math.cos(angle) * rift.radius;
            double z = at.z + Math.sin(angle) * rift.radius;
            rift.level.addAlwaysVisibleParticle(i % 2 == 0 ? WHITE : AMBER, true,
                    x, at.y + 0.12D, z, 0.0D, 0.005D, 0.0D);
            if (i % 6 == 0) {
                rift.level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true,
                        x, at.y + 0.65D, z, 0.0D, 0.015D, 0.0D);
            }
        }
        // 竖向锯齿裂口，让视线平视时也能看清，不只依赖脚下的小圆环。
        for (int i = 0; i < 16; i++) {
            double y = at.y + 0.15D + i * 0.16D;
            double offset = Math.sin(i * 1.7D + rift.age * 0.2D) * 0.14D;
            rift.level.addAlwaysVisibleParticle(i % 3 == 0 ? WHITE : PURPLE, true,
                    at.x + offset, y, at.z, 0.0D, 0.0D, 0.0D);
        }
    }

    private static final class RiftWarning {
        private final ClientLevel level;
        private final Vec3 origin;
        private final float radius;
        private final int duration;
        private int age;

        private RiftWarning(ClientLevel level, Vec3 origin, float radius, int duration) {
            this.level = level;
            this.origin = origin;
            this.radius = radius;
            this.duration = duration;
        }
    }

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }
}
