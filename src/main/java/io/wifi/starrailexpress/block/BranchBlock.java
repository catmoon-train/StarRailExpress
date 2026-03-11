package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.index.tag.TMMBlockTags;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * @author EightSidedSquare
 */

public class BranchBlock extends PipeBlock {

    public static final Map<Block, Block> STRIPPED_BRANCHES = new Object2ObjectOpenHashMap<>();

    public BranchBlock(Properties settings) {
        super(0.25f, settings);
        this.registerDefaultState(super.defaultBlockState()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction side = ctx.getClickedFace();
        if (state != null) return this.connectState(state
                .setValue(PROPERTY_BY_DIRECTION.get(side), ctx.isSecondaryUseActive())
                .setValue(PROPERTY_BY_DIRECTION.get(side.getOpposite()), true), pos, world);
        return null;
    }

    public BlockState connectState(BlockState state, BlockPos pos, LevelAccessor world) {
        BlockState blockState = state;
        for (Direction direction : Direction.values())
            blockState = this.connectState(blockState, pos, world, direction);
        return blockState;
    }

    public BlockState connectState(BlockState state, BlockPos pos, LevelAccessor world, Direction direction) {
        BlockState sideState = world.getBlockState(pos.relative(direction));
        if (!sideState.is(TMMBlockTags.BRANCHES)) return state;
        BooleanProperty sideProperty = PROPERTY_BY_DIRECTION.get(direction.getOpposite());
        if (sideState.hasProperty(sideProperty) && sideState.getValue(sideProperty)) {
            return state.setValue(PROPERTY_BY_DIRECTION.get(direction), true);
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return this.connectState(state, pos, world, direction);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.SHEARS)) {
            return super.useItemOn(stack, state, world, pos, player, hand, hit);
        }
        boolean success = false;
        Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        Direction direction = null;
        if (hitPos.x < 0.25) direction = Direction.WEST;
        else if (hitPos.x > 0.75) direction = Direction.EAST;
        else if (hitPos.y < 0.25) direction = Direction.DOWN;
        else if (hitPos.y > 0.75) direction = Direction.UP;
        else if (hitPos.z < 0.25) direction = Direction.NORTH;
        else if (hitPos.z > 0.75) direction = Direction.SOUTH;
        if (direction != null) {
            BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
            if (state.getValue(property)) {
                world.setBlock(pos, state.setValue(property, false), Block.UPDATE_ALL);
                BlockPos sidePos = pos.relative(direction);
                BlockState sideState = world.getBlockState(sidePos);
                BooleanProperty oppositeProperty = PROPERTY_BY_DIRECTION.get(direction.getOpposite());
                if (sideState.is(TMMBlockTags.BRANCHES) && sideState.hasProperty(oppositeProperty) && sideState.getValue(oppositeProperty)) {
                    world.setBlock(sidePos, sideState.setValue(oppositeProperty, false), Block.UPDATE_ALL);
                }
                success = true;
            }
        }
        Direction side = hit.getDirection();
        BooleanProperty property = PROPERTY_BY_DIRECTION.get(side);
        if (!success && !state.getValue(property)) {
            world.setBlock(pos, state.setValue(property, true), Block.UPDATE_ALL);
            success = true;
        }
        if (success) {
            world.playSound(player, pos, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 1f, 1f);
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, world, pos, player, hand, hit);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return null;
    }
}