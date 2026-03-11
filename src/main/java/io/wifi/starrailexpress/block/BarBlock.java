package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.index.TMMProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BarBlock extends RotatedPillarBlock {

    public static final BooleanProperty TOP = TMMProperties.TOP;
    public static final BooleanProperty BOTTOM = BlockStateProperties.BOTTOM;

    protected static final VoxelShape X_SHAPE = Block.box(0, 6, 6, 16, 10, 10);
    protected static final VoxelShape Y_SHAPE = Block.box(6, 0, 6, 10, 16, 10);
    protected static final VoxelShape Z_SHAPE = Block.box(6, 6, 0, 10, 10, 16);

    public BarBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(TOP, true)
                .setValue(BOTTOM, true));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.getValue(AXIS);
        if (direction.getAxis() == axis) {
            return state.setValue(
                    direction == this.getTopDirection(axis) ? TOP : BOTTOM,
                    !this.isConnectedBar(neighborState, axis)
            );
        }
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction.Axis axis = ctx.getClickedFace().getAxis();
        Direction topDirection = this.getTopDirection(axis);
        return this.defaultBlockState().setValue(AXIS, ctx.getClickedFace().getAxis())
                .setValue(TOP, !this.isConnectedBar(world.getBlockState(pos.relative(topDirection)), axis))
                .setValue(BOTTOM, !this.isConnectedBar(world.getBlockState(pos.relative(topDirection.getOpposite())), axis));
    }

    private boolean isConnectedBar(BlockState state, Direction.Axis axis) {
        return state.is(this) && state.getValue(AXIS) == axis;
    }

    private Direction getTopDirection(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, TOP, BOTTOM);
    }
}
