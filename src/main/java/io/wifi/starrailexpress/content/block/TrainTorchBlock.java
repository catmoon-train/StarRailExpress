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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class TrainTorchBlock extends BaseTorchBlock implements LightBlockInterface, SimpleWaterloggedBlock {
    public static SimpleParticleType flameParticle = ParticleTypes.FLAME;

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final MapCodec<TrainTorchBlock> CODEC = simpleCodec(TrainTorchBlock::new);

    public MapCodec<? extends TrainTorchBlock> codec() {
        return CODEC;
    }

    public TrainTorchBlock(Properties properties) {
        super(properties.lightLevel(TrainTorchBlock::lightBlockSupplier));
        this.registerDefaultState(
                this.defaultBlockState().setValue(LIT, true).setValue(ACTIVE, true).setValue(WATERLOGGED, false));
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
        return isEnabled(state) ? 14 : 0;
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { LIT, ACTIVE, WATERLOGGED });
    }

    protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
        return true;
    }

    public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
        if (!isEnabled(blockState))
            return;
        double d = (double) blockPos.getX() + (double) 0.5F;
        double e = (double) blockPos.getY() + 0.7;
        double f = (double) blockPos.getZ() + (double) 0.5F;
        level.addParticle(ParticleTypes.SMOKE, d, e, f, (double) 0.0F, (double) 0.0F, (double) 0.0F);
        level.addParticle(flameParticle, d, e, f, (double) 0.0F, (double) 0.0F, (double) 0.0F);
    }
    
    @Override
    protected FluidState getFluidState(BlockState blockState) {
        return (Boolean) blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false)
                : super.getFluidState(blockState);
    }
}
