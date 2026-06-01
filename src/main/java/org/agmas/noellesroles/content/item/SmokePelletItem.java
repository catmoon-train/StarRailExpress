package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;

import java.util.List;

public class SmokePelletItem extends Item {
    public SmokePelletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (!RepairModeState.canUseSurvivorUtility(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            RepairGameplayEffects.burst(serverLevel, player.getX(), player.getY() + 0.8D, player.getZ(), 1);
            RepairGameplayEffects.disorientHunters(serverLevel, player.getX(), player.getY(), player.getZ(), 5.5D, 90);
            for (net.minecraft.server.level.ServerPlayer hunter : serverLevel.players()) {
                if (RepairModeState.isHunter(hunter) && hunter.distanceToSqr(player) <= 6.5D * 6.5D) {
                    RepairModeState.blockHunterCarry(hunter, 20 * 8);
                    hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.carry_jammed")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(this, 20 * 18);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.smoke_pellet.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
