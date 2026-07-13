package org.agmas.noellesroles.content.block.scene;

import java.util.Optional;

import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 断桥方块：玩家踩过后短暂延迟即断裂（失去碰撞、不可见），一段时间后自动恢复。
 * 使用原版木板贴图。
 */
public class BreakingBridgeBlock extends SlabBlock implements EntityBlock {

    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    public static final MapCodec<BreakingBridgeBlock> CODEC = simpleCodec(BreakingBridgeBlock::new);

    public MapCodec<? extends BreakingBridgeBlock> codec() {
        return CODEC;
    }

    public boolean securityLock = false;

    protected VoxelShape getVisualShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos,
            CollisionContext collisionContext) {
        if (securityLock)
            return Shapes.empty();
        securityLock = true;
        if (!blockState.getValue(BROKEN)) {
            BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
            if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                if (bbbe.displayState != null) {
                    var result = bbbe.displayState.getVisualShape(blockGetter, blockPos, collisionContext);
                    securityLock = false;
                    return result;
                }
            }
        }
        securityLock = false;
        return Shapes.empty();
    }

    protected float getShadeBrightness(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {

        if (securityLock)
            return 1F;
        securityLock = true;
        if (!blockState.getValue(BROKEN)) {
            BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
            if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                if (bbbe.displayState != null) {
                    var result = bbbe.displayState.getShadeBrightness(blockGetter, blockPos);
                    securityLock = false;
                    return result;
                }
            }
        }
        securityLock = false;

        return 1.0F;
    }

    protected boolean propagatesSkylightDown(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        if (securityLock)
            return true;
        securityLock = true;
        if (!blockState.getValue(BROKEN)) {
            BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
            if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                if (bbbe.displayState != null) {
                    var result = bbbe.displayState.propagatesSkylightDown(blockGetter, blockPos);
                    securityLock = false;
                    return result;
                }
            }
        }
        securityLock = false;
        return true;
    }

    @Override
    protected boolean skipRendering(BlockState blockState, BlockState blockState2, Direction direction) {
        return blockState2.is(this) ? true : super.skipRendering(blockState, blockState2, direction);
    }

    public BreakingBridgeBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.defaultBlockState().setValue(BROKEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BROKEN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (!state.getValue(BROKEN)) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                if (bbbe.displayState != null)
                    return bbbe.displayState.getShape(world, pos, context);
            }
        }
        return state.getValue(BROKEN)
                ? (context.isHoldingItem(ModSceneBlocks.BREAKING_BRIDGE.asItem()) ? Shapes.block() : Shapes.empty())
                : super.getShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos,
            CollisionContext context) {
        if (!blockState.getValue(BROKEN)) {
            BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
            if (blockEntity instanceof BreakingBridgeBlockEntity bbbe) {
                if (bbbe.displayState != null)
                    return bbbe.displayState.getCollisionShape(blockGetter, blockPos, context);
            }
        }
        return blockState.getValue(BROKEN) ? Shapes.empty()
                : super.getCollisionShape(blockState, blockGetter, blockPos, context);
    }

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
    public void tick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
        {
            // 恢复
            level.setBlock(pos, state.setValue(BROKEN, false), Block.UPDATE_ALL);
            var entity = level.getBlockEntity(pos);
            if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                bbbe.recoveried(level, state, pos, random);
            }
        }

    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 只有当方块类型改变时（即被破坏或替换），才需要清理实体
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BreakingBridgeBlockEntity) {
                // 可选：在移除前执行一些清理逻辑（比如通知配对方块解除绑定）
                // ((RemoteRedstoneBlockEntity) blockEntity).onRemove();

                // 移除方块实体
                level.removeBlockEntity(pos);
            }
            // 调用父类方法，以确保正常执行其他清理（比如更新红石信号等）
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

    @Override
    public ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos,
            Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (!player.isCreative() || itemStack.isEmpty())
            return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        if (itemStack.is(ModSceneBlocks.BREAKING_BRIDGE.asItem())
                || itemStack.is(ModSceneBlocks.FAKE_BLOCK.asItem()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!itemStack.isEmpty()) {
            var diState = getBlockStateFromItem(itemStack, blockState.getOptionalValue(TYPE));
            if (diState == null) {
                return ItemInteractionResult.FAIL;
            }
            var entity = level.getBlockEntity(blockPos);
            if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                bbbe.setDisplayState(diState);
                player.displayClientMessage(Component.translatable("block.noellesroles.breaking_bridge.set_to",
                        diState.getBlock().getName()), true);
            }
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
                        { // 仅在服务端执行
                            BlockEntity entity = level.getBlockEntity(blockPos);
                            if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                                bbbe.addBreakingStage();
                                int b = bbbe.breakingStage;
                                // 发送消息（服务端调用 sendSystemMessage 会发给客户端）
                                player.displayClientMessage(
                                        Component.translatable("block.noellesroles.breaking_bridge.tip", b),
                                        true);
                                return InteractionResult.SUCCESS;
                            }
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

                // 客户端只返回成功，不执行逻辑
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    public static BlockState getBlockStateFromItem(ItemStack stack, Optional<SlabType> slabType) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        // 获取默认状态
        BlockState state = blockItem.getBlock().defaultBlockState();

        // 尝试读取 BlockStateTag
        BlockItemStateProperties tag = stack.get(DataComponents.BLOCK_STATE);
        if (tag != null) {
            for (var entry : tag.properties().entrySet()) {
                // 检查该属性是否存在于当前 BlockState 中
                String key = entry.getKey();
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property != null) {
                    // 获取值的字符串表示
                    String value = entry.getValue();
                    // 尝试解析并设置值
                    state = setPropertyValue(state, property, value);
                }
            }
        } else {
            var slabTypeValue = slabType.orElse(null);
            if (state.hasProperty(TYPE)) {
                state = state.setValue(TYPE, slabTypeValue);
            }
        }

        return state;
    }

    // 辅助方法：将字符串值安全地应用到属性上
    private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property,
            String value) {
        return property.getValue(value)
                .map(val -> state.setValue(property, val))
                .orElse(state); // 解析失败则保持不变
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
