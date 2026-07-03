package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import io.wifi.starrailexpress.content.block_entity.LockableButtonBlockEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public abstract class LockableButtonBlock extends ButtonBlock
        implements EntityBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty ACTIVE = LightBlockInterface.ACTIVE;
    private final Supplier<BlockEntityType<LockableButtonBlockEntity>> typeSupplier;

    public LockableButtonBlock(Properties settings) {
        this(() -> TMMBlockEntities.LOCKABLE_BUTTON, settings);
    }

    public LockableButtonBlock(Supplier<BlockEntityType<LockableButtonBlockEntity>> typeSupplier, Properties settings) {

        super(BlockSetType.IRON, 20, settings);
        this.registerDefaultState(super.defaultBlockState().setValue(ACTIVE, true).setValue(WATERLOGGED, false));
        this.typeSupplier = typeSupplier;

    }

    public boolean canOpen(Level world, BlockPos blockPos) {
        var state = world.getBlockState(blockPos);
        if (state.getValue(ACTIVE))
            return true;
        return false;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState placementState = super.getStateForPlacement(ctx);
        if (placementState != null) {
            return placementState.setValue(ACTIVE, true);
        }
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE, WATERLOGGED);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && state.getValue(ACTIVE) && getConnectedDirection(state) == direction ? 15 : 0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult blockHitResult) {
        if (state.getValue(POWERED)) {
            return InteractionResult.CONSUME;
        }
        if (!canOpen(world, pos)) {
            world.playSound(player, pos, TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 0.1f, 1f);
            return InteractionResult.FAIL;
        }
        var result = SmallDoorBlock.canOpenDoor((s, w, e, l) -> {
            return InteractionResult.SUCCESS;
        }, state, world, pos, player, null);
        if (result.equals(InteractionResult.PASS)) {
            return InteractionResult.PASS;
        }
        if (result.equals(InteractionResult.SUCCESS) || result.equals(InteractionResult.CONSUME)
                || result.equals(InteractionResult.CONSUME_PARTIAL)) {
            // 成功打开
        } else {
            // 失败
            return InteractionResult.FAIL;
        }
        if (!state.getValue(ACTIVE)) {
            world.playSound(player, pos, TMMSounds.BLOCK_BUTTON_TOGGLE_NO_POWER, SoundSource.BLOCKS, 0.1f, 1f);
        }
        this.press(state, world, pos, player);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    // ======================= 辅助方法 =======================
    @Nullable
    private LockableButtonBlockEntity getBlockEntity(Level world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        return be instanceof LockableButtonBlockEntity ? (LockableButtonBlockEntity) be : null;
    }

    // ======================= 方块实体相关 =======================
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return typeSupplier.get().create(pos, state);
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
            if (blockEntity instanceof LockableButtonBlockEntity) {
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

    // ======================= 放置与生存条件 =======================
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true; // 可放置在任意位置（类似红石火把可悬空）
    }

    @Override
    protected void playSound(@Nullable Player player, LevelAccessor world, BlockPos pos, boolean powered) {
        world.playSound(player, pos, this.getSound(powered), SoundSource.BLOCKS, 0.5f, powered ? 1.0f : 1.5f);
    }

    @Override
    protected SoundEvent getSound(boolean powered) {
        return TMMSounds.BLOCK_SPACE_BUTTON_TOGGLE;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState,
            BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, TMMBlockEntities.LOCKABLE_BUTTON, (l, b, s, t) -> {
                    if (t instanceof LockableButtonBlockEntity d) {
                        LockableButtonBlockEntity.clientTick(l, b, s, d);
                    }
                })
                : createTickerHelper(blockEntityType, TMMBlockEntities.LOCKABLE_BUTTON,
                        (l, b, s, t) -> {
                            if (t instanceof LockableButtonBlockEntity d) {
                                LockableButtonBlockEntity.serverTick(l, b, s, d);
                            }
                        });
    }

    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> blockEntityType, BlockEntityType<E> blockEntityType2,
            BlockEntityTicker<A> blockEntityTicker) {
        return blockEntityType2 == blockEntityType ? blockEntityTicker : null;
    }
}
