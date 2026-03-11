package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.block.property.CouchArms;
import io.wifi.starrailexpress.index.TMMProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CouchBlock extends HorizontalFacingMountableBlock {
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);
    public static final EnumProperty<CouchArms> ARMS = TMMProperties.COUCH_ARMS;

    public CouchBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState()
                .setValue(ARMS, CouchArms.SINGLE)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMS, BlockStateProperties.HORIZONTAL_FACING);
    }
    

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Vec3 getNorthFacingSitPos(Level world, BlockState state, BlockPos pos) {
        return new Vec3(0.5f, 0.5f, 0.375f);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx).setValue(ARMS, CouchArms.NO_ARMS);
        BlockState clickedBlockState = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(ctx.getClickedFace().getOpposite()));

        if (ctx.isSecondaryUseActive()) {
            state = state.setValue(ARMS, CouchArms.SINGLE);
        } else if (clickedBlockState.getBlock() instanceof CouchBlock && clickedBlockState.getValue(BlockStateProperties.HORIZONTAL_FACING).equals(state.getValue(BlockStateProperties.HORIZONTAL_FACING))) {
            if (clickedBlockState.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise().equals(ctx.getClickedFace())) {
                state = state.setValue(ARMS, CouchArms.RIGHT);
            } else if (clickedBlockState.getValue(BlockStateProperties.HORIZONTAL_FACING).getCounterClockWise().equals(ctx.getClickedFace())) {
                state = state.setValue(ARMS, CouchArms.LEFT);
            }
        }

        return state;
    }
}
