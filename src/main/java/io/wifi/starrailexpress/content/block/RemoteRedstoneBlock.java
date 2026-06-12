package io.wifi.starrailexpress.content.block;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.entity.RemoteRedstoneBlockEntity;
import io.wifi.starrailexpress.index.DevItems;
import io.wifi.starrailexpress.index.SREBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RemoteRedstoneBlock extends RedstoneTorchBlock implements EntityBlock, SimpleWaterloggedBlock {
    private static final MapCodec<RedstoneTorchBlock> CODEC = simpleCodec(RemoteRedstoneBlock::new);

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty TRIGGERED = BooleanProperty.create("triggered");
    // 是否需要延迟更新邻居（红石信号输出更新）
    public static final BooleanProperty PENDING_NEIGHBOR_UPDATE = BooleanProperty.create("pending_neighbor_update");
    // 是否需要延迟向绑定方块同步状态
    public static final BooleanProperty PENDING_REMOTE_SYNC = BooleanProperty.create("pending_remote_sync");

    public RemoteRedstoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.defaultBlockState()
                        .setValue(LIT, false)
                        .setValue(WATERLOGGED, false)
                        .setValue(TRIGGERED, false)
                        .setValue(PENDING_NEIGHBOR_UPDATE, false)
                        .setValue(PENDING_REMOTE_SYNC, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, LIT, TRIGGERED, PENDING_NEIGHBOR_UPDATE, PENDING_REMOTE_SYNC);
    }

    @Override
    public MapCodec<? extends RedstoneTorchBlock> codec() {
        return CODEC;
    }

    // ======================= 红石信号输出 =======================
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // 当本地没有红石输入（LIT=false）且被远程触发（TRIGGERED=true）时，输出强信号15
        return !state.getValue(LIT) && state.getValue(TRIGGERED) ? 15 : 0;
    }

    @Override
    protected boolean hasNeighborSignal(Level level, BlockPos pos, BlockState state) {
        // 只检测下方红石信号（可选，根据需求调整）
        return level.hasNeighborSignal(pos.below());
    }

    // ======================= 核心逻辑：邻居变化（本地红石输入） =======================
    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        if (world.isClientSide)
            return;
        // 忽略自身更新
        if (neighborPos.equals(pos))
            return;

        boolean currentlyTriggered = state.getValue(TRIGGERED);
        // 有远程信号，放弃更新
        if (currentlyTriggered)
            return;
        // 检测当前方块是否接收到红石信号
        boolean isPowered = world.hasNeighborSignal(pos);
        boolean currentlyLit = state.getValue(LIT);
        if (isPowered != currentlyLit) {
            // 本地红石状态变化：更新 LIT，并标记需要延迟处理远程同步和邻居更新
            BlockState newState = state.setValue(LIT, isPowered)
                    .setValue(PENDING_REMOTE_SYNC, true)
                    .setValue(PENDING_NEIGHBOR_UPDATE, true);
            world.setBlock(pos, newState, Block.UPDATE_CLIENTS);
            // 调度一个 tick 来执行后续操作（防止链式更新）
            if (!world.getBlockTicks().willTickThisTick(pos, this)) {
                world.scheduleTick(pos, this, 1);
            }
        }
    }

    // ======================= 远程同步触发 =======================
    /**
     * 被远程配对方块调用，更新当前方块的远程触发状态。
     */
    public void onRemoteTrigger(BlockState state, Level world, BlockPos pos, boolean triggered) {
        if (world.isClientSide)
            return;
        if (triggered && state.getValue(LIT)) {
            return;
        }
        boolean currentTriggered = state.getValue(TRIGGERED);
        if (triggered == currentTriggered)
            return;
        // 消失但是附近有红石信号

        BlockState newState = state.setValue(TRIGGERED, triggered)
                .setValue(PENDING_NEIGHBOR_UPDATE, true);
        if (!triggered) {
            newState = newState.setValue(LIT, true);
        }
        world.setBlock(pos, newState, Block.UPDATE_CLIENTS);
        if (!world.getBlockTicks().willTickThisTick(pos, this)) {
            world.scheduleTick(pos, this, 1);
        }
    }

    // ======================= 延迟处理 tick =======================
    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        boolean needRemoteSync = state.getValue(PENDING_REMOTE_SYNC);
        boolean needNeighborUpdate = state.getValue(PENDING_NEIGHBOR_UPDATE);

        BlockState newState = state;

        // 1. 向配对方块同步状态（如果本地 LIT 发生了变化）
        if (needRemoteSync) {
            newState = newState.setValue(PENDING_REMOTE_SYNC, false);
            // 获取绑定目标并同步 TRIGGERED 状态
            RemoteRedstoneBlockEntity be = getBlockEntity(world, pos);
            if (be != null) {
                BlockPos relaPos = be.getTargetBlockPos();
                if (relaPos != null) {
                    BlockPos targetPos = pos.offset(relaPos);
                    if (targetPos != null && world.getBlockEntity(targetPos) instanceof RemoteRedstoneBlockEntity) {
                        BlockState targetState = world.getBlockState(targetPos);
                        if (targetState.getBlock() instanceof RemoteRedstoneBlock targetBlock) {
                            // 目标方块的 TRIGGERED 应该等于本地 LIT 值（非取反）
                            boolean shouldTrigger = newState.getValue(LIT);
                            targetBlock.onRemoteTrigger(targetState, world, targetPos, shouldTrigger);
                        }
                    }
                }
            }
        }

        // 2. 更新邻居（红石信号输出）
        if (needNeighborUpdate) {
            newState = newState.setValue(PENDING_NEIGHBOR_UPDATE, false);
            world.updateNeighborsAt(pos, this);
            world.updateNeighbourForOutputSignal(pos, this);
        }

        // 如果状态有变化，应用新的 BlockState（通常只有标志位清除，但必须 setBlock 才能持久化）
        if (needRemoteSync || needNeighborUpdate) {
            world.setBlock(pos, newState, Block.UPDATE_CLIENTS);
        }
    }

    // ======================= 辅助方法 =======================
    @Nullable
    private RemoteRedstoneBlockEntity getBlockEntity(Level world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        return be instanceof RemoteRedstoneBlockEntity ? (RemoteRedstoneBlockEntity) be : null;
    }

    // ======================= 方块实体相关 =======================
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RemoteRedstoneBlockEntity(pos, state);
    }

    // ======================= 水逻辑 =======================
    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 只有当方块类型改变时（即被破坏或替换），才需要清理实体
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RemoteRedstoneBlockEntity) {
                // 可选：在移除前执行一些清理逻辑（比如通知配对方块解除绑定）
                // ((RemoteRedstoneBlockEntity) blockEntity).onRemove();

                // 移除方块实体
                level.removeBlockEntity(pos);
            }
            // 调用父类方法，以确保正常执行其他清理（比如更新红石信号等）
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState blockState, Level level, BlockPos blockPos) {
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        return blockEntity instanceof MenuProvider ? (MenuProvider) blockEntity : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ======================= 放置与生存条件 =======================
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true; // 可放置在任意位置（类似红石火把可悬空）
    }

    // ======================= 视觉效果 =======================
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.isClientSide || !SRE.canSeeBarrier())
            return;

        if (state.getValue(LIT) || state.getValue(TRIGGERED)) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            double y = pos.getY() + 0.7 + (random.nextDouble() - 0.5) * 0.2;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0, 0, 0);
        }

        BlockParticleOption marker = new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state);
        level.addAlwaysVisibleParticle(marker,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                0, 0, 0);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 手持特定物品时才显示轮廓
        if (context.isHoldingItem(SREBlocks.REMOTE_REDSTONE.asItem()) ||
                context.isHoldingItem(DevItems.BINDING_TOOL) ||
                context.isHoldingItem(Items.REDSTONE) ||
                context.isHoldingItem(Items.DEBUG_STICK)) {
            return Shapes.block();
        }
        return Shapes.empty();
    }

    // ======================= 玩家交互 =======================
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player.getMainHandItem().is(DevItems.BINDING_TOOL)) {
            return InteractionResult.PASS;
        }
        if (player.isCreative()) {
            if (!world.isClientSide) {
                sendTip(player, world, pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static void sendTip(Player player, Level world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof RemoteRedstoneBlockEntity be) {
            BlockPos retarget = (be.getTargetBlockPos());
            BlockPos target = null;
            if (retarget != null) {
                target = pos.offset(retarget);
            }
            MutableComponent msg = Component.translatable("message.block.starrailexpress.remote_redstone.info",
                    target != null ? target.toShortString()
                            : Component.translatable("message.block.starrailexpress.remote_redstone.info.none"));
            player.displayClientMessage(msg.withStyle(ChatFormatting.YELLOW), true);
        }
    }
}