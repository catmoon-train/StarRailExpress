package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.index.TMMProperties;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NeonTubeBlock extends BarBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty ACTIVE = TMMProperties.ACTIVE;

    protected static final VoxelShape X_SHAPE = Block.box(0, 4, 4, 16, 12, 12);
    protected static final VoxelShape Y_SHAPE = Block.box(4, 0, 4, 12, 16, 12);
    protected static final VoxelShape Z_SHAPE = Block.box(4, 4, 0, 12, 12, 16);

    public NeonTubeBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState().setValue(LIT, false).setValue(ACTIVE, true));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
        };
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        BlockState updatedState = super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        if (updatedState == null) {
            return null;
        }
        Direction.Axis axis = state.getValue(AXIS);
        if (direction.getAxis() == axis && neighborState.is(this) && neighborState.getValue(AXIS) == axis) {
            return updatedState.setValue(ACTIVE, neighborState.getValue(ACTIVE));
        }
        return updatedState;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, world, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        boolean lit = state.getValue(LIT);
        boolean active = state.getValue(ACTIVE);
        Direction.Axis axis = state.getValue(AXIS);
        Direction direction = switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
        BlockPos.MutableBlockPos mutable = pos.mutable();
        while (this.toggle(world, mutable, axis, lit)) {
            mutable.move(direction);
        }
        mutable.set(pos).move(direction.getOpposite());
        while (this.toggle(world, mutable, axis, lit)) {
            mutable.move(direction.getOpposite());
        }
        world.playSound(null, pos, TMMSounds.BLOCK_LIGHT_TOGGLE, SoundSource.BLOCKS, 0.5f, lit ? 1f : 1.2f);
        if (!active) {
            world.playSound(player, pos, TMMSounds.BLOCK_BUTTON_TOGGLE_NO_POWER, SoundSource.BLOCKS, 0.1f, 1f);
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    private boolean toggle(Level world, BlockPos pos, Direction.Axis axis, boolean lit) {
        BlockState state = world.getBlockState(pos);
        if (state.is(this) && state.getValue(AXIS) == axis && state.getValue(LIT) == lit) {
            world.setBlockAndUpdate(pos, state.setValue(LIT, !lit));
            return true;
        }
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, ACTIVE);
    }
}
