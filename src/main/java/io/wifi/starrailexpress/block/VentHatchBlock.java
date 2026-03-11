package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class VentHatchBlock extends FaceAttachedHorizontalDirectionalBlock {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final VoxelShape NORTH_SHAPE = Block.box(1, 1, 15, 15, 15, 16);
    private static final VoxelShape EAST_SHAPE = Block.box(0, 1, 1, 1, 15, 15);
    private static final VoxelShape SOUTH_SHAPE = Block.box(1, 1, 0, 15, 15, 1);
    private static final VoxelShape WEST_SHAPE = Block.box(15, 1, 1, 16, 15, 15);
    private static final VoxelShape UP_SHAPE = Block.box(1, 0, 1, 15, 1, 15);
    private static final VoxelShape DOWN_SHAPE = Block.box(1, 15, 1, 15, 16, 15);
    private static final VoxelShape[] OPEN_WALL_SHAPES = {
            Block.box(1, 15, 0, 15, 16, 14),
            Block.box(2, 15, 1, 16, 16, 15),
            Block.box(1, 15, 2, 15, 16, 16),
            Block.box(0, 15, 1, 14, 16, 15)
    };
    private static final VoxelShape[] OPEN_CEILING_SHAPES = {
            Block.box(1, 2, 0, 15, 16, 1),
            Block.box(15, 2, 1, 16, 16, 15),
            Block.box(1, 2, 15, 15, 16, 16),
            Block.box(0, 2, 1, 1, 16, 15)
    };
    private static final VoxelShape[] OPEN_FLOOR_SHAPES = {
            Block.box(1, 0, 15, 15, 14, 16),
            Block.box(0, 0, 1, 1, 14, 15),
            Block.box(1, 0, 0, 15, 14, 1),
            Block.box(15, 0, 1, 16, 14, 15)
    };

    public VentHatchBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        boolean open = state.getValue(OPEN);
        world.setBlockAndUpdate(pos, state.setValue(OPEN, !open));
        SoundEvent sound = open ? SoundEvents.COPPER_TRAPDOOR_CLOSE : SoundEvents.COPPER_TRAPDOOR_OPEN;
        world.playSound(null, pos, sound, SoundSource.BLOCKS, 1f, 1.125f);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            Direction facing = state.getValue(FACING);
            return (switch (state.getValue(FACE)) {
                case CEILING -> OPEN_CEILING_SHAPES;
                case WALL -> OPEN_WALL_SHAPES;
                case FLOOR -> OPEN_FLOOR_SHAPES;
            })[facing.get2DDataValue()];
        }
        return this.getShapeForState(state);
    }

    public VoxelShape getShapeForState(BlockState state) {
        return switch (VentHatchBlock.getConnectedDirection(state)) {
            case DOWN -> DOWN_SHAPE;
            case UP -> UP_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, OPEN);
    }
}
