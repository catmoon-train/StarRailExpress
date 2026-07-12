package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block_entity.ZiplineBlockEntity;
import io.wifi.starrailexpress.content.entity.ZiplineRiderEntity;
import io.wifi.starrailexpress.content.item.BindingToolItem;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.shapes.EntityCollisionContext;
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
    /** 玩家放置时自动连线的直线扫描距离 */
    private static final int MAX_ZIPLINE_RANGE = 25;
    /** 绑定工具手动连线的最大距离，可跨高度、可斜拉 */
    public static final int MAX_LINK_DISTANCE = 64;
    /** 上滑索消耗的体力比例（基于体力上限的百分比） */
    private static final float RIDE_STAMINA_RATIO = 0.4f;
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

    /**
     * 自动连线只在玩家手动放置时进行。
     * onPlace 会被 /setblock、整区复制（BlockCopyUtils）等批量写方块的路径触发，
     * 在那里扫描会让复制区的柱子连到游戏区的柱子上；复制出来的连接由方块实体 NBT 里的相对偏移负责。
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                           ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            rescanRoute(level, pos);
            rescanNearbyRoutes(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ZiplineBlockEntity zbe) {
                for (BlockPos connected : zbe.getConnectedPositions()) {
                    if (level.getBlockEntity(connected) instanceof ZiplineBlockEntity otherZbe) {
                        otherZbe.removeConnection(pos);
                        updateVisualConnections(level, connected, otherZbe.getConnectedPositions());
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 重新扫描本柱子的自动连线。手动（绑定工具）拉出的斜线/跨层连接不会被回收。
     */
    private static void rescanRoute(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ZiplineBlockEntity zbe)) {
            return;
        }

        Set<BlockPos> scanned = scanConnections(level, pos);
        for (BlockPos old : zbe.getConnectedPositions()) {
            if (isAutoScannable(pos, old) && !scanned.contains(old)) {
                unlink(level, pos, old);
            }
        }
        for (BlockPos connected : scanned) {
            link(level, pos, connected);
        }
    }

    /**
     * 在两根柱子中间插入新柱子时，让两侧的旧柱子改连到新柱子上。
     */
    private static void rescanNearbyRoutes(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int i = 1; i <= MAX_ZIPLINE_RANGE; i++) {
                BlockPos checkPos = pos.relative(dir, i);
                if (level.getBlockState(checkPos).getBlock() instanceof ZiplineBlock) {
                    rescanRoute(level, checkPos);
                    break;
                }
            }
        }
    }

    /** 同层、且正好落在某个水平方向上的连接，才是自动扫描能生成/回收的 */
    private static boolean isAutoScannable(BlockPos from, BlockPos to) {
        if (from.getY() != to.getY()) {
            return false;
        }
        return (from.getX() == to.getX()) != (from.getZ() == to.getZ());
    }

    private static Set<BlockPos> scanConnections(Level level, BlockPos pos) {
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

    /** 双向连接两根柱子。返回 false 表示其中一端不是滑索柱。 */
    public static boolean link(Level level, BlockPos from, BlockPos to) {
        if (from.equals(to)) {
            return false;
        }
        if (!(level.getBlockEntity(from) instanceof ZiplineBlockEntity fromZbe)
                || !(level.getBlockEntity(to) instanceof ZiplineBlockEntity toZbe)) {
            return false;
        }
        fromZbe.addConnection(to);
        toZbe.addConnection(from);
        updateVisualConnections(level, from, fromZbe.getConnectedPositions());
        updateVisualConnections(level, to, toZbe.getConnectedPositions());
        return true;
    }

    /** 双向断开两根柱子 */
    public static void unlink(Level level, BlockPos from, BlockPos to) {
        if (level.getBlockEntity(from) instanceof ZiplineBlockEntity fromZbe) {
            fromZbe.removeConnection(to);
            updateVisualConnections(level, from, fromZbe.getConnectedPositions());
        }
        if (level.getBlockEntity(to) instanceof ZiplineBlockEntity toZbe) {
            toZbe.removeConnection(from);
            updateVisualConnections(level, to, toZbe.getConnectedPositions());
        }
    }

    /** 断开某根柱子的全部连接 */
    public static void unlinkAll(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ZiplineBlockEntity zbe)) {
            return;
        }
        for (BlockPos connected : zbe.getConnectedPositions()) {
            unlink(level, pos, connected);
        }
    }

    /**
     * 丢掉指向非滑索方块的连接。未加载的区块会被跳过，避免误删跨区块的长连接。
     */
    private static void pruneDeadConnections(Level level, BlockPos pos, ZiplineBlockEntity zbe) {
        for (BlockPos connected : zbe.getConnectedPositions()) {
            if (level.hasChunkAt(connected) && !(level.getBlockState(connected).getBlock() instanceof ZiplineBlock)) {
                unlink(level, pos, connected);
            }
        }
    }

    private static void updateVisualConnections(Level level, BlockPos pos, Set<BlockPos> connections) {
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

    private static BlockState applyVisualConnections(BlockState state, BlockPos pos, Set<BlockPos> connections) {
        boolean north = false;
        boolean east = false;
        boolean south = false;
        boolean west = false;
        for (BlockPos connected : connections) {
            int dx = connected.getX() - pos.getX();
            int dz = connected.getZ() - pos.getZ();
            if (dx == 0 && dz == 0) continue;
            // 斜拉的连接取水平分量更大的那一侧出杆
            if (Math.abs(dx) >= Math.abs(dz)) {
                if (dx > 0) east = true;
                else west = true;
            } else {
                if (dz > 0) south = true;
                else north = true;
            }
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

    /**
     * 滑索上的玩家从柱子里穿过去，不然经过途中的柱子时会被柱头顶住。
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() != null
                && entityContext.getEntity().getVehicle() instanceof ZiplineRiderEntity) {
            return Shapes.empty();
        }
        return getShape(state, level, pos, context);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // 让方块本身放行：滑索方块交给放置逻辑，绑定工具交给 BindingToolItem.useOn
        if (isHoldingZiplineBlock(stack) || stack.getItem() instanceof BindingToolItem) {
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
            // 客户端镜像服务端的体力扣除：sprintingTicks 两端各自独立计算且没有同步包，
            // 只在服务端扣会让客户端 HUD 体力条纹丝不动
            if (level.getBlockEntity(pos) instanceof ZiplineBlockEntity clientZbe
                    && !clientZbe.getConnectedPositions().isEmpty()
                    && !tryConsumeRideStamina(level, player)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.sidedSuccess(true);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ZiplineBlockEntity zbe)) {
            return InteractionResult.PASS;
        }

        pruneDeadConnections(level, pos, zbe);
        Set<BlockPos> connections = zbe.getConnectedPositions();
        if (connections.isEmpty()) {
            return InteractionResult.PASS;
        }

        BlockPos targetPos = selectTarget(connections, pos, player);

        if (targetPos == null) {
            return InteractionResult.PASS;
        }

        if (!tryConsumeRideStamina(level, player)) {
            player.displayClientMessage(
                    Component.translatable("message.starrailexpress.zipline.not_enough_stamina")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.FAIL;
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
        player.startRiding(rider, true);

        return InteractionResult.SUCCESS;
    }

    /**
     * 游戏运行中上滑索消耗当前体力的 40%，体力不足返回 false（不扣）。
     * 判定口径与 PlayerEntityMixin.tmm$limitSprint / StaminaProvider 保持一致：
     * 未开局、旁观/死亡、无限体力效果、职业不受体力限制（maxSprintTime 为负或 MAX_VALUE）都直接放行。
     */
    private static boolean tryConsumeRideStamina(Level level, Player player) {
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(level);
        if (gameComponent == null || !gameComponent.isRunning()) {
            return true;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return true;
        }
        if (org.agmas.noellesroles.init.ModEffects.hasInfiniteStamina(player)) {
            return true;
        }
        SRERole role = gameComponent.getRole(player);
        if (role == null) {
            return true;
        }
        int maxSprintTime = role.getMaxSprintTime(player);
        if (maxSprintTime < 0 || maxSprintTime == Integer.MAX_VALUE) {
            return true;
        }
        if (!(player instanceof PlayerStaminaGetter stamina)) {
            return true;
        }
        float max = maxSprintTime * org.agmas.noellesroles.init.ModEffects.getStaminaCapacityMultiplier(player);
        float current = stamina.starrailexpress$getStamina();
        if (current < 0) {
            current = max; // -1 = 尚未初始化，视为满
        }
        current = Math.min(current, max);
        // 最低体力要求：必须拥有基于体力上限 40% 的体力才允许上滑索
        float require = max * RIDE_STAMINA_RATIO;
        if (current < require) {
            return false;
        }
        // 消耗基于体力上限 40% 的体力，最低降至 0
        float cost = require;
        stamina.starrailexpress$setStamina(Math.max(0f, current - cost));
        return true;
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
