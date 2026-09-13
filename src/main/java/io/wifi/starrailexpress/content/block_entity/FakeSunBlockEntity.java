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

package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 假太阳方块实体。
 *
 * <p>
 * 它本身不保存任何数据，只负责在客户端为所在方块生成一次太阳粒子。
 * 粒子是持久的（见 {@code SunParticle}），方块消失时会自己结束，
 * 所以这里不需要每刻重复生成，也不会有性能负担。
 */
public class FakeSunBlockEntity extends BlockEntity {

    /** 是否已经生成过太阳粒子（只有客户端的方块实体实例会被置为 true）。 */
    private boolean sunSpawned;

    public FakeSunBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.FAKE_SUN, pos, state);
    }

    public static <T extends BlockEntity> void clientTick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        if (!(blockEntity instanceof FakeSunBlockEntity sun) || sun.sunSpawned) {
            return;
        }
        sun.sunSpawned = true;
        level.addParticle(TMMParticles.SUN, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
    }
}
