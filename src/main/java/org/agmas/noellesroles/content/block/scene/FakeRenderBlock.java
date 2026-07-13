package org.agmas.noellesroles.content.block.scene;

import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 假方块：只渲染无实体（不发光）
 */
public class FakeRenderBlock extends BreakingBridgeBlock {

    public static final MapCodec<FakeRenderBlock> CODEC = simpleCodec(FakeRenderBlock::new);

    public MapCodec<? extends FakeRenderBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean skipRendering(BlockState blockState, BlockState blockState2, Direction direction) {
        return blockState2.is(this) ? true : super.skipRendering(blockState, blockState2, direction);
    }

    public FakeRenderBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.defaultBlockState().setValue(TYPE, SlabType.DOUBLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc) {
            if (ecc.getEntity() instanceof Player player) {
                if (player.isCreative()) {
                    return Shapes.block();
                }
            }
        }
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
    }

    @Override
    public void startBreaking(BlockState state, ServerLevel level, BlockPos pos) {
    }

    @Override
    public void startRecovery(BlockState state, ServerLevel level, BlockPos pos, int time) {
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
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
        if (itemStack.is(ModSceneBlocks.FAKE_BLOCK.asItem()) || itemStack.is(ModSceneBlocks.BREAKING_BRIDGE.asItem()))
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState blockState,
            BlockEntityType<T> blockEntityType) {
        return null;
    }
}
