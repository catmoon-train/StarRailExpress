package io.wifi.starrailexpress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ElevatorButtonBlock extends TMMButtonBlock {

    protected static final VoxelShape NORTH_SHAPE = Block.box(5, 5, 14, 11, 11, 16);
    protected static final VoxelShape NORTH_PRESSED_SHAPE = Block.box(5, 5, 15, 11, 11, 16);
    protected static final VoxelShape EAST_SHAPE = Block.box(0, 5, 5, 2, 11, 11);
    protected static final VoxelShape EAST_PRESSED_SHAPE = Block.box(0, 5, 5, 1, 11, 11);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(5, 5, 0, 11, 11, 2);
    protected static final VoxelShape SOUTH_PRESSED_SHAPE = Block.box(5, 5, 0, 11, 11, 1);
    protected static final VoxelShape WEST_SHAPE = Block.box(14, 5, 5, 16, 11, 11);
    protected static final VoxelShape WEST_PRESSED_SHAPE = Block.box(15, 5, 5, 16, 11, 11);
    protected static final VoxelShape FLOOR_SHAPE = Block.box(5, 0, 5, 11, 2, 11);
    protected static final VoxelShape FLOOR_PRESSED_SHAPE = Block.box(5, 0, 5, 11, 1, 11);
    protected static final VoxelShape CEILING_SHAPE = Block.box(5, 14, 5, 11, 16, 11);
    protected static final VoxelShape CEILING_PRESSED_SHAPE = Block.box(5, 15, 5, 11, 16, 11);

    public ElevatorButtonBlock(Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        boolean pressed = state.getValue(POWERED);
        Direction facing = state.getValue(FACING);
        AttachFace face = state.getValue(FACE);
        return switch (face) {
            case CEILING -> pressed ? CEILING_PRESSED_SHAPE : CEILING_SHAPE;
            case WALL -> switch (facing) {
                case NORTH -> pressed ? PRESSED_NORTH_AABB : NORTH_AABB;
                case EAST -> pressed ? PRESSED_EAST_AABB : EAST_AABB;
                case SOUTH -> pressed ? PRESSED_SOUTH_AABB : SOUTH_AABB;
                default -> pressed ? PRESSED_WEST_AABB : WEST_AABB;
            };
            case FLOOR -> pressed ? FLOOR_PRESSED_SHAPE : FLOOR_SHAPE;
        };
    }
}
