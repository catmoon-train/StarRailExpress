package io.wifi.starrailexpress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class WalkwayBlock extends Block {

    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    protected static final VoxelShape TOP_SHAPE = Block.box(0, 13, 0, 16, 16, 16);
    protected static final VoxelShape BOTTOM_SHAPE = Block.box(0, 0, 0, 16, 3, 16);

    public WalkwayBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState().setValue(HALF, Half.TOP));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction direction = ctx.getClickedFace();
        Half half = direction == Direction.UP ? Half.BOTTOM : Half.TOP;
        if (!ctx.replacingClickedOnBlock() && direction.getAxis().isHorizontal()) {
            half = ctx.getClickLocation().y - (double) ctx.getClickedPos().getY() > 0.5 ? Half.TOP : Half.BOTTOM;
        }
        return this.defaultBlockState().setValue(HALF, half);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == Half.TOP ? TOP_SHAPE : BOTTOM_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }
}
