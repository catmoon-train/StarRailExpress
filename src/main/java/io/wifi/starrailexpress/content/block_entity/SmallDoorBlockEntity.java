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

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class SmallDoorBlockEntity extends DoorBlockEntity {

    public SmallDoorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static SmallDoorBlockEntity createGlass(BlockPos pos, BlockState state) {
        return new SmallDoorBlockEntity(TMMBlockEntities.SMALL_GLASS_DOOR, pos, state);
    }

    public static SmallDoorBlockEntity createCustom(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        return new SmallDoorBlockEntity(type, pos, state);
    }

    public static SmallDoorBlockEntity createWood(BlockPos pos, BlockState state) {
        return new SmallDoorBlockEntity(TMMBlockEntities.SMALL_WOOD_DOOR, pos, state);
    }

    @Override
    protected void toggleBlocks() {
        if (this.level == null) {
            return;
        }
        this.level.setBlock(this.worldPosition, this.getBlockState().setValue(SmallDoorBlock.OPEN, this.open),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        this.level.setBlock(this.worldPosition.above(), this.getBlockState().setValue(SmallDoorBlock.OPEN, this.open)
                .setValue(SmallDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    @Override
    protected void toggleOpen(int ticks) {
        super.toggleOpen(ticks);
    }
}
