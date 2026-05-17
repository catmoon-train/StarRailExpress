package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.block_entity.HunterCageBlockEntity;
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;
import org.agmas.noellesroles.game.modes.repair.RepairArenaBuilder;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.List;

public class HunterChainItem extends Item {
    public HunterChainItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer hunter)
                || !(target instanceof ServerPlayer prisoner)) {
            return InteractionResult.PASS;
        }
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        var prisonerComponent = ModComponents.REPAIR_ROLES.get(prisoner);
        if (!RepairModeState.canUseHunterUtility(hunter) || prisoner == hunter || !prisonerComponent.downed
                || prisonerComponent.trialStand.present() || prisoner.isSpectator()) {
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.chain_requires_downed")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (hunterComponent.carryBlockedTicks > 0) {
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.carry_blocked",
                    Math.max(1, hunterComponent.carryBlockedTicks / 20)).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (hunterComponent.carrying != null) {
            RepairModeState.releaseCarried(hunter);
        }

        hunterComponent.carrying = prisoner.getUUID();
        prisonerComponent.carriedBy = hunter.getUUID();
        prisonerComponent.carryStruggleProgress = 0;
        prisonerComponent.lastStruggleSide = "";
        prisonerComponent.lastStruggleTick = -1000L;
        hunterComponent.sync();
        prisonerComponent.sync();
        prisoner.teleportTo(hunter.getX(), hunter.getY(), hunter.getZ());
        RepairGameplayEffects.burst(level, hunter.getX(), hunter.getY() + 0.8D, hunter.getZ(), 1);
        hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.carrying", prisoner.getName()), true);
        prisoner.displayClientMessage(Component.translatable("message.noellesroles.repair.you_are_carried"), false);
        if (!hunter.getAbilities().instabuild) {
            stack.hurtAndBreak(1, hunter, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer hunter)
                || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!RepairModeState.canUseHunterUtility(hunter)) {
            return InteractionResult.FAIL;
        }

        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        if (hunterComponent.carrying == null) {
            return InteractionResult.PASS;
        }
        if (!(level.getPlayerByUUID(hunterComponent.carrying) instanceof ServerPlayer prisoner)
                || !ModComponents.REPAIR_ROLES.get(prisoner).downed) {
            hunterComponent.carrying = null;
            hunterComponent.sync();
            return InteractionResult.FAIL;
        }

        BlockPos cagePos = context.getClickedPos();
        if (!(level.getBlockEntity(cagePos) instanceof HunterCageBlockEntity)) {
            BlockPos relative = cagePos.relative(context.getClickedFace());
            if (level.getBlockState(relative).canBeReplaced()) {
                cagePos = relative;
            }
        }
        HunterCageBlockEntity cage = level.getBlockEntity(cagePos) instanceof HunterCageBlockEntity existing
                ? existing
                : null;
        if (cage == null) {
            cagePos = findCagePos(level, cagePos);
            if (cagePos == null) {
                hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.no_cage_space")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            BlockState state = ModBlocks.HUNTER_CAGE.defaultBlockState();
            RepairArenaBuilder.trackGameplayPlacement(level, cagePos);
            level.setBlockAndUpdate(cagePos, state);
            if (level.getBlockEntity(cagePos) instanceof HunterCageBlockEntity created) {
                cage = created;
            }
        }
        if (cage == null || !cage.addPrisoner(prisoner.getUUID(), hunter.getUUID())) {
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.trial_full")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        var prisonerComponent = ModComponents.REPAIR_ROLES.get(prisoner);
        prisonerComponent.carriedBy = null;
        prisonerComponent.trialStand = org.agmas.noellesroles.component.RepairRolePlayerComponent.BlockPosTag.of(cagePos);
        prisoner.teleportTo(cagePos.getX() + 0.5D, cagePos.getY(), cagePos.getZ() + 0.5D);
        prisonerComponent.sync();
        hunterComponent.carrying = null;
        hunterComponent.sync();
        RepairGameplayEffects.burst(level, cagePos.getX() + 0.5D, cagePos.getY() + 1.0D, cagePos.getZ() + 0.5D, 2);
        hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.trial_started"), true);
        return InteractionResult.SUCCESS;
    }

    private static BlockPos findCagePos(ServerLevel level, BlockPos clickedPos) {
        for (BlockPos candidate : List.of(clickedPos, clickedPos.above(), clickedPos.north(), clickedPos.south(),
                clickedPos.east(), clickedPos.west())) {
            if (level.getBlockState(candidate).canBeReplaced() && level.getBlockState(candidate.above()).canBeReplaced()) {
                return candidate;
            }
        }
        for (BlockPos candidate : BlockPos.betweenClosed(clickedPos.offset(-1, 0, -1), clickedPos.offset(1, 1, 1))) {
            if (level.getBlockState(candidate).canBeReplaced() && level.getBlockState(candidate.above()).canBeReplaced()) {
                return candidate.immutable();
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.hunter_chain.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
