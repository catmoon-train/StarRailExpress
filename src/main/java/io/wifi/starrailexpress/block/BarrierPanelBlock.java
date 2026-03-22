package io.wifi.starrailexpress.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.wifi.starrailexpress.util.BarrierViewer;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

public class BarrierPanelBlock extends PanelBlock {
    private static final VoxelShape UP_SHAPE = box(0.0, 15.9, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape DOWN_SHAPE = box(0.0, 0.0, 0.0, 16.0, 0.1, 16.0);
    private static final VoxelShape EAST_SHAPE = box(0.0, 0.0, 0.0, 0.1, 16.0, 16.0);
    private static final VoxelShape WEST_SHAPE = box(15.9, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = box(0.0, 0.0, 0.0, 16.0, 16.0, 0.1);
    private static final VoxelShape NORTH_SHAPE = box(0.0, 0.0, 15.9, 16.0, 16.0, 16.0);
    private static final Map<Direction, VoxelShape> SHAPES_FOR_DIRECTIONS = Util.make(Maps.newEnumMap(Direction.class), shapes -> {
        shapes.put(Direction.NORTH, SOUTH_SHAPE);
        shapes.put(Direction.EAST, WEST_SHAPE);
        shapes.put(Direction.SOUTH, NORTH_SHAPE);
        shapes.put(Direction.WEST, EAST_SHAPE);
        shapes.put(Direction.UP, UP_SHAPE);
        shapes.put(Direction.DOWN, DOWN_SHAPE);
    });
    private final ImmutableMap<BlockState, VoxelShape> SHAPES;

    public BarrierPanelBlock(Properties settings) {
        super(settings);
        this.SHAPES = this.getShapeForEachState(BarrierPanelBlock::calculateMultifaceShape);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return this.SHAPES.get(state);
    }

    private static VoxelShape calculateMultifaceShape(BlockState state) {
        VoxelShape voxelShape = Shapes.empty();
        for (Direction direction : DIRECTIONS) {
            if (hasFace(state, direction))
                voxelShape = Shapes.or(voxelShape, SHAPES_FOR_DIRECTIONS.get(direction));
        }
        return voxelShape.isEmpty() ? Shapes.block() : voxelShape;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        if (BarrierViewer.isBarrierVisible()) return RenderShape.MODEL;
        return RenderShape.INVISIBLE;
    }
}
