package org.agmas.noellesroles.content.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;

public class RepairPalletBlock extends Block {
    public RepairPalletBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        return dropPallet(level, pos, player) ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        dropPallet(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private boolean dropPallet(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!RepairModeState.canUseSurvivorUtility(serverPlayer)) {
            return true;
        }
        RepairGameplayEffects.burst(serverLevel, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 2);
        int hits = 0;
        for (ServerPlayer target : serverLevel.players()) {
            if (target.distanceToSqr(pos.getCenter()) <= 3.5D * 3.5D && RepairGameplayEffects.isHunter(target)) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, true, true));
                hits++;
            }
        }
        if (hits > 0) {
            int reward = 30 * hits;
            RepairModeState.awardCoins(serverPlayer, reward, "repair_coin_source.rescue");
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.coin_reward", reward)
                    .withStyle(ChatFormatting.GOLD), true);
        }
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.pallet_dropped")
                .withStyle(ChatFormatting.AQUA), true);
        level.destroyBlock(pos, false);
        return true;
    }
}
