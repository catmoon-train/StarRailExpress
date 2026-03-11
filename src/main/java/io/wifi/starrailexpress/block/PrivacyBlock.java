package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.index.TMMProperties;
import io.wifi.starrailexpress.index.TMMSounds;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public interface PrivacyBlock {

    BooleanProperty OPAQUE = TMMProperties.OPAQUE;
    BooleanProperty INTERACTION_COOLDOWN = TMMProperties.INTERACTION_COOLDOWN;
    Direction[][] DIAGONALS = new Direction[][]{
            new Direction[]{Direction.NORTH, Direction.EAST},
            new Direction[]{Direction.SOUTH, Direction.EAST},
            new Direction[]{Direction.SOUTH, Direction.WEST},
            new Direction[]{Direction.NORTH, Direction.WEST},
            new Direction[]{Direction.UP, Direction.NORTH},
            new Direction[]{Direction.UP, Direction.EAST},
            new Direction[]{Direction.UP, Direction.SOUTH},
            new Direction[]{Direction.UP, Direction.WEST},
            new Direction[]{Direction.DOWN, Direction.NORTH},
            new Direction[]{Direction.DOWN, Direction.EAST},
            new Direction[]{Direction.DOWN, Direction.SOUTH},
            new Direction[]{Direction.DOWN, Direction.WEST}
    };

    int DELAY = 1;
    int COOLDOWN = 20;

    default void toggle(BlockState state, Level world, BlockPos pos) {
        boolean opaque = !state.getValue(OPAQUE);
        if (state.getValue(INTERACTION_COOLDOWN)) {
            world.setBlockAndUpdate(pos, state.setValue(INTERACTION_COOLDOWN, false));
            return;
        } else {
            world.playSound(null, pos, TMMSounds.BLOCK_PRIVACY_PANEL_TOGGLE, SoundSource.BLOCKS, 0.1f, opaque ? 1.0f : 1.2f);
        }

        world.setBlockAndUpdate(pos, state.setValue(OPAQUE, opaque).setValue(INTERACTION_COOLDOWN, true));
        world.scheduleTick(pos, state.getBlock(), COOLDOWN);
        Set<Direction> changedDirections = EnumSet.noneOf(Direction.class);
        for (Direction direction : Direction.values()) {
            BlockPos sidePos = pos.relative(direction);
            BlockState sideState = world.getBlockState(sidePos);
            if (this.canToggle(sideState) && sideState.getValue(OPAQUE) != opaque) {
                changedDirections.add(direction);
                world.scheduleTick(sidePos, sideState.getBlock(), DELAY);
            }
        }
        for (Direction[] diagonal : DIAGONALS) {
            if (diagonalHasAdjacentBlock(diagonal, changedDirections)) continue;
            BlockPos diagonalPos = this.offsetDiagonal(pos, diagonal);
            BlockState diagonalState = world.getBlockState(diagonalPos);
            if (this.canToggle(diagonalState) && diagonalState.getValue(OPAQUE) != opaque) {
                world.scheduleTick(diagonalPos, diagonalState.getBlock(), DELAY);
            }
        }
    }

    default boolean diagonalHasAdjacentBlock(Direction[] diagonal, Set<Direction> changedDirections) {
        return changedDirections.contains(diagonal[0]) || changedDirections.contains(diagonal[1]);
    }

    default BlockPos offsetDiagonal(BlockPos pos, Direction[] diagonal) {
        return pos.relative(diagonal[0]).relative(diagonal[1]);
    }

    default boolean canInteract(BlockState state, BlockPos pos, Level world, Player player, InteractionHand hand) {
        if (state.getValue(INTERACTION_COOLDOWN)) return false;
        if (player.getItemInHand(hand).getItem() instanceof SpyglassItem) return false;
        for (Direction direction : Direction.values()) {
            BlockState sideState = world.getBlockState(pos.relative(direction));
            if (sideState.hasProperty(INTERACTION_COOLDOWN) && sideState.getValue(INTERACTION_COOLDOWN)) return false;
        }
        for (Direction[] diagonal : DIAGONALS) {
            BlockPos diagonalPos = this.offsetDiagonal(pos, diagonal);
            BlockState diagonalState = world.getBlockState(diagonalPos);
            if (diagonalState.hasProperty(INTERACTION_COOLDOWN) && diagonalState.getValue(INTERACTION_COOLDOWN)) return false;
        }
        return true;
    }

    default boolean canToggle(BlockState state) {
        return state.getBlock() instanceof PrivacyBlock;
    }


}
