package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMProperties;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public abstract class TMMButtonBlock extends ButtonBlock {
    public static final BooleanProperty ACTIVE = TMMProperties.ACTIVE;

    public TMMButtonBlock(Properties settings) {
        super(BlockSetType.IRON, 20, settings);
        this.registerDefaultState(super.defaultBlockState().setValue(ACTIVE, true));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState placementState = super.getStateForPlacement(ctx);
        if (placementState != null) {
            return placementState.setValue(ACTIVE, true);
        }
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && state.getValue(ACTIVE) && getConnectedDirection(state) == direction ? 15 : 0;
    }

    @Override
    public void press(BlockState state, Level world, BlockPos pos, @Nullable Player player) {
        if (state.getValue(ACTIVE)) {
            if (!world.isClientSide) {
                Iterable<BlockPos> iterable = BlockPos.withinManhattan(pos, 1, 1, 1);
                for (BlockPos blockPos : iterable) {
                    if (blockPos.equals(pos)) {
                        continue;
                    }
                    if (this.tryOpenDoors(world, blockPos)) {
                        break;
                    }
                }
            }
        } else {
            world.playSound(player, pos, TMMSounds.BLOCK_BUTTON_TOGGLE_NO_POWER, SoundSource.BLOCKS, 0.1f, 1f);
        }
        super.press(state, world, pos, player);
    }

    private boolean tryOpenDoors(Level world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof SmallDoorBlockEntity entity) {
            if (entity.isJammed()) {
                if (!world.isClientSide)
                    world.playSound(null, entity.getBlockPos().getX() + .5f, entity.getBlockPos().getY() + 1, entity.getBlockPos().getZ() + .5f, TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                return false;
            }

            entity.toggle(false);
            return true;
        }
        return false;
    }

    @Override
    protected void playSound(@Nullable Player player, LevelAccessor world, BlockPos pos, boolean powered) {
        world.playSound(player, pos, this.getSound(powered), SoundSource.BLOCKS, 0.5f, powered ? 1.0f : 1.5f);
    }

    @Override
    protected SoundEvent getSound(boolean powered) {
        return TMMSounds.BLOCK_SPACE_BUTTON_TOGGLE;
    }
}
