package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MultifaceSpreader;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

public class PanelBlock extends MultifaceBlock {

    public PanelBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends MultifaceBlock> codec() {
        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return state;
    }

    @Override
    public boolean isValidStateForPlacement(BlockGetter world, BlockState state, BlockPos pos, Direction direction) {
        return this.isFaceSupported(direction) && (!state.is(this) || !hasFace(state, direction));
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(this.asItem())
                && Arrays.stream(DIRECTIONS).anyMatch(direction -> !hasFace(state, direction))
                && !context.isSecondaryUseActive();
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return null;
    }

}
