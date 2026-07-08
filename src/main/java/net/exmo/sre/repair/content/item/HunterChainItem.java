package net.exmo.sre.repair.content.item;

import net.minecraft.ChatFormatting;
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
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.util.RepairGameplayEffects;
import net.exmo.sre.repair.state.RepairModeState;

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

        // 现在笼子放置和囚禁逻辑已经在HunterCageBlock中处理
        // 这里只需要允许锁链作为有效的猎人工具使用
        return InteractionResult.PASS;
    }




    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.hunter_chain.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
