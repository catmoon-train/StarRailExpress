package org.agmas.noellesroles.content.block.scene;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.index.DevItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 断桥方块：玩家踩过后短暂延迟即断裂（失去碰撞、不可见），一段时间后自动恢复。
 * 使用原版木板贴图。
 * 红石信号上升沿可触发断裂，并沿相连方块传播（距离≤16）。
 */
public class BreakingBridgeBlock extends SlabBlock implements EntityBlock {

    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered"); // 记录红石状态
    public static final MapCodec<BreakingBridgeBlock> CODEC = simpleCodec(BreakingBridgeBlock::new);

    // 防止形状递归锁（各线程独立）
    protected final ThreadLocal<Boolean> recursionLock = ThreadLocal.withInitial(() -> false);
    // 防止红石传播递归锁（各线程独立）
    private static final ThreadLocal<Boolean> redstonePropagationLock = ThreadLocal.withInitial(() -> false);

    @Override
    public MapCodec<? extends BreakingBridgeBlock> codec() {
        return CODEC;
    }

    // -------- 形状相关方法（不变，仅保留原逻辑）--------
    @Override
    protected VoxelShape getVisualShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos,
            CollisionContext collisionContext) {
        if (recursionLock.get()) {
            return Shapes.empty();
        }
        recursionLock.set(true);
        try {
            if (!blockState.getValue(BROKEN)) {
                BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
                if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                    if (bbbe.displayState != null) {
                        return bbbe.displayState.getVisualShape(blockGetter, blockPos, collisionContext);
                    }
                }
            }
            return Shapes.empty();
        } finally {
            recursionLock.set(false);
        }
    }

    @Override
    protected float getShadeBrightness(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        if (recursionLock.get()) {
            return 1.0F;
        }
        recursionLock.set(true);
        try {
            if (!blockState.getValue(BROKEN)) {
                BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
                if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                    if (bbbe.displayState != null) {
                        return bbbe.displayState.getShadeBrightness(blockGetter, blockPos);
                    }
                }
            }
            return 1.0F;
        } finally {
            recursionLock.set(false);
        }
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        if (recursionLock.get()) {
            return true;
        }
        recursionLock.set(true);
        try {
            if (!blockState.getValue(BROKEN)) {
                BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
                if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                    if (bbbe.displayState != null) {
                        return bbbe.displayState.propagatesSkylightDown(blockGetter, blockPos);
                    }
                }
            }
            return true;
        } finally {
            recursionLock.set(false);
        }
    }

    @Override
    protected boolean skipRendering(BlockState blockState, BlockState blockState2, Direction direction) {
        return blockState2.is(this) ? true : super.skipRendering(blockState, blockState2, direction);
    }

    // -------- 构造与状态定义 --------
    public BreakingBridgeBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(BROKEN, false)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BROKEN, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 放置时默认无红石信号
        return super.getStateForPlacement(context).setValue(POWERED, false);
    }

    // -------- 形状与碰撞（保留原有逻辑，仅微调）--------
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc) {
            if (ecc.getEntity() instanceof Player player) {
                if (ecc.isHoldingItem(DevItems.BREAKING_BRIDGE_TOOL)) {
                    return Shapes.block();
                }
                if (player.isCreative()) {
                    return super.getShape(state, world, pos, context);
                }
            }
        }
        if (recursionLock.get()) {
            return state.getValue(BROKEN)
                    ? (context.isHoldingItem(ModSceneBlocks.BREAKING_BRIDGE.asItem()) ? Shapes.block() : Shapes.empty())
                    : super.getShape(state, world, pos, context);
        }
        recursionLock.set(true);
        try {
            if (!state.getValue(BROKEN)) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                    if (bbbe.displayState != null) {
                        var t = bbbe.displayState.getShape(world, pos, context);
                        if (t != Shapes.empty())
                            return t;
                    }
                }
            }
            return state.getValue(BROKEN)
                    ? (context.isHoldingItem(ModSceneBlocks.BREAKING_BRIDGE.asItem()) ? Shapes.block() : Shapes.empty())
                    : super.getShape(state, world, pos, context);
        } finally {
            recursionLock.set(false);
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos,
            CollisionContext context) {
        if (recursionLock.get()) {
            return blockState.getValue(BROKEN) ? Shapes.empty()
                    : super.getCollisionShape(blockState, blockGetter, blockPos, context);
        }
        recursionLock.set(true);
        try {
            if (!blockState.getValue(BROKEN)) {
                BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
                if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                    if (bbbe.displayState != null) {
                        var t = bbbe.displayState.getCollisionShape(blockGetter, blockPos, context);
                        if (t != Shapes.empty()) {
                            if (t.max(Axis.Y) < 0.375) {
                                var aabb = t.bounds();
                                t = Shapes.box(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, 0.375, aabb.maxZ);
                            }
                            return t;
                        }
                    }
                }
            }
            return blockState.getValue(BROKEN) ? Shapes.empty()
                    : super.getCollisionShape(blockState, blockGetter, blockPos, context);
        } finally {
            recursionLock.set(false);
        }
    }

    // -------- 红石信号处理（新增）--------
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean moved) {
        if (level.isClientSide)
            return;
        // 防止传播递归
        if (redstonePropagationLock.get())
            return;

        boolean hasSignal = level.hasNeighborSignal(pos);
        boolean currentlyPowered = state.getValue(POWERED);

        // 只处理上升沿：无信号 -> 有信号
        if (hasSignal && !currentlyPowered) {
            // 标记当前方块 powered，并启动断裂（传播）
            redstonePropagationLock.set(true);
            try {
                // 用 BFS 传播到所有相连的 BreakingBridgeBlock，距离 ≤ 16
                int maxDist = 16;
                Queue<BlockPos> posQueue = new ArrayDeque<>();
                Queue<Integer> distQueue = new ArrayDeque<>();
                Set<BlockPos> visited = new HashSet<>();

                posQueue.add(pos);
                distQueue.add(0);
                visited.add(pos);

                while (!posQueue.isEmpty()) {
                    BlockPos current = posQueue.poll();
                    int dist = distQueue.poll();

                    BlockState currentState = level.getBlockState(current);
                    if (currentState.is(ModSceneBlocks.BREAKING_BRIDGE)) {
                        // 设置 POWERED 为 true（上升沿标记）
                        level.setBlock(current, currentState.setValue(POWERED, true), Block.UPDATE_CLIENTS);
                        // 触发断裂（如果尚未断裂或正在恢复）
                        if (level instanceof ServerLevel serverLevel) {
                            startBreaking(currentState, serverLevel, current);
                        }
                    }

                    if (dist >= maxDist)
                        continue;

                    for (Direction direction : Direction.values()) {
                        BlockPos neighbor = current.relative(direction);
                        if (visited.contains(neighbor))
                            continue;
                        BlockState neighborState = level.getBlockState(neighbor);
                        if (neighborState.is(ModSceneBlocks.BREAKING_BRIDGE)) {
                            visited.add(neighbor);
                            posQueue.add(neighbor);
                            distQueue.add(dist + 1);
                        }
                    }
                }
            } finally {
                redstonePropagationLock.set(false);
            }
        }
        // 下降沿（有信号 -> 无信号）：仅更新 POWERED 为 false，不触发断裂
        else if (!hasSignal && currentlyPowered) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
        }
        // 其他情况（信号无变化）忽略
    }

    // -------- 原有功能：踩踏断裂、恢复、tick等（保持不变）--------
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && !state.getValue(BROKEN) && entity instanceof Player
                && level instanceof ServerLevel serverLevel
                && !serverLevel.getBlockTicks().hasScheduledTick(pos, this)) {
            startBreaking(state, serverLevel, pos);
        }
        super.stepOn(level, pos, state, entity);
    }

    public void startBreaking(BlockState state, ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BreakingBridgeBlockEntity bbbe) {
            if (bbbe.nowTime < 0)
                bbbe.startBreaking(level, state, pos);
        }
    }

    public void startRecovery(BlockState state, ServerLevel level, BlockPos pos, int time) {
        level.scheduleTick(pos, this, time);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 恢复
        level.setBlock(pos, state.setValue(BROKEN, false), Block.UPDATE_ALL);
        var entity = level.getBlockEntity(pos);
        if (entity instanceof BreakingBridgeBlockEntity bbbe) {
            bbbe.recoveried(level, state, pos, random);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BreakingBridgeBlockEntity) {
                level.removeBlockEntity(pos);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreakingBridgeBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // -------- 物品交互（不变）--------
    @Override
    public ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos,
            Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (!player.isCreative() || itemStack.isEmpty())
            return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
        if (itemStack.is(ModSceneBlocks.BREAKING_BRIDGE.asItem())
                || itemStack.is(ModSceneBlocks.FAKE_BLOCK.asItem()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var entity = level.getBlockEntity(blockPos);
        if (!(entity instanceof BreakingBridgeBlockEntity bbbe))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (bbbe.displayState != null) {
            if (!player.getOffhandItem().is(Items.DEBUG_STICK))
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        if (!itemStack.isEmpty()) {
            var diState = getBlockStateFromItem(player, interactionHand, blockHitResult, itemStack,
                    blockState.getOptionalValue(TYPE));
            if (diState == null) {
                return ItemInteractionResult.FAIL;
            }

            bbbe.setDisplayState(diState);
            player.displayClientMessage(Component.translatable("block.noellesroles.breaking_bridge.set_to",
                    diState.getBlock().getName()), true);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player,
            BlockHitResult blockHitResult) {
        if (player.isCreative()) {
            ItemStack mainhand = player.getMainHandItem();
            if (mainhand.isEmpty()) {
                if (!level.isClientSide) {
                    if (player.isShiftKeyDown()) {
                        BlockEntity entity = level.getBlockEntity(blockPos);
                        if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                            bbbe.addBreakingStage();
                            int b = bbbe.breakingStage;
                            player.displayClientMessage(
                                    Component.translatable("block.noellesroles.breaking_bridge.tip", b),
                                    true);
                            return InteractionResult.SUCCESS;
                        }
                    } else {
                        BlockEntity entity = level.getBlockEntity(blockPos);
                        if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                            player.displayClientMessage(
                                    Component.translatable("block.noellesroles.breaking_bridge.info",
                                            bbbe.displayState == null ? getName()
                                                    : bbbe.displayState.getBlock().getName(),
                                            bbbe.breakingStage, bbbe.breakingTime, bbbe.restoringTime),
                                    true);
                        }
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    // -------- 辅助方法（不变）--------
    public static BlockState getBlockStateFromItem(Player player, InteractionHand interactionHand,
            BlockHitResult blockHitResult, ItemStack stack, Optional<SlabType> slabType) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        try {
            var tstate = blockItem.getBlock()
                    .getStateForPlacement(
                            new BlockPlaceContext(player, interactionHand, stack, blockHitResult));
            if (tstate != null)
                state = tstate;
        } catch (Exception e) {
        }
        if (state == null)
            return Blocks.AIR.defaultBlockState();
        BlockItemStateProperties tag = stack.get(DataComponents.BLOCK_STATE);
        if (tag != null) {
            for (var entry : tag.properties().entrySet()) {
                String key = entry.getKey();
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property != null) {
                    String value = entry.getValue();
                    state = setPropertyValue(state, property, value);
                }
            }
        } else {
            var slabTypeValue = slabType.orElse(null);
            if (state.hasProperty(TYPE)) {
                state = state.setValue(TYPE, slabTypeValue);
            }
            if (state.hasProperty(BlockStateProperties.HALF)) {
                if (slabTypeValue == SlabType.TOP) {
                    state = state.setValue(BlockStateProperties.HALF, Half.TOP);
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property,
            String value) {
        return property.getValue(value)
                .map(val -> state.setValue(property, val))
                .orElse(state);
    }

    @Override
    protected boolean triggerEvent(BlockState blockState, Level level, BlockPos blockPos, int i, int j) {
        super.triggerEvent(blockState, level, blockPos, i, j);
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        return blockEntity == null ? false : blockEntity.triggerEvent(i, j);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState blockState, Level level, BlockPos blockPos) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        return blockEntity instanceof MenuProvider ? (MenuProvider) blockEntity : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState,
            BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, ModSceneBlocks.BREAKING_BRIDGE_ENTITY,
                (l, b, s, t) -> {
                    if (t instanceof BreakingBridgeBlockEntity d) {
                        BreakingBridgeBlockEntity.tick(l, b, s, d);
                    }
                });
    }

    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> blockEntityType, BlockEntityType<E> blockEntityType2,
            BlockEntityTicker<A> blockEntityTicker) {
        return blockEntityType2 == blockEntityType ? blockEntityTicker : null;
    }
}