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

public class ChimneyBlockEntity extends SyncingBlockEntity {

    public ChimneyBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.CHIMNEY, pos, state);
    }

    public static <T extends BlockEntity> void clientTick(Level world, BlockPos pos, BlockState state, T t) {
        world.addParticle(TMMParticles.BLACK_SMOKE, pos.getX() + .5f, pos.getY(), pos.getZ() + .5f, 0, 0, 0);
    }
}
