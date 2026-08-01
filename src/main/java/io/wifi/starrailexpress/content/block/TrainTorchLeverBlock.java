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

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class TrainTorchLeverBlock extends LeverBlock implements LightBlockInterface, SimpleWaterloggedBlock {
    public static final MapCodec<LeverBlock> CODEC = simpleCodec(TrainTorchLeverBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public TrainTorchLeverBlock(Properties properties) {
        super(properties.lightLevel(TrainTorchLeverBlock::lightBlockSupplier));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false)
                .setValue(WATERLOGGED, false)
                .setValue(FACE, AttachFace.WALL).setValue(LIT, true).setValue(ACTIVE, true));
    }

    @Override
    public MapCodec<LeverBlock> codec() {
        return CODEC;
    }

    public static boolean isPowered(BlockState state) {
        if (state.getOptionalValue(POWERED).orElse(true)) {
            return true;
        }
        return false;
    }

    protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
        return true;
    }

    public static boolean isEnabled(BlockState state) {
        if (state.getOptionalValue(ACTIVE).orElse(true)) {
            if (state.getOptionalValue(LIT).orElse(true)) {
                return true;
            }
        }
        return false;
    }

    public static int lightBlockSupplier(BlockState state) {
        return isEnabled(state) ? (isPowered(state) ? 14 : 13) : 0;
    }

    @Override
    protected int getSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
        return isPowered(blockState) ? (isEnabled(blockState) ? 15 : 2) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos,
            Direction direction) {
        return isPowered(blockState) && getConnectedDirection(blockState) == direction
                ? (isEnabled(blockState) ? 15 : 2)
                : 0;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(new Property[] { LIT, ACTIVE, WATERLOGGED });
    }

    @Override
    protected FluidState getFluidState(BlockState blockState) {
        return (Boolean) blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false)
                : super.getFluidState(blockState);
    }
}
