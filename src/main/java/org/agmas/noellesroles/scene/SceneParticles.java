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

package org.agmas.noellesroles.scene;

import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 场景方块通用粒子/音效助手（服务端）。
 * 统一使用原版粒子，通过 {@link ServerLevel#sendParticles} 广播给附近玩家。
 *
 * <p>每个方法都只发一个包（粒子数交给 {@code count}），不要为了"多摆几颗粒子"而在循环里调用它们。
 */
public final class SceneParticles {
    private SceneParticles() {
    }

    /** 在一点爆发一团粒子。spread 为各轴随机散布半径，speed 作为粒子初速度/缩放参数。 */
    public static void burst(ServerLevel level, Vec3 center, ParticleOptions particle,
            int count, double spread, double speed) {
        ParticleFx.burst(level, particle, center.x, center.y, center.z, count, spread, spread, spread, speed);
    }

    /** 在方块中心爆发一团粒子。 */
    public static void blockBurst(ServerLevel level, BlockPos pos, ParticleOptions particle,
            int count, double spread, double speed) {
        burst(level, Vec3.atCenterOf(pos), particle, count, spread, speed);
    }

    /** 水平圆环粒子（用于范围提示）。已退化为以中心为原点的一团。 */
    public static void ring(ServerLevel level, Vec3 center, ParticleOptions particle,
            double radius, int points, double speed) {
        ParticleFx.burst(level, particle, center.x, center.y, center.z, points,
                radius * 0.5D, 0.0D, radius * 0.5D, speed);
    }

    /** 从基点向上的一列粒子。已退化为包住整列的一团。 */
    public static void column(ServerLevel level, BlockPos base, ParticleOptions particle,
            double height, double speed) {
        int steps = Math.max(1, (int) Math.ceil(height * 2));
        ParticleFx.burst(level, particle,
                base.getX() + 0.5D, base.getY() + height * 0.5D, base.getZ() + 0.5D,
                steps + 1,
                0.12D, height * 0.5D, 0.12D, speed);
    }

    /** 向下方扫描一条竖直粒子轨迹（用于坠落/喷射提示）。已退化为包住整列的一团。 */
    public static void columnDown(ServerLevel level, Vec3 top, ParticleOptions particle,
            double height, double speed) {
        int steps = Math.max(1, (int) Math.ceil(height * 2));
        ParticleFx.burst(level, particle, top.x, top.y - height * 0.5D, top.z,
                steps + 1,
                0.05D, height * 0.5D, 0.05D, speed);
    }

    public static void sound(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** 在一个区域(AABB)内随机散布若干粒子。合并为一个包。 */
    public static void regionScatter(ServerLevel level, net.minecraft.world.phys.AABB box,
            ParticleOptions particle, int count) {
        ParticleFx.region(level, particle, box, count, 0.0D);
    }

    /**
     * 以方块为基准的场景区域：水平 3×3（左右各 1 格），竖直 4 格（向上）。
     */
    public static net.minecraft.world.phys.AABB sceneRegion(BlockPos pos) {
        return new net.minecraft.world.phys.AABB(
                pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 3, pos.getY() + 5, pos.getZ() + 3);
    }
}
