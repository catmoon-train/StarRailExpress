package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.agmas.noellesroles.content.block_entity.RepairStationBlockEntity;
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;

import java.util.List;

public class HunterJammerItem extends Item {
    public HunterJammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        if (!(context.getLevel().getBlockEntity(pos) instanceof RepairStationBlockEntity station)) {
            return InteractionResult.PASS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !RepairModeState.canUseHunterUtility(player)) {
            return InteractionResult.FAIL;
        }
        station.sabotage(18, 20 * 18);
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            RepairGameplayEffects.burst(serverLevel, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, 1);
        }
        if (context.getPlayer() != null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.station_jammed")
                    .withStyle(ChatFormatting.RED), true);
            player.getCooldowns().addCooldown(this, 20 * 35);
            RepairModeState.startSkillCooldown(player, 20 * 35, "warden_jam");
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().hurtAndBreak(1, player,
                        net.minecraft.world.entity.LivingEntity.getSlotForHand(context.getHand()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.hunter_jammer.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
