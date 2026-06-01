package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.init.ModEffects;

import java.util.List;

public class RepairMedkitItem extends Item {
    private static final float HEAL_AMOUNT = 8.0F;

    public RepairMedkitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !RepairModeState.isNonHunterRepairPlayer(serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        var component = ModComponents.REPAIR_ROLES.get(serverPlayer);
        if (component.downed || component.trialStand.present() || component.carriedBy != null) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.medkit_cannot_use")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (serverPlayer.getHealth() >= serverPlayer.getMaxHealth() && component.repairInjuryLevel <= 0) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.medkit_full")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        component.repairInjuryLevel = Math.max(0, component.repairInjuryLevel - 1);
        component.lastHunterHitTick = -1000L;
        component.sync();
        serverPlayer.heal(HEAL_AMOUNT);
        serverPlayer.removeEffect(MobEffects.WEAKNESS);
        serverPlayer.removeEffect(MobEffects.DARKNESS);
        serverPlayer.removeEffect(ModEffects.NO_COLLIDE);
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.8F, 1.25F);
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.medkit_used"), true);
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.getCooldowns().addCooldown(this, 20 * 12);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.repair_medkit.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
