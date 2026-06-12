package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.UpSmallDoorBlockEntity;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class UpSmallDoorBlock extends SmallDoorBlock {

    public static final int EXPAND_MAX = 32;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    protected static final VoxelShape X_SHAPE = Block.box(7, 0, 0, 9, 16, 16);
    protected static final VoxelShape Z_SHAPE = Block.box(0, 0, 7, 16, 16, 9);
    private final Supplier<BlockEntityType<UpSmallDoorBlockEntity>> typeSupplier;

    public UpSmallDoorBlock(Supplier<BlockEntityType<UpSmallDoorBlockEntity>> typeSupplier, Properties settings) {
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
        if (context.equals(CollisionContext.empty())) {
            return super.getShape(state);
        }
        VoxelShape lowerXShape = Block.box(7, 0, 0, 9, 32, 16);
        VoxelShape lowerZShape = Block.box(0, 0, 7, 16, 32, 9);
        VoxelShape upperXShape = Block.box(7, 0, 0, 9, 16, 16);
        VoxelShape upperZShape = Block.box(0, 0, 7, 16, 16, 9);

        VoxelShape openXShape = Block.box(7, 14, 0, 9, 16, 16);
        VoxelShape openZShape = Block.box(0, 14, 7, 16, 16, 9);
        VoxelShape baseShape;
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (lower) {
            baseShape = state.getValue(FACING).getAxis() == Direction.Axis.X ? lowerXShape : lowerZShape;
        } else {
            baseShape = state.getValue(FACING).getAxis() == Direction.Axis.X ? upperXShape : upperZShape;
        }

        boolean open = state.getValue(OPEN);
        if (open) {
            if (lower) {
                return Block.box(0, 0, 0, 0, 0, 0);
            }
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? openXShape : openZShape;
        }
        return baseShape;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? this.typeSupplier.get().create(pos, state) : null;
    }

    @Override
    protected BlockEntityType<? extends DoorBlockEntity> getBlockEntityType() {
        return this.typeSupplier.get();
    }

    public void toggleDoor(BlockState state, Level world, SmallDoorBlockEntity entity, BlockPos lowerPos, int ticks) {
        // 先触发当前门（作为主控门）
        entity.toggle(false, ticks);

        Direction facing = state.getValue(FACING);
        // 门的侧面方向（垂直于朝向，即门排列的方向）
        Direction sideDir1 = facing.getCounterClockWise();
        Direction sideDir2 = facing.getClockWise();

        // 收集需要联动的所有门的位置（包括当前门的位置，后续会排除主门）
        Set<BlockPos> toggledPositions = new HashSet<>();
        toggledPositions.add(lowerPos);

        // 向两个侧面方向递归探索
        collectConnectedDoors(world, lowerPos, sideDir1, facing.getAxis(), toggledPositions, state.getValue(OPEN),
                state.getBlock(),
                EXPAND_MAX);
        collectConnectedDoors(world, lowerPos, sideDir2, facing.getAxis(), toggledPositions, state.getValue(OPEN),
                state.getBlock(),
                EXPAND_MAX);

        // 对除主门以外的所有门执行联动开关（传入 true 表示从属联动）
        for (BlockPos pos : toggledPositions) {
            if (pos.equals(lowerPos))
                continue;
            BlockState neighborState = world.getBlockState(pos);
            if (neighborState.getBlock() instanceof SmallDoorBlock
                    && world.getBlockEntity(pos) instanceof SmallDoorBlockEntity neighborEntity) {
                neighborEntity.toggle(true, ticks);
            }
        }
    }

    /**
     * 递归收集沿着指定侧面方向连续的所有同类型门（下半部分）。
     *
     * @param world        世界
     * @param startPos     起始门的下半部分位置
     * @param direction    侧面探索方向（向左或向右）
     * @param requiredAxis 门朝向需要满足的轴（与主门轴相同，允许相反）
     * @param collected    收集结果集合
     * @param open         开门状态
     * @param block        方块Class
     * @param max          最大连锁
     */
    private void collectConnectedDoors(Level world, BlockPos startPos, Direction direction, Direction.Axis requiredAxis,
            Set<BlockPos> collected, boolean open, Block block, int max) {
        BlockPos currentPos = startPos.relative(direction);
        int count = 0;
        while (count <= max) {
            BlockState state = world.getBlockState(currentPos);
            // 必须是 SmallDoorBlock 的下半部分，且 FACING 的轴与 requiredAxis 相同，且必须开门状态相同
            if (!(state.is(block))
                    || state.getValue(HALF) != DoubleBlockHalf.LOWER
                    || state.getValue(OPEN) != open
                    || state.getValue(FACING).getAxis() != requiredAxis) {
                break;
            }
            // 加入集合
            collected.add(currentPos);
            // 继续向相同方向前进
            currentPos = currentPos.relative(direction);
            count++;
        }
    }
}
