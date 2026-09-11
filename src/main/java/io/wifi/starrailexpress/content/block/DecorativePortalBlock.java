package io.wifi.starrailexpress.content.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A visual-only portal surface that does not trigger dimension travel. */
public class DecorativePortalBlock extends Block {
    public DecorativePortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis clickedAxis = context.getClickedFace().getAxis();
        Direction.Axis axis;
        if (clickedAxis == Direction.Axis.X) {
            axis = Direction.Axis.Z;
        } else if (clickedAxis == Direction.Axis.Z) {
            axis = Direction.Axis.X;
        } else {
            axis = context.getHorizontalDirection().getAxis() == Direction.Axis.X
                    ? Direction.Axis.Z
                    : Direction.Axis.X;
        }
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
    }
}
