package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds;
import org.joml.Vector3f;

/**
 * 信标占位圈：沿半径铺一圈尘粒子，标出「需要站立的范围」。
 *
 * <p>服务端每 20 tick 发一条 {@link EchoHuntFxIds#BEACON_RING}，参数是
 * {@code [半径, 是否被迟滞]}。正常为绿色，被迟滞时为红色——玩家一眼能看出
 * 这座信标现在是不是「站了也不涨」。
 *
 * <p>粒子全部在客户端本地生成，不进网络；丢包只是少一圈，不影响任何判定。
 */
public final class EchoBeaconRingClient {
    private static final DustParticleOptions GREEN = dust(0.28F, 0.82F, 0.48F, 1.1F);
    private static final DustParticleOptions RED = dust(0.88F, 0.28F, 0.28F, 1.2F);
    /** 一圈的点数：够密但不至于糊成一条线。 */
    private static final int POINTS = 48;

    private EchoBeaconRingClient() { }

    public static void register() {
        CustomParticleHandlers.register(EchoHuntFxIds.BEACON_RING, EchoBeaconRingClient::play);
    }

    private static void play(ClientLevel level, Vec3 origin, int durationTicks, float[] params) {
        if (level == null || origin == null) return;
        double radius = params.length > 0 ? Math.max(0.5D, params[0]) : 6.0D;
        boolean stalled = params.length > 1 && params[1] > 0.5F;
        var color = stalled ? RED : GREEN;
        // 只画半径圈 + 四个方向短刻线，避免粒子过多。
        for (int i = 0; i < POINTS; i++) {
            double angle = Math.PI * 2.0 * i / POINTS;
            double x = origin.x + Math.cos(angle) * radius;
            double z = origin.z + Math.sin(angle) * radius;
            level.addParticle(color, x, origin.y + 0.08D, z, 0, 0, 0);
        }
        for (int dir = 0; dir < 4; dir++) {
            double angle = Math.PI / 2.0 * dir;
            for (int step = 1; step <= 3; step++) {
                double r = radius * (0.35D + 0.2D * step);
                level.addParticle(color,
                        origin.x + Math.cos(angle) * r, origin.y + 0.08D, origin.z + Math.sin(angle) * r,
                        0, 0, 0);
            }
        }
    }

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }
}
