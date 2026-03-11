package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.block.property.OrnamentShape;
import io.wifi.starrailexpress.index.TMMProperties;

import io.wifi.starrailexpress.mixin.block.AbstractBlockInvoker;
import io.wifi.starrailexpress.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class OrnamentBlock extends DirectionalBlock {

    public static final EnumProperty<OrnamentShape> SHAPE = TMMProperties.ORNAMENT_SHAPE;

    public OrnamentBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, OrnamentShape.CENTER));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Direction side = ctx.getClickedFace();
        Level world = ctx.getLevel();
        BlockState state = world.getBlockState(pos);
        Vec2 hit = BlockUtils.get2DHit(ctx.getClickLocation(), pos, side);
        boolean topRight = hit.x + hit.y > 1;
        boolean bottomRight = hit.x - hit.y > 0;
        boolean center = ctx.isSecondaryUseActive();
        OrnamentShape shape = center ? OrnamentShape.CENTER :
                topRight && bottomRight ? OrnamentShape.RIGHT :
                        topRight ? OrnamentShape.TOP :
                                !bottomRight ? OrnamentShape.LEFT : OrnamentShape.BOTTOM;
        if (state.is(this)) {
            OrnamentShape originalShape = state.getValue(SHAPE);
            OrnamentShape newShape = originalShape.with(shape);
            if (originalShape == newShape) return null;
            return state.setValue(SHAPE, newShape);
        }
        return this.defaultBlockState()
                .setValue(FACING, side)
                .setValue(SHAPE, shape);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return (context.getItemInHand().is(this.asItem())) || super.canBeReplaced(state, context);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> GlassPanelBlock.NORTH_COLLISION_SHAPE;
            case EAST -> GlassPanelBlock.EAST_COLLISION_SHAPE;
            case SOUTH -> GlassPanelBlock.SOUTH_COLLISION_SHAPE;
            case WEST -> GlassPanelBlock.WEST_COLLISION_SHAPE;
            case UP -> GlassPanelBlock.UP_COLLISION_SHAPE;
            case DOWN -> GlassPanelBlock.DOWN_COLLISION_SHAPE;
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.isSecondaryUseActive() && !player.getMainHandItem().is(this.asItem())) {
            Direction dir = state.getValue(FACING);
            BlockPos behindBlockPos = pos.subtract(new Vec3i(dir.getStepX(), dir.getStepY(), dir.getStepZ()));
            BlockState blockBehindOrnament = world.getBlockState(behindBlockPos);
            return ((AbstractBlockInvoker) blockBehindOrnament.getBlock()).tmm$invokeOnUseWithItem(stack, blockBehindOrnament, world, behindBlockPos, player, hand, hit.withPosition(behindBlockPos));
        }
        return super.useItemOn(stack, state, world, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive() && !player.getMainHandItem().is(this.asItem())) {
            Direction dir = state.getValue(FACING);
            BlockPos behindBlockPos = pos.subtract(new Vec3i(dir.getStepX(), dir.getStepY(), dir.getStepZ()));
            BlockState blockBehindOrnament = world.getBlockState(behindBlockPos);
                return ((AbstractBlockInvoker) blockBehindOrnament.getBlock()).tmm$invokeOnUse(blockBehindOrnament, world, behindBlockPos, player, hit.withPosition(behindBlockPos));
        }
        return super.useWithoutItem(state, world, pos, player, hit);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }
}
