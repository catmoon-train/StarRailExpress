package org.agmas.noellesroles.game.command_block;

import java.util.List;

import org.agmas.noellesroles.Noellesroles;

import io.wifi.starrailexpress.network.packet.CustomParticleS2CPayload;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** 命令方块传奇职业的客户端光环。一个包，形状由客户端画。 */
public final class LegendFx {
    public static final ResourceLocation RING = Noellesroles.id("legend_ring");
    public static final float SWAP = 0.0F;
    public static final float RIFT = 1.0F;
    public static final float FLASH = 2.0F;
    public static final float BOOM = 3.0F;
    public static final float STRING = 4.0F;
    public static final float PAPER = 5.0F;
    public static final float FOG = 6.0F;
    public static final float SWING = 7.0F;
    public static final float CANDLE = 8.0F;
    public static final float DREAD = 9.0F;

    private LegendFx() {
    }

    public static void ring(ServerLevel level, Vec3 at, float radius, float style) {
        ParticleFx.sendCustom(level, RING, at, 0, radius, style);
    }

    public static void pair(ServerLevel level, Vec3 a, Vec3 b, float radius, float style) {
        ParticleFx.sendCustomBatch(level, a, List.of(
                CustomParticleS2CPayload.Entry.at(RING, a.x, a.y, a.z, 0, radius, style),
                CustomParticleS2CPayload.Entry.at(RING, b.x, b.y, b.z, 0, radius, style)));
    }
}
