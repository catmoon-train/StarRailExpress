package org.agmas.noellesroles.block;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.agmas.noellesroles.block_entity.VendingMachinesBlockEntity;
import org.agmas.noellesroles.packet.OpenVendingMachinesScreenS2CPacket;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

public class VendingMachinesBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING;
    private static final MapCodec<VendingMachinesBlock> CODEC;
    protected static final VoxelShape SHAPE;
    // protected static final VoxelShape SHAPE_TOP = Block.box(0, -16, 0, 16, 16, 16);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public VendingMachinesBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                (BlockState) ((BlockState) this.stateDefinition.any()).setValue(FACING, Direction.NORTH).setValue(HALF,
                        DoubleBlockHalf.LOWER));
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // if (state.getValue(HALF).equals(DoubleBlockHalf.UPPER))
        //     return SHAPE_TOP;
        return SHAPE;
    }

    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? super.getTicker(level, state, type) : null;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        // if (blockState.getValue(HALF).equals(DoubleBlockHalf.UPPER))
        //     return SHAPE_TOP;
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level,
            BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        onUseVendingMachines(player, blockPos);
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player,
            BlockHitResult blockHitResult) {
        onUseVendingMachines(player, blockPos);
        return InteractionResult.SUCCESS;
    }

    public static void onUseVendingMachines(Player player, BlockPos blockPos) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.level().getBlockState(blockPos).getValue(HALF).equals(DoubleBlockHalf.UPPER)) {
                blockPos = blockPos.below();
            }
            ServerPlayNetworking.send(serverPlayer, new OpenVendingMachinesScreenS2CPacket(blockPos));
        }
    }

    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState placementState = super.getStateForPlacement(ctx);

        if (placementState == null) {
            return null;
        }
        placementState = placementState.setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        BlockPos pos = ctx.getClickedPos();
        Level world = ctx.getLevel();
        return pos.getY() < world.getMaxBuildHeight() - 1 && world.getBlockState(pos.above()).canBeReplaced(ctx)
                ? placementState
                : null;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { FACING, HALF });
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF).equals(DoubleBlockHalf.LOWER))
            return new VendingMachinesBlockEntity(pos, state);
        return null;
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack itemStack) {
        world.setBlockAndUpdate(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction == half.getDirectionToOther() &&
                (!neighborState.is(this)
                        || neighborState.getValue(FACING) != state.getValue(FACING)
                        || neighborState.getValue(HALF) != half.getOtherHalf())) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    static {
        FACING = BlockStateProperties.HORIZONTAL_FACING;
        SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
        CODEC = simpleCodec(VendingMachinesBlock::new);
    }
}
