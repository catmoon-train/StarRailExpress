package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block_entity.TicketGateBlockEntity;
import io.wifi.starrailexpress.content.item.AdmissionTicketItem;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TicketGateBlock extends DoorBlock implements EntityBlock {
    public TicketGateBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new TicketGateBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER || !type.equals(TMMBlockEntities.TICKET_GATE)) {
            return null;
        }
        return level.isClientSide ? null
                : (l, p, s, e) -> TicketGateBlockEntity.serverTick(l, p, s, (TicketGateBlockEntity) e);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (canBind(state, level, pos, player)) {
            return InteractionResult.CONSUME;
        }
        return handleGateOpen(state, level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (canBind(state, level, pos, player)) {
            return ItemInteractionResult.CONSUME;
        }
        InteractionResult result = handleGateOpen(state, level, pos, player);
        return result.consumesAction()
                ? ItemInteractionResult.CONSUME
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 创意模式右键用入场券绑定检票门，同时记录票名 */
    private boolean canBind(BlockState state, Level level, BlockPos pos, Player player) {
        if (!player.isCreative()) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(TMMItems.ADMISSION_TICKET)) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!(level.getBlockEntity(lowerPos) instanceof TicketGateBlockEntity gate)) {
            return false;
        }
        String ticketId = AdmissionTicketItem.getTicketId(stack);
        if (ticketId.isBlank()) {
            player.displayClientMessage(
                    Component.translatable("message.starrailexpress.ticket_gate.invalid_ticket"), true);
            return true;
        }
        String ticketName = stack.getHoverName().getString();
        gate.setTicketInfo(ticketId, ticketName);
        player.displayClientMessage(Component.translatable("message.starrailexpress.ticket_gate.bound"), true);
        return true;
    }

    /** 普通检票开门逻辑：校验票ID + 票名 */
    private InteractionResult handleGateOpen(BlockState state, Level level, BlockPos pos, Player player) {
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!(level.getBlockEntity(lowerPos) instanceof TicketGateBlockEntity gate)) {
            return InteractionResult.FAIL;
        }
        if (!gate.hasTicket()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.starrailexpress.ticket_gate.not_bound"), true);
            }
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getMainHandItem();
        // 校验票ID
        if (!AdmissionTicketItem.matches(stack, gate.getTicketId())) {
            if (!level.isClientSide) {
                level.playSound(null, lowerPos, TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                player.displayClientMessage(
                        Component.translatable("message.starrailexpress.ticket_gate.requires_ticket"), true);
            }
            return InteractionResult.FAIL;
        }
        // 校验票名
        String heldName = stack.getHoverName().getString();
        if (!heldName.equals(gate.getTicketName())) {
            if (!level.isClientSide) {
                level.playSound(null, lowerPos, TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                player.displayClientMessage(
                        Component.translatable("message.starrailexpress.ticket_gate.requires_ticket"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            if (!player.isCreative()) {
                AdmissionTicketItem.consumeUse(stack);
            }
            BlockState lowerState = level.getBlockState(lowerPos);
            boolean open = lowerState.getValue(DoorBlock.OPEN);
            level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, !open),
                    Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(lowerPos.above(),
                    level.getBlockState(lowerPos.above()).setValue(DoorBlock.OPEN, !open),
                    Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        }
        return InteractionResult.CONSUME;
    }
}
