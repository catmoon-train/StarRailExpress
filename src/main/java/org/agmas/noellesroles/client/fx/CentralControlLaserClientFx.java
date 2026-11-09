package org.agmas.noellesroles.client.fx;

import org.agmas.noellesroles.game.roles.innocence.central_control.CentralControlRole;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 中控员激光模块的客户端表现：把「起点 → 终点」这条线段画成一道红色激光。
 *
 * <p>
 * 服务端只发一个 {@code CustomParticleS2CPayload}（起点 + 终点坐标），
 * 这里的处理器在本地逐点 {@code addParticle} 摆出线段形状，不产生任何网络流量。
 */
public final class CentralControlLaserClientFx {

    /** 红色激光的粒子颜色（Minecraft 的 dust 粒子用 0~1 的 RGB）。 */
    private static final Vector3f LASER_COLOR = new Vector3f(1.0F, 0.09F, 0.06F);
    /** 沿线段摆放的点数。 */
    private static final int POINTS = 48;

    private CentralControlLaserClientFx() {
    }

    public static void register() {
        CustomParticleHandlers.register(CentralControlRole.LASER_BEAM_FX, CentralControlLaserClientFx::paintBeam);
    }

    /**
     * 参数约定：{@code params = [endX, endY, endZ]}，起点就是服务端给的原点。
     */
    private static void paintBeam(ClientLevel level, Vec3 origin, int durationTicks, float[] params) {
        if (params.length < 3) {
            return;
        }
        Vec3 end = new Vec3(params[0], params[1], params[2]);
        Vec3 delta = end.subtract(origin);
        double length = delta.length();
        if (length <= 0.001D) {
            return;
        }
        var dust = new DustParticleOptions(LASER_COLOR, 0.7F);
        for (int i = 0; i <= POINTS; i++) {
            double t = (double) i / POINTS;
            Vec3 point = origin.add(delta.scale(t));
            // 起点附近更亮一点，表现出「从镜头射出」的感觉
            double scale = i < 2 ? 1.8F : 1.0F;
            level.addParticle(new DustParticleOptions(LASER_COLOR, (float) (0.7F * scale)),
                    point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
        }
        // 命中点 / 落点额外一团高亮
        level.addParticle(dust, end.x, end.y, end.z, 0.0D, 0.0D, 0.0D);
    }
}
