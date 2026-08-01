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

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 场景方块通用粒子/音效助手（服务端）。
 * 统一使用原版粒子，通过 {@link ServerLevel#sendParticles} 广播给附近玩家。
 */
public final class SceneParticles {
    private SceneParticles() {
    }

    /** 在一点爆发一团粒子。spread 为各轴随机散布半径，speed 作为粒子初速度/缩放参数。 */
    public static void burst(ServerLevel level, Vec3 center, ParticleOptions particle,
            int count, double spread, double speed) {
        level.sendParticles(particle, center.x, center.y, center.z, count, spread, spread, spread, speed);
    }

    /** 在方块中心爆发一团粒子。 */
    public static void blockBurst(ServerLevel level, BlockPos pos, ParticleOptions particle,
            int count, double spread, double speed) {
        burst(level, Vec3.atCenterOf(pos), particle, count, spread, speed);
    }

    /** 水平圆环粒子（用于范围提示）。 */
    public static void ring(ServerLevel level, Vec3 center, ParticleOptions particle,
            double radius, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            double x = center.x + Math.cos(a) * radius;
            double z = center.z + Math.sin(a) * radius;
            level.sendParticles(particle, x, center.y, z, 1, 0.0, 0.0, 0.0, speed);
        }
    }

    /** 从基点向上的一列粒子。 */
    public static void column(ServerLevel level, BlockPos base, ParticleOptions particle,
            double height, double speed) {
        double cx = base.getX() + 0.5;
        double cz = base.getZ() + 0.5;
        int steps = Math.max(1, (int) Math.ceil(height * 2));
        for (int i = 0; i <= steps; i++) {
            double y = base.getY() + (height * i) / steps;
            level.sendParticles(particle, cx, y, cz, 1, 0.12, 0.0, 0.12, speed);
        }
    }

    /** 向下方扫描一条竖直粒子轨迹（用于坠落/喷射提示）。 */
    public static void columnDown(ServerLevel level, Vec3 top, ParticleOptions particle,
            double height, double speed) {
        int steps = Math.max(1, (int) Math.ceil(height * 2));
        for (int i = 0; i <= steps; i++) {
            double y = top.y - (height * i) / steps;
            level.sendParticles(particle, top.x, y, top.z, 1, 0.05, 0.0, 0.05, speed);
        }
    }

    public static void sound(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** 在一个区域(AABB)内随机散布若干粒子。 */
    public static void regionScatter(ServerLevel level, net.minecraft.world.phys.AABB box,
            ParticleOptions particle, int count) {
        for (int i = 0; i < count; i++) {
            double x = box.minX + level.getRandom().nextDouble() * (box.maxX - box.minX);
            double y = box.minY + level.getRandom().nextDouble() * (box.maxY - box.minY);
            double z = box.minZ + level.getRandom().nextDouble() * (box.maxZ - box.minZ);
            level.sendParticles(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
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
