package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class GlassPanelBlock extends DirectionalBlock {

    public static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 12, 16, 16, 16);
    public static final VoxelShape NORTH_COLLISION_SHAPE = Block.box(0, 0, 15, 16, 16, 16);
    public static final VoxelShape EAST_SHAPE = Block.box(0, 0, 0, 4, 16, 16);
    public static final VoxelShape EAST_COLLISION_SHAPE = Block.box(0, 0, 0, 1, 16, 16);
    public static final VoxelShape SOUTH_SHAPE = Block.box(0, 0, 0, 16, 16, 4);
    public static final VoxelShape SOUTH_COLLISION_SHAPE = Block.box(0, 0, 0, 16, 16, 1);
    public static final VoxelShape WEST_SHAPE = Block.box(12, 0, 0, 16, 16, 16);
    public static final VoxelShape WEST_COLLISION_SHAPE = Block.box(15, 0, 0, 16, 16, 16);
    public static final VoxelShape UP_SHAPE = Block.box(0, 0, 0, 16, 4, 16);
    public static final VoxelShape UP_COLLISION_SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    public static final VoxelShape DOWN_SHAPE = Block.box(0, 12, 0, 16, 16, 16);
    public static final VoxelShape DOWN_COLLISION_SHAPE = Block.box(0, 15, 0, 16, 16, 16);

    public GlassPanelBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context.isHoldingItem(this.asItem())) {
            return switch (state.getValue(FACING)) {
                case NORTH -> NORTH_SHAPE;
                case EAST -> EAST_SHAPE;
                case SOUTH -> SOUTH_SHAPE;
                case WEST -> WEST_SHAPE;
                case UP -> UP_SHAPE;
                case DOWN -> DOWN_SHAPE;
            };
        }
        return this.getCollisionShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_COLLISION_SHAPE;
            case EAST -> EAST_COLLISION_SHAPE;
            case SOUTH -> SOUTH_COLLISION_SHAPE;
            case WEST -> WEST_COLLISION_SHAPE;
            case UP -> UP_COLLISION_SHAPE;
            case DOWN -> DOWN_COLLISION_SHAPE;
        };
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        LevelAccessor world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction facing = ctx.getClickedFace();
        BlockState neighborState = world.getBlockState(pos.relative(facing.getOpposite()));
        if (!ctx.isSecondaryUseActive() && neighborState.is(this)) {
            Direction neighborFacing = neighborState.getValue(FACING);
            if (!neighborFacing.getAxis().equals(facing.getAxis())) facing = neighborFacing;
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState stateFrom, Direction direction) {
        Direction facing = state.getValue(FACING);
        if (stateFrom.is(this)) {
            Direction fromFacing = stateFrom.getValue(FACING);
            if (fromFacing.equals(direction)) return facing.equals(direction.getOpposite());
            else if (fromFacing.equals(direction.getOpposite())) return facing.equals(direction);
            else if (fromFacing.equals(facing)) return true;
        }
        return super.skipRendering(state, stateFrom, direction);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return 15;
    }
}
