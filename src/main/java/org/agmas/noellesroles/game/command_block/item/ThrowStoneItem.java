package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.CommandBlockThrownEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 掷出后命中才眩晕 5 秒。买到的石头用一次少一个。 */
public class ThrowStoneItem extends Item {
    public ThrowStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator() || stack.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CommandBlockThrownEntity.throwStone(serverPlayer);
            stack.shrink(1);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
            InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }
}
