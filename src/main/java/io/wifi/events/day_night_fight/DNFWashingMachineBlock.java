package io.wifi.events.day_night_fight;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class DNFWashingMachineBlock extends Block {
    public DNFWashingMachineBlock(Properties properties) {
        super(properties);
    }
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(DNFItems.SOAP)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.FAIL;
        }
        if (!DNF.isDayNightFightMode(level)) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.clothes.dnf_only")
                    .withStyle(ChatFormatting.YELLOW), true);
            return ItemInteractionResult.FAIL;
        }
        if (!DNFItems.washClothes(serverPlayer)) {
            return ItemInteractionResult.FAIL;
        }
        if (!serverPlayer.isCreative()) {
            stack.hurtAndBreak(1, serverPlayer,
                    hand == InteractionHand.MAIN_HAND
                            ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
                            : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.9f, 1.35f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.dnf.clothes.need_soap")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }
}
