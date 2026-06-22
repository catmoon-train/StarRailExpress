package org.agmas.noellesroles.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;

public class CourierMailItem extends Item {
    public static BiConsumer<Player, InteractionHand> openSendScreen = null;
    public static BiConsumer<Player, InteractionHand> openReceiveScreen = null;

    public CourierMailItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        CourierMailData.appendTooltip(stack, ctx, tooltip, flag);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide) {
            boolean isSend = CourierMailData.getSender(stack).isEmpty() && !CourierMailData.isReply(stack);
            if (isSend) {
                if (openSendScreen != null) openSendScreen.accept(user, hand);
            } else {
                if (openReceiveScreen != null) openReceiveScreen.accept(user, hand);
            }
        }
        return InteractionResultHolder.success(stack);
    }

}
