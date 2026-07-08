package net.exmo.sre.repair.content.item;

import net.exmo.sre.repair.logic.RepairSanitySystem;
import net.exmo.sre.repair.state.RepairModeState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.ModComponents;

import java.util.List;

/** 镇静剂（恐鬼症道具）：服用恢复大量理智。 */
public class SanityMedsItem extends Item {
    private static final int RESTORE_AMOUNT = 40;
    private static final int COOLDOWN_TICKS = 20 * 10;

    public SanityMedsItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide || !(user instanceof ServerPlayer player)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.consume(stack);
        }
        if (!RepairModeState.isRepairGameRunning(player) || !RepairModeState.isNonHunterRepairPlayer(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (ModComponents.REPAIR_ROLES.get(player).sanity >= RepairSanitySystem.MAX_SANITY) {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.sanity_full")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        RepairSanitySystem.restore(player, RESTORE_AMOUNT);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.9F, 1.1F);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.2D, player.getZ(), 10, 0.35D, 0.4D, 0.35D, 0.02D);
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.sanity_restored",
                RESTORE_AMOUNT).withStyle(ChatFormatting.GREEN), true);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.sanity_meds.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
