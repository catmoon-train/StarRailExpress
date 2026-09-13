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

package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.packet.CustomParticleS2CPayload;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端粒子广播助手（仅服务端可用）。
 *
 * <p>本类的每个方法**只调用一次** {@link ServerLevel#sendParticles}，也就是**一次调用 = 一个
 * {@code ClientboundLevelParticlesPacket}**。包里的 {@code count} 是"这一次生成几颗粒子"，不额外
 * 增加包体积，因此需要 N 颗粒子时应当把 N 交给 {@code count}，**绝不要在循环里反复调用本类方法**。
 *
 * <p>散布语义与原版一致：客户端把每颗粒子放在 {@code pos + nextGaussian() * spread}，速度取
 * {@code nextGaussian() * speed}。也就是约 68% 落在 ±spread 内、约 99% 落在 ±3*spread 内。
 * 代价是环形/螺旋/柱状这类"按形状摆点"的效果会退化成中心高斯团——网络与性能优先时的取舍。
 */
public final class ParticleFx {
    private static final Logger LOGGER = LoggerFactory.getLogger("SRE/ParticleFx");

    private ParticleFx() {
    }

    /** 一次发包的通用入口。 */
    public static void burst(ServerLevel level, ParticleOptions particle,
            double x, double y, double z, int count,
            double spreadX, double spreadY, double spreadZ, double speed) {
        if (count <= 0) {
            return;
        }
        level.sendParticles(particle, x, y, z, count, spreadX, spreadY, spreadZ, speed);
    }

    /** 球形体积爆发：原"绕圈摆点"的效果退化为以 center 为中心的一团。 */
    public static void sphere(ServerLevel level, ParticleOptions particle, Vec3 center,
            int count, double radius, double speed) {
        burst(level, particle, center.x, center.y, center.z, count,
                radius * 0.5D, radius * 0.5D, radius * 0.5D, speed);
    }

    /** 两点之间连成的柱体：原"沿轨迹逐点摆粒子"的效果退化为包裹整段的一团。 */
    public static void segment(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to,
            int count, double thickness, double speed) {
        burst(level, particle,
                (from.x + to.x) * 0.5D, (from.y + to.y) * 0.5D, (from.z + to.z) * 0.5D,
                count,
                Math.abs(to.x - from.x) * 0.25D + thickness,
                Math.abs(to.y - from.y) * 0.25D + thickness,
                Math.abs(to.z - from.z) * 0.25D + thickness,
                speed);
    }

    /** AABB 区域散布：原"区域内逐点随机"的效果合并为一个以盒中心为原点的高斯团。 */
    public static void region(ServerLevel level, ParticleOptions particle, AABB box, int count, double speed) {
        Vec3 center = box.getCenter();
        burst(level, particle, center.x, center.y, center.z, count,
                box.getXsize() * 0.25D, box.getYsize() * 0.25D, box.getZsize() * 0.25D, speed);
    }

    /** 与 {@link #region} 相同，但把各轴散布限制在上限内，避免长轨迹被摊成一整片。 */
    public static void regionCapped(ServerLevel level, ParticleOptions particle, AABB box,
            int count, double speed, double maxSpread) {
        Vec3 center = box.getCenter();
        burst(level, particle, center.x, center.y, center.z, count,
                Math.min(box.getXsize() * 0.25D, maxSpread),
                Math.min(box.getYsize() * 0.25D, maxSpread),
                Math.min(box.getZsize() * 0.25D, maxSpread),
                speed);
    }

    // ==================== 自定义形状粒子（服务端发 id，客户端渲染） ====================
    //
    // 原版 sendParticles 只能表达"在一点按高斯散布"，环形/螺旋/沿轨迹/跟随实体等形状无法表达；
    // 而在服务端循环里逐点发包会把包数放大成百上千倍。所以约定：自定义形状 -> 服务端只发一个
    // CustomParticleS2CPayload（id + 原点 + 时长 + 参数），形状由客户端按 id 生成（客户端逐点
    // addParticle 不走网络）。客户端处理器注册见 CustomParticleHandlers。

    /** 自定义特效的广播半径（格）。 */
    public static final double CUSTOM_FX_RANGE = 64.0D;

    /** 发送一条自定义形状特效，持续时长交给客户端处理器自定。 */
    public static void sendCustom(ServerLevel level, ResourceLocation id, Vec3 origin) {
        sendCustom(level, id, origin, 0);
    }

    /**
     * 发送一条自定义形状特效：客户端会按 {@code id} 找处理器，围绕 {@code origin} 自己算形状。
     *
     * @param durationTicks 期望时长（tick），0 表示一次性/由客户端处理器自定
     * @param params        自定义参数，最多 {@code CustomParticleS2CPayload.MAX_PARAMS} 个
     */
    public static void sendCustom(ServerLevel level, ResourceLocation id, Vec3 origin,
            int durationTicks, float... params) {
        sendCustomBatch(level, origin, List.of(CustomParticleS2CPayload.Entry.at(
                id, origin.x, origin.y, origin.z, durationTicks, params)));
    }

    /**
     * 一次发送多条自定义形状特效（合并成一个包，按 {@code center} 选接收者）。
     * 同一时刻有多条特效时请优先用这个，不要循环调用 {@link #sendCustom}。
     */
    public static void sendCustomBatch(ServerLevel level, Vec3 center,
            List<CustomParticleS2CPayload.Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        if (entries.size() > CustomParticleS2CPayload.MAX_ENTRIES) {
            LOGGER.warn("自定义粒子条目过多（{} > {}），已截断；请合并或分批发送。",
                    entries.size(), CustomParticleS2CPayload.MAX_ENTRIES);
            entries = List.copyOf(entries.subList(0, CustomParticleS2CPayload.MAX_ENTRIES));
        }
        // 先看有没有人要看，避免白建包
        List<ServerPlayer> targets = new ArrayList<>();
        double rangeSqr = CUSTOM_FX_RANGE * CUSTOM_FX_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= rangeSqr) {
                targets.add(player);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        CustomParticleS2CPayload payload = new CustomParticleS2CPayload(entries);
        for (ServerPlayer player : targets) {
            PacketTracker.sendToClient(player, payload);
        }
    }
}
