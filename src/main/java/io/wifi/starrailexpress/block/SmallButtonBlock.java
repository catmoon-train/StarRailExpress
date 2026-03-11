package io.wifi.starrailexpress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SmallButtonBlock extends TMMButtonBlock {

    protected static final VoxelShape NORTH_SHAPE = Block.box(6, 6, 14, 10, 10, 16);
    protected static final VoxelShape NORTH_PRESSED_SHAPE = Block.box(6, 6, 15, 10, 10, 16);
    protected static final VoxelShape EAST_SHAPE = Block.box(0, 6, 6, 2, 10, 10);
    protected static final VoxelShape EAST_PRESSED_SHAPE = Block.box(0, 6, 6, 1, 10, 10);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(6, 6, 0, 10, 10, 2);
    protected static final VoxelShape SOUTH_PRESSED_SHAPE = Block.box(6, 6, 0, 10, 10, 1);
    protected static final VoxelShape WEST_SHAPE = Block.box(14, 6, 6, 16, 10, 10);
    protected static final VoxelShape WEST_PRESSED_SHAPE = Block.box(15, 6, 6, 16, 10, 10);
    protected static final VoxelShape FLOOR_X_SHAPE = Block.box(6, 0, 6, 10, 2, 10);
    protected static final VoxelShape FLOOR_X_PRESSED_SHAPE = Block.box(6, 0, 6, 10, 1, 10);
    protected static final VoxelShape FLOOR_Z_SHAPE = Block.box(6, 0, 6, 10, 2, 10);
    protected static final VoxelShape FLOOR_Z_PRESSED_SHAPE = Block.box(6, 0, 6, 10, 1, 10);
    protected static final VoxelShape CEILING_X_SHAPE = Block.box(6, 14, 6, 10, 16, 10);
    protected static final VoxelShape CEILING_X_PRESSED_SHAPE = Block.box(6, 15, 6, 10, 16, 10);
    protected static final VoxelShape CEILING_Z_SHAPE = Block.box(6, 14, 6, 10, 16, 10);
    protected static final VoxelShape CEILING_Z_PRESSED_SHAPE = Block.box(6, 15, 6, 10, 16, 10);

    public SmallButtonBlock(Properties settings) {
        super(settings);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        boolean pressed = state.getValue(POWERED);
        Direction facing = state.getValue(FACING);
        AttachFace face = state.getValue(FACE);
        boolean xAxis = facing.getAxis() == Direction.Axis.X;
        return switch (face) {
            case CEILING ->
                    xAxis ? (pressed ? PRESSED_CEILING_AABB_X : CEILING_AABB_X) : (pressed ? PRESSED_CEILING_AABB_Z : CEILING_AABB_Z);
            case WALL -> switch (facing) {
                case NORTH -> pressed ? PRESSED_NORTH_AABB : NORTH_AABB;
                case EAST -> pressed ? PRESSED_EAST_AABB : EAST_AABB;
                case SOUTH -> pressed ? PRESSED_SOUTH_AABB : SOUTH_AABB;
                default -> pressed ? PRESSED_WEST_AABB : WEST_AABB;
            };
            case FLOOR ->
                    xAxis ? (pressed ? PRESSED_FLOOR_AABB_X : FLOOR_AABB_X) : (pressed ? PRESSED_FLOOR_AABB_Z : FLOOR_AABB_Z);
        };
    }
}
