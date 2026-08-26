package org.agmas.noellesroles.content.item;

import org.agmas.noellesroles.role_data.killer.DoremyRoleData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DoremyGhostItem extends Item {

    private static final int COOLDOWN_TICKS = 20 * 90;

    public DoremyGhostItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        var item = player.getItemInHand(interactionHand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(item);
        }
        if (DoremyRoleData.isDreaming(player)) {
            player.displayClientMessage(
                    Component.translatable("item.noellesroles.doremy_ghost.abnormal").withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(item);
        }
        return super.use(level, player, interactionHand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        var item = super.finishUsingItem(itemStack, level, livingEntity);
        if (livingEntity instanceof ServerPlayer player) {
            {
                if (!player.isCreative())
                    player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                DoremyRoleData.tryDream(player, 5 * 20);
                player.displayClientMessage(Component.translatable("message.item.noellesroles.doremy_ghost.use"), true);
            }
        }
        return item;
    }
}
