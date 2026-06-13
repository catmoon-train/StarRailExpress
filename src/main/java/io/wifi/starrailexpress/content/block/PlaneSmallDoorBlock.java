package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.PlaneSmallDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class PlaneSmallDoorBlock extends SmallDoorBlock {

    public static final int EXPAND_MAX = 32;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    protected static final VoxelShape X_SHAPE = Block.box(7, 0, 0, 9, 16, 16);
    protected static final VoxelShape Z_SHAPE = Block.box(0, 0, 7, 16, 16, 9);
    private final Supplier<BlockEntityType<PlaneSmallDoorBlockEntity>> typeSupplier;

    public PlaneSmallDoorBlock(Supplier<BlockEntityType<PlaneSmallDoorBlockEntity>> typeSupplier, Properties settings) {
        super(settings);
        this.registerDefaultState(
                super.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
        this.typeSupplier = typeSupplier;
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

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState placementState = this.defaultBlockState().setValue(FACING,
                ctx.getHorizontalDirection().getOpposite());
        if (placementState == null) {
            return null;
        }
        BlockPos pos = ctx.getClickedPos();
        Level world = ctx.getLevel();
        return pos.getY() < world.getMaxBuildHeight() - 1 && world.getBlockState(pos.above()).canBeReplaced(ctx)
                ? placementState
                : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        boolean open = state.getValue(OPEN);
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        Direction facing = state.getValue(FACING);

        // 基础关闭形状（与动画初始位置一致）
        VoxelShape baseShape;
        if (facing.getAxis() == Direction.Axis.X) {
            // 门沿 X 轴方向放置（朝北/南），宽度为 2 像素，长度贯穿 16
            baseShape = lower ? Block.box(7, 0, 0, 9, 32, 16) : Block.box(7, 0, 0, 9, 16, 16);
        } else {
            // 门沿 Z 轴方向放置（朝东/西），宽度为 2 像素，长度贯穿 16
            baseShape = lower ? Block.box(0, 0, 7, 16, 32, 9) : Block.box(0, 0, 7, 16, 16, 9);
        }

        if (!open) {
            return baseShape;
        }

        // 打开时，计算最终偏移量（与动画定义中的终点一致）
        // 向外偏移：门的正面方向 (facing) 偏移 7 格
        // 向右偏移：门的右侧方向 (facing 顺时针旋转 90°) 偏移 14 格
        float outX = facing.getStepX() * 7f;
        float outZ = facing.getStepZ() * 7f;
        float rightX = facing.getClockWise().getStepX() * 14f;
        float rightZ = facing.getClockWise().getStepZ() * 14f;
        float dx = outX + rightX;
        float dz = outZ + rightZ;
        var finalShape = baseShape.move(-dx / 16f, 0, dz / 16f);
        VoxelShape bounds = Block.box(0, 0, 0, 16, 32, 16);
        // 取交集，裁剪掉超出部分
        finalShape = Shapes.join(finalShape, bounds, BooleanOp.AND);
        // 平移碰撞箱
        // SRE.LOGGER.info("Shape: {}", finalShape.bounds());

        return finalShape;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? this.typeSupplier.get().create(pos, state) : null;
    }

    @Override
    protected BlockEntityType<? extends DoorBlockEntity> getBlockEntityType() {
        return this.typeSupplier.get();
    }

    @Override
    public void toggleDoor(BlockState state, Level world, SmallDoorBlockEntity entity, BlockPos lowerPos, int ticks) {
        // 当前门（作为主控门）
        entity.toggle(false, ticks);
    }

}
