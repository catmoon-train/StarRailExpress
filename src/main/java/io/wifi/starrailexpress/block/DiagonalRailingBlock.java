package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.block.property.RailingShape;
import io.wifi.starrailexpress.index.TMMProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DiagonalRailingBlock extends AbstractRailingBlock {

    public static final BooleanProperty LEFT = TMMProperties.LEFT;
    public static final EnumProperty<RailingShape> SHAPE = TMMProperties.RAILING_SHAPE;
    protected static final VoxelShape NORTH_LEFT_SHAPE = createShape(16, 8, 2, 0, 0, 8, 0);
    protected static final VoxelShape NORTH_RIGHT_SHAPE = createShape(16, 8, 2, 8, 0, 0, 0);
    protected static final VoxelShape EAST_LEFT_SHAPE = createShape(16, 2, 8, 14, 0, 14, 8);
    protected static final VoxelShape EAST_RIGHT_SHAPE = createShape(16, 2, 8, 14, 8, 14, 0);
    protected static final VoxelShape SOUTH_LEFT_SHAPE = createShape(16, 8, 2, 8, 14, 0, 14);
    protected static final VoxelShape SOUTH_RIGHT_SHAPE = createShape(16, 8, 2, 0, 14, 8, 14);
    protected static final VoxelShape WEST_LEFT_SHAPE = createShape(16, 2, 8, 0, 8, 0, 0);
    protected static final VoxelShape WEST_RIGHT_SHAPE = createShape(16, 2, 8, 0, 0, 0, 8);

    public DiagonalRailingBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState().setValue(LEFT, false).setValue(SHAPE, RailingShape.MIDDLE));
    }

    protected static VoxelShape createShape(int height, int sizeX, int sizeZ, int x1, int z1, int x2, int z2) {
        return Shapes.or(
                Block.box(x1, 0, z1, x1 + sizeX, height, z1 + sizeZ),
                Block.box(x2, -8, z2, x2 + sizeX, height - 8, z2 + sizeZ)
        );
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        if (state == null) {
            return null;
        }
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction facing = state.getValue(FACING);
        BlockState stateForStairsBelow = this.getPlacementState(state, world.getBlockState(pos.below()));
        if (stateForStairsBelow != null) {
            boolean left = stateForStairsBelow.getValue(LEFT);
            BlockPos neighborPos = pos.relative(left ? facing.getCounterClockWise() : facing.getClockWise());
            return this.tryConnecting(stateForStairsBelow, world.getBlockState(neighborPos));
        }
        Direction left = facing.getCounterClockWise();
        state = state.setValue(SHAPE, RailingShape.BOTTOM);
        BlockState stateForStairsLeft = this.getPlacementState(state, world.getBlockState(pos.relative(left)));
        if (stateForStairsLeft != null) {
            return stateForStairsLeft;
        }
        Direction right = facing.getClockWise();
        return this.getPlacementState(state, world.getBlockState(pos.relative(right)));
    }

    @Nullable
    private BlockState getPlacementState(BlockState state, BlockState stairsState) {
        Direction facing = state.getValue(FACING);
        if (stairsState.getBlock() instanceof StairBlock) {
            if (stairsState.getValue(StairBlock.HALF) == Half.TOP) {
                return null;
            }

            Direction stairsFacing = stairsState.getValue(StairBlock.FACING);
            StairsShape stairShape = stairsState.getValue(StairBlock.SHAPE);

            if (stairsFacing.getClockWise() == facing) {
                if (stairShape != StairsShape.INNER_RIGHT && stairShape != StairsShape.OUTER_LEFT) {
                    return state.setValue(LEFT, true);
                }
            } else if (stairsFacing.getCounterClockWise() == facing) {
                if (stairShape != StairsShape.INNER_LEFT && stairShape != StairsShape.OUTER_RIGHT) {
                    return state.setValue(LEFT, false);
                }
            } else if (stairsFacing == facing) {
                if (stairShape == StairsShape.OUTER_LEFT) {
                    return state.setValue(LEFT, true);
                } else if (stairShape == StairsShape.OUTER_RIGHT) {
                    return state.setValue(LEFT, false);
                }
            } else if (stairsFacing.getOpposite() == facing) {
                if (stairShape == StairsShape.INNER_LEFT) {
                    return state.setValue(LEFT, false);
                } else if (stairShape == StairsShape.INNER_RIGHT) {
                    return state.setValue(LEFT, true);
                }
            }

        } else if (stairsState.getBlock() instanceof TrimmedStairsBlock) {
            Direction stairsFacing = stairsState.getValue(TrimmedStairsBlock.FACING);
            if (stairsFacing.getClockWise() == facing) {
                return state.setValue(LEFT, false);
            } else if (stairsFacing.getCounterClockWise() == facing) {
                return state.setValue(LEFT, true);
            }
        }
        return null;
    }

    private BlockState tryConnecting(BlockState state, BlockState neighborState) {
        if (state.getValue(SHAPE) != RailingShape.MIDDLE) {
            return state;
        }
        Direction facing = state.getValue(FACING);
        boolean left = state.getValue(LEFT);
        if (neighborState.getBlock() instanceof RailingBlock && neighborState.getValue(FACING) == facing) {
            return state.setValue(SHAPE, RailingShape.TOP);
        } else if (neighborState.getBlock() instanceof RailingPostBlock && neighborState.getValue(FACING) == (left ? facing.getClockWise() : facing)) {
            return state.setValue(SHAPE, RailingShape.TOP);
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        BlockState blockState = super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        if (blockState == null) {
            return null;
        } else if (state.getValue(SHAPE) == RailingShape.MIDDLE) {
            Direction facing = state.getValue(FACING);
            boolean left = state.getValue(LEFT);
            if (neighborPos.equals(pos.relative(left ? facing.getCounterClockWise() : facing.getClockWise()))) {
                return this.tryConnecting(state, neighborState);
            }
        }
        return blockState;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (state.getValue(SHAPE) == RailingShape.BOTTOM) {
            return switch (state.getValue(FACING)) {
                case NORTH -> RailingBlock.NORTH_SHAPE;
                case EAST -> RailingBlock.EAST_SHAPE;
                case SOUTH -> RailingBlock.SOUTH_SHAPE;
                default -> RailingBlock.WEST_SHAPE;
            };
        } else if (state.getValue(LEFT)) {
            return switch (state.getValue(FACING)) {
                case NORTH -> NORTH_LEFT_SHAPE;
                case EAST -> EAST_LEFT_SHAPE;
                case SOUTH -> SOUTH_LEFT_SHAPE;
                default -> WEST_LEFT_SHAPE;
            };
        }
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_RIGHT_SHAPE;
            case EAST -> EAST_RIGHT_SHAPE;
            case SOUTH -> SOUTH_RIGHT_SHAPE;
            default -> WEST_RIGHT_SHAPE;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return this.getShape(state, world, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEFT, SHAPE);
        super.createBlockStateDefinition(builder);
    }
}
