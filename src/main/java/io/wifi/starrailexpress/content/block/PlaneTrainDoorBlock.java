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

package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.PlaneSmallDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class PlaneTrainDoorBlock extends PlaneSmallDoorBlock {
    public PlaneTrainDoorBlock(Supplier<BlockEntityType<PlaneSmallDoorBlockEntity>> typeSupplier, Properties settings) {
        super(typeSupplier, settings);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        return TrainDoorBlock.useWithoutItemGeneric(
                (s, w, p, pl, h) -> super.useWithoutItem(s, w, p, pl, h), // lambda 调用父类并传递参数
                (a, b, c, d) -> super.open(a, b, c, d), // lambda 调用父类并传递参数
                state, world, pos, player, hit);
    }
}
