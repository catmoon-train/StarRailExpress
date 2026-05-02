package io.wifi.events.day_night_fight;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.item.ThrowingKnife;
import org.agmas.noellesroles.init.ModEntities;

import java.util.List;

public class DNFFlyingKnifeItem extends ThrowingKnife {
    public DNFFlyingKnifeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer player)) {
            user.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (!DNF.isDNFKiller(player)) {
            player.displayClientMessage(Component.translatable("message.dnf.item.killer_only")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator() || remainingUseTicks >= getUseDuration(stack, user) - 8
                || !(user instanceof ServerPlayer player)) {
            return;
        }
        if (!DNF.isDNFKiller(player)) {
            player.displayClientMessage(Component.translatable("message.dnf.item.killer_only")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        throwDnfKnife(player, stack, true);
    }

    public static boolean throwDnfKnife(ServerPlayer player, ItemStack stack, boolean consume) {
        if (player.getCooldowns().isOnCooldown(DNFItems.FLYING_KNIFE)) {
            return false;
        }
        if (consume && !player.isCreative()) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(DNFItems.FLYING_KNIFE, 20);
        DNFFlyingKnifeEntity entity = new DNFFlyingKnifeEntity(ModEntities.THROWING_KNIFE, player, player.level(),
                DNFItems.FLYING_KNIFE.getDefaultInstance());
        entity.setPos(player.getEyePosition(1.4f));
        entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.3f, 1.0f);
        entity.setOwner(player);
        player.level().addFreshEntity(entity);
        player.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, 1f, 1.4f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        DNFItems.appendDnfTooltip(stack, context, tooltip, flag, "item.starrailexpress.dnf_flying_knife.tooltip");
    }
}
