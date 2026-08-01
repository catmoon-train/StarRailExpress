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

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class LockableButtonBlockEntity extends SmallDoorBlockEntity {

    public LockableButtonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void toggleBlocks() {
    }

    @Override
    protected void toggleOpen(int ticks) {
    }

    public static <T extends LockableButtonBlockEntity> void clientTick(Level world, BlockPos pos, BlockState state,
            T entity) {
        entity.age++;
    }

    public static <T extends LockableButtonBlockEntity> void serverTick(Level world, BlockPos pos, BlockState state,
            T entity) {
        if (entity.isJammed()) {
            entity.setJammed(entity.getJammedTime() - 1);
        }
        if (entity.cooldown > 0)
            entity.cooldown--;
    }
}
