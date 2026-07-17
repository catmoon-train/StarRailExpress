package net.exmo.sre.sixtyseconds.content.block;

import com.mojang.serialization.MapCodec;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsNewspaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SixtySecondsMailboxBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SixtySecondsMailboxBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new SixtySecondsMailboxBlock());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mailbox) {
                mailbox.ownerTeamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
                mailbox.setChanged();
                SixtySecondsNewspaper.registerMailbox(serverLevel, mailbox.ownerTeamId, pos);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !SixtySecondsMod.isActive(level)) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mailbox)) {
            return ItemInteractionResult.SUCCESS;
        }
        int playerTeamId = SixtySecondsStatsComponent.KEY.get(serverPlayer).teamId;
        if (mailbox.ownerTeamId != playerTeamId) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.mailbox_not_owner"),
                    true);
            return ItemInteractionResult.SUCCESS;
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new net.exmo.sre.sixtyseconds.content.mail.SixtySecondsMailboxContainer(
                        id, inv, mailbox),
                Component.translatable("container.noellesroles.sixty_seconds_mailbox")));
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !SixtySecondsMod.isActive(level)) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mailbox)) {
            return InteractionResult.SUCCESS;
        }
        int playerTeamId = SixtySecondsStatsComponent.KEY.get(serverPlayer).teamId;
        if (mailbox.ownerTeamId != playerTeamId) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.mailbox_not_owner"),
                    true);
            return InteractionResult.SUCCESS;
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new net.exmo.sre.sixtyseconds.content.mail.SixtySecondsMailboxContainer(
                        id, inv, mailbox),
                Component.translatable("container.noellesroles.sixty_seconds_mailbox")));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity mailbox) {
                Containers.dropContents(level, pos, mailbox);
            }
            if (level instanceof ServerLevel serverLevel) {
                SixtySecondsNewspaper.unregisterMailbox(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
