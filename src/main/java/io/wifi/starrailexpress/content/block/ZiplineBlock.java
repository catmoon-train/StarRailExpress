package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.ZiplineBlockEntity;
import io.wifi.starrailexpress.content.entity.ZiplineRiderEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class ZiplineBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final int MAX_ZIPLINE_RANGE = 25;
    private static final double ROPE_HEIGHT = 0.40;
    private static final VoxelShape CENTER_SHAPE = Block.box(6.5, 5.5, 6.5, 9.5, 8.5, 9.5);
    private static final VoxelShape NORTH_SHAPE = Block.box(6.5, 5.5, 0.0, 9.5, 8.5, 8.0);
    private static final VoxelShape EAST_SHAPE = Block.box(8.0, 5.5, 6.5, 16.0, 8.5, 9.5);
    private static final VoxelShape SOUTH_SHAPE = Block.box(6.5, 5.5, 8.0, 9.5, 8.5, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0, 5.5, 6.5, 8.0, 8.5, 9.5);

    public ZiplineBlock() {
        super(Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.WOOD)
                .mapColor(MapColor.COLOR_BROWN)
                .noOcclusion()
                .forceSolidOn());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NORTH, EAST, SOUTH, WEST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return applyNearbyVisualConnections(ctx.getLevel(), ctx.getClickedPos(),
                this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            refreshRoute(level, pos);
            refreshNearbyRoutes(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ZiplineBlockEntity zbe) {
                for (BlockPos connected : zbe.getConnectedPositions()) {
                    BlockEntity otherBe = level.getBlockEntity(connected);
                    if (otherBe instanceof ZiplineBlockEntity otherZbe) {
                        otherZbe.removeConnection(pos);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            refreshNearbyRoutes(level, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean notify) {
        super.neighborChanged(state, level, pos, block, fromPos, notify);
        if (!level.isClientSide) {
            refreshRoute(level, pos);
        }
    }

    private void refreshRoute(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ZiplineBlockEntity zbe)) {
            return;
        }

        Set<BlockPos> oldConnections = new HashSet<>(zbe.getConnectedPositions());
        Set<BlockPos> newConnections = scanConnections(level, pos);

        zbe.clearConnections();
        for (BlockPos connected : newConnections) {
            zbe.addConnection(connected);
        }

        for (BlockPos old : oldConnections) {
            if (!newConnections.contains(old) && level.getBlockEntity(old) instanceof ZiplineBlockEntity otherZbe) {
                otherZbe.removeConnection(pos);
                updateVisualConnections(level, old, otherZbe.getConnectedPositions());
            }
        }

        for (BlockPos connected : newConnections) {
            if (level.getBlockEntity(connected) instanceof ZiplineBlockEntity otherZbe) {
                otherZbe.addConnection(pos);
                updateVisualConnections(level, connected, otherZbe.getConnectedPositions());
            }
        }

        updateVisualConnections(level, pos, newConnections);
    }

    private void refreshNearbyRoutes(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= MAX_ZIPLINE_RANGE; i++) {
                BlockPos checkPos = pos.relative(dir, i);
                if (level.getBlockState(checkPos).getBlock() instanceof ZiplineBlock) {
                    refreshRoute(level, checkPos);
                    break;
                }
            }
        }
    }

    private Set<BlockPos> scanConnections(Level level, BlockPos pos) {
        Set<BlockPos> connections = new HashSet<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= MAX_ZIPLINE_RANGE; i++) {
                BlockPos checkPos = pos.relative(dir, i);
                BlockState checkState = level.getBlockState(checkPos);
                if (checkState.getBlock() instanceof ZiplineBlock) {
                    connections.add(checkPos);
                    break;
                }
            }
        }
        return connections;
    }

    private void updateVisualConnections(Level level, BlockPos pos, Set<BlockPos> connections) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ZiplineBlock)) {
            return;
        }
        BlockState updated = applyVisualConnections(state, pos, connections);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private BlockState applyNearbyVisualConnections(BlockGetter level, BlockPos pos, BlockState state) {
        return state
                .setValue(NORTH, level.getBlockState(pos.north()).getBlock() instanceof ZiplineBlock)
                .setValue(EAST, level.getBlockState(pos.east()).getBlock() instanceof ZiplineBlock)
                .setValue(SOUTH, level.getBlockState(pos.south()).getBlock() instanceof ZiplineBlock)
                .setValue(WEST, level.getBlockState(pos.west()).getBlock() instanceof ZiplineBlock);
    }

    private BlockState applyVisualConnections(BlockState state, BlockPos pos, Set<BlockPos> connections) {
        boolean north = false;
        boolean east = false;
        boolean south = false;
        boolean west = false;
        for (BlockPos connected : connections) {
            int dx = Integer.compare(connected.getX() - pos.getX(), 0);
            int dz = Integer.compare(connected.getZ() - pos.getZ(), 0);
            if (dx == 0 && dz < 0) north = true;
            if (dx > 0 && dz == 0) east = true;
            if (dx == 0 && dz > 0) south = true;
            if (dx < 0 && dz == 0) west = true;
        }
        return state.setValue(NORTH, north).setValue(EAST, east).setValue(SOUTH, south).setValue(WEST, west);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CENTER_SHAPE;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, NORTH_SHAPE);
        if (state.getValue(EAST)) shape = Shapes.or(shape, EAST_SHAPE);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, SOUTH_SHAPE);
        if (state.getValue(WEST)) shape = Shapes.or(shape, WEST_SHAPE);
        return shape;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (isHoldingZiplineBlock(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        InteractionResult result = tryStartZipline(state, level, pos, player);
        return result.consumesAction()
                ? ItemInteractionResult.CONSUME
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        return tryStartZipline(state, level, pos, player);
    }

    private InteractionResult tryStartZipline(BlockState state, Level level, BlockPos pos, Player player) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ZiplineBlockEntity zbe)) {
            return InteractionResult.PASS;
        }

        refreshRoute(level, pos);
        Set<BlockPos> connections = zbe.getConnectedPositions();
        if (connections.isEmpty()) {
            return InteractionResult.PASS;
        }

        BlockPos targetPos = selectTarget(connections, pos, player);

        if (targetPos == null) {
            return InteractionResult.PASS;
        }

        ZiplineRiderEntity rider = TMMEntities.ZIPLINE_RIDER.create(level);
        if (rider == null) {
            return InteractionResult.PASS;
        }

        if (player.isPassenger()) {
            player.stopRiding();
        }
        rider.setStartAndEnd(pos, targetPos, player);
        Vec3 startPos = ropePoint(pos, targetPos, 0.0f);
        rider.setPos(startPos);
        level.addFreshEntity(rider);

        return InteractionResult.SUCCESS;
    }

    private boolean isHoldingZiplineBlock(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ZiplineBlock;
    }

    /**
     * 从连接列表中选择最合适的滑索目标
     */
    @Nullable
    private BlockPos selectTarget(Set<BlockPos> connections, BlockPos from, Player player) {
        if (connections.isEmpty()) return null;
        if (connections.size() == 1) return connections.iterator().next();

        Vec3 lookVec = player.getLookAngle();
        Vec3 fromCenter = Vec3.atCenterOf(from);

        // 找与玩家视线方向最接近的连接点
        BlockPos best = null;
        double bestDot = -2.0;
        for (BlockPos conn : connections) {
            Vec3 toConn = Vec3.atCenterOf(conn).subtract(fromCenter).normalize();
            double dot = lookVec.dot(toConn);
            if (dot > bestDot) {
                bestDot = dot;
                best = conn;
            }
        }
        return best != null ? best : connections.iterator().next();
    }

    public static Vec3 ropePoint(BlockPos start, BlockPos end, float progress) {
        Vec3 from = Vec3.atCenterOf(start).add(0, ROPE_HEIGHT, 0);
        Vec3 to = Vec3.atCenterOf(end).add(0, ROPE_HEIGHT, 0);
        double distance = from.distanceTo(to);
        Vec3 mid = from.add(to).scale(0.5).add(0, -0.18 * distance, 0);
        double oneMinusT = 1.0 - progress;
        return from.scale(oneMinusT * oneMinusT)
                .add(mid.scale(2.0 * oneMinusT * progress))
                .add(to.scale(progress * progress));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZiplineBlockEntity(TMMBlockEntities.ZIPLINE, pos, state);
    }
}
