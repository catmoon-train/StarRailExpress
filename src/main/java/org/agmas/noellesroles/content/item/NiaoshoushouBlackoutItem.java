package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 鸟兽兽的范围关灯，固定半径 30 格、120 秒冷却。 */
public class NiaoshoushouBlackoutItem extends Item {
    public NiaoshoushouBlackoutItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isSpectator()) {
            return InteractionResultHolder.fail(stack);
        }
        SREWorldBlackoutComponent.KEY.get(level).triggerBlackout(serverPlayer.blockPosition(), 30, true,
                SREWorldBlackoutComponent.getMaxDuration(level));
        if (!player.isCreative()) {
            stack.shrink(1);
            player.getCooldowns().addCooldown(this, 2 * 60 * 20);
        }
        return InteractionResultHolder.consume(stack);
    }
}
