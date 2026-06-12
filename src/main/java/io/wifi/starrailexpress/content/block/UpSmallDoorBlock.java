package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import java.util.function.Supplier;

public class UpSmallDoorBlock extends SmallDoorBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    protected static final VoxelShape X_SHAPE = Block.box(7, 0, 0, 9, 16, 16);
    protected static final VoxelShape Z_SHAPE = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape[] SHAPES = createShapes();
    private final Supplier<BlockEntityType<UpSmallDoorBlockEntity>> typeSupplier;

    public UpSmallDoorBlock(Supplier<BlockEntityType<UpSmallDoorBlockEntity>> typeSupplier, Properties settings) {
        super(settings);
        this.registerDefaultState(
                super.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
        this.typeSupplier = typeSupplier;
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        VoxelShape lowerXShape = Block.box(7, 0, 0, 9, 32, 16);
        VoxelShape lowerZShape = Block.box(0, 0, 7, 16, 32, 9);
        VoxelShape upperXShape = Block.box(7, 0, 0, 9, 16, 16);
        VoxelShape upperZShape = Block.box(0, 0, 7, 16, 16, 9);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int id = direction.get2DDataValue();
            boolean xAxis = direction.getAxis() == Direction.Axis.X;
            shapes[id] = xAxis ? lowerXShape : lowerZShape;
            shapes[id + 4] = xAxis ? upperXShape : upperZShape;
            Vector3f offset = Direction.UP.step().mul(7).add(0, 16, 0);
            AABB box = new AABB(7, 0, 7, 9, 32, 9).move(offset);
            shapes[id + 8] = Block.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            shapes[id + 12] = Block.box(box.minX, box.minY - 16, box.minZ, box.maxX, box.maxY - 16, box.maxZ);
        }
        return shapes;
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
        BlockState placementState = this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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
            return this.getShape(state);
        }
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        boolean open = state.getValue(OPEN);
        return SHAPES[state.getValue(FACING).get2DDataValue() + (lower ? 0 : 4) + (open ? 8 : 0)];
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? this.typeSupplier.get().create(pos, state) : null;
    }

    @Override
    protected BlockEntityType<? extends DoorBlockEntity> getBlockEntityType() {
        return this.typeSupplier.get();
    }
}
