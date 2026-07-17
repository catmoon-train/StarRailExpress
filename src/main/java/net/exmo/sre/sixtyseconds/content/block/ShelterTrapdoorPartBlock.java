package net.exmo.sre.sixtyseconds.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 避难所活板门的<b>部件块</b>（3×3×2 结构里除主控块外的 17 格，见 {@link ShelterTrapdoorBlock}）。
 * 右键任一部件 = 反查主控块坐标并走同一套门交互（{@link ShelterDoorBlock#useDoor}，传主控块坐标，
 * 因此整座活板门当作「一扇门」）。破坏任一部件连带清掉整座结构。
 */
public class ShelterTrapdoorPartBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ShelterTrapdoorPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    private static void delegate(Level level, BlockPos pos, Player player) {
        BlockPos controller = ShelterTrapdoorBlock.findController(level, pos);
        ShelterDoorBlock.useDoor(level, controller != null ? controller : pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        delegate(level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        delegate(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos controller = ShelterTrapdoorBlock.findController(level, pos);
            if (controller != null) {
                ShelterTrapdoorBlock.removeStructure(level, controller);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
