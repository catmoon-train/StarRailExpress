package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.entity.RemoteRedstoneBlockEntity;
import io.wifi.starrailexpress.index.DevItems;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.Property;
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

    protected int getSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
        return !blockState.getValue(LIT) && blockState.getValue(TRIGGERED) ? 15 : 0;
    }

    @Override
    protected BlockState updateShape(BlockState blockState, Direction direction, BlockState blockState2,
            LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2) {
        if ((Boolean) blockState.getValue(WATERLOGGED)) {
            levelAccessor.scheduleTick(blockPos, Fluids.WATER, Fluids.WATER.getTickDelay(levelAccessor));
        }

        return super.updateShape(blockState, direction, blockState2, levelAccessor, blockPos, blockPos2);
    }

    public RemoteRedstoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                (BlockState) this.defaultBlockState().setValue(LIT, false).setValue(WATERLOGGED, false)
                        .setValue(TRIGGERED, false));
    }

    protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
        return true;
    }

    @Override
    protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
    }

    @Override
    public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
        if (level.isClientSide && SRE.canSeeBarrier()) {
            if ((blockState.getValue(LIT) || blockState.getValue(TRIGGERED))) {
                double d = (double) blockPos.getX() + (double) 0.5F + (randomSource.nextDouble() - (double) 0.5F) * 0.2;
                double e = (double) blockPos.getY() + 0.7 + (randomSource.nextDouble() - (double) 0.5F) * 0.2;
                double f = (double) blockPos.getZ() + (double) 0.5F + (randomSource.nextDouble() - (double) 0.5F) * 0.2;
                level.addParticle(DustParticleOptions.REDSTONE, d, e, f, (double) 0.0F, (double) 0.0F, (double) 0.0F);
            } else {
                BlockParticleOption particleEffect = new BlockParticleOption(ParticleTypes.BLOCK, blockState);
                level.addAlwaysVisibleParticle(particleEffect, blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                        (double) 0.0F,
                        (double) 0.0F, (double) 0.0F);
            }
        }
    }

    @Override
    protected boolean hasNeighborSignal(Level level, BlockPos blockPos, BlockState blockState) {
        return level.hasNeighborSignal(blockPos.below());
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos,
            boolean notify) {
        if (!world.isClientSide) {
            boolean isPowered = world.hasNeighborSignal(pos);
            if (isPowered != state.getValue(LIT) && !state.getValue(TRIGGERED)) {
                state = state.setValue(LIT, isPowered);
                world.setBlock(pos, state, 2);
                tryTriggerRemote(state, world, pos, block, fromPos, isPowered);
            }
        }
    }

    public RemoteRedstoneBlockEntity getBlockEntity(Level world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof RemoteRedstoneBlockEntity r)
            return r;
        return null;
    }

    public void tryTriggerRemote(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos,
            boolean isPowered) {
        RemoteRedstoneBlockEntity entity = getBlockEntity(world, pos);
        if (entity != null) {
            var targetPos = entity.getTargetBlockPos();
            if (targetPos != null) {
                BlockState targetState = world.getBlockState(targetPos);
                if (targetState.getBlock() instanceof RemoteRedstoneBlock rrb) {
                    rrb.onTriggered(state, world, pos, isPowered);
                }
            }
        }
    }

    public void onTriggered(BlockState state, Level world, BlockPos pos, boolean isPowered) {
        if (isPowered && state.getValue(LIT)) {
            return;
        }
        world.setBlock(pos, state.setValue(TRIGGERED, isPowered), Block.UPDATE_ALL);
        world.updateNeighborsAt(pos, this); // 更新周围方块（红石粉、机器等）
        // 对于比较器侧面检测，可以额外更新比较器
        world.updateNeighbourForOutputSignal(pos, this);
    }

    @Override
    protected FluidState getFluidState(BlockState blockState) {
        return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RemoteRedstoneBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { WATERLOGGED, LIT, TRIGGERED });
    }

    @Override
    protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos,
            CollisionContext collisionContext) {
        return collisionContext.isHoldingItem(TMMBlocks.REMOTE_REDSTONE.asItem())
                || collisionContext.isHoldingItem(DevItems.BINDING_TOOL)
                || collisionContext.isHoldingItem(Items.REDSTONE)
                || collisionContext.isHoldingItem(Items.DEBUG_STICK) ? Shapes.block() : Shapes.empty();
    }

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
        if (world.getBlockEntity(pos) instanceof RemoteRedstoneBlockEntity cbe) {
            if (cbe.getTargetBlockPos() == null) {
                player.displayClientMessage(
                        Component
                                .translatable("message.block.starrailexpress.remote_redstone.info",
                                        Component.translatable(
                                                "message.block.starrailexpress.remote_redstone.info.none"))
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            } else {

                player.displayClientMessage(
                        Component
                                .translatable("message.block.starrailexpress.remote_redstone.info",
                                        cbe.getTargetBlockPos().toShortString())
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            }
        }
    }

    protected RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public MapCodec<? extends RedstoneTorchBlock> codec() {
        return CODEC;
    }
}
