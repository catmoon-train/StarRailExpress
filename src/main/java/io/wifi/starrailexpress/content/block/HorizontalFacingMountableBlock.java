package io.wifi.starrailexpress.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public abstract class HorizontalFacingMountableBlock extends MountableBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final EnumProperty<PartType> PART = EnumProperty.create("part", PartType.class);

    public HorizontalFacingMountableBlock(Properties settings) {
        super(settings);
    }

    public final BlockState getDefaultBlockState() {
        return defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(PART, PartType.CENTER);
    }

    public void setPlacedByMirrored(Level level, BlockPos blockPos, BlockState blockState,
            @Nullable LivingEntity livingEntity,
            ItemStack itemStack) {
        super.setPlacedBy(level, blockPos, blockState, livingEntity, itemStack);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos blockPos, BlockState blockState,
            @Nullable LivingEntity livingEntity,
            ItemStack itemStack) {
        this.setPlacedByMirrored(level, blockPos, blockState, livingEntity, itemStack);
    }
     @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos,
            boolean notify) {
                
            }

    public BlockState playerWillDestroyMirrored(Level level, BlockPos blockPos, BlockState blockState, Player player) {
        return super.playerWillDestroy(level, blockPos, blockState, player);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos blockPos, BlockState blockState, Player player) {
        return this.playerWillDestroyMirrored(level, blockPos, blockState, player);
    }

    @Override
    protected BlockState updateShape(BlockState blockState, Direction direction, BlockState blockState2,
            LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2) {
        return this.updateShapeMirrored(blockState, direction, blockState2, levelAccessor, blockPos, blockPos2);
    }

    protected BlockState updateShapeMirrored(BlockState blockState, Direction direction, BlockState blockState2,
            LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2) {
        return super.updateShape(blockState, direction, blockState2, levelAccessor, blockPos, blockPos2);
    }

    @Override
    public Vec3 getSitPos(Level world, BlockState state, BlockPos pos) {
        Vec3 sitPos = this.getNorthFacingSitPos(world, state, pos);
        return switch (state.getValue(FACING)) {
            case EAST -> new Vec3(sitPos.z, sitPos.y, 1 - sitPos.x);
            case SOUTH -> new Vec3(1 - sitPos.x, sitPos.y, 1 - sitPos.z);
            case WEST -> new Vec3(1 - sitPos.z, sitPos.y, sitPos.x);
            default -> sitPos;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public final void registerDefaultStateMirrored(BlockState blockState) {
        this.registerDefaultState(blockState);
    }

    public static enum PartType implements StringRepresentable {
        LEFT("left"),
        CENTER("center"),
        RIGHT("right");

        private final String name;

        PartType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public abstract Vec3 getNorthFacingSitPos(Level world, BlockState state, BlockPos pos);

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.getStateForPlacementMirrored(ctx);
    }

    public BlockState getStateForPlacementMirrored(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

}
