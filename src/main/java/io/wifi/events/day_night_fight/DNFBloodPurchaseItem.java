package io.wifi.events.day_night_fight;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Supplier;

import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;

public class DNFBloodPurchaseItem extends Item {
    private final int price;
    private final Supplier<ItemStack> purchase;
    private final String nameKey;

    public DNFBloodPurchaseItem(Properties properties, int price, Supplier<ItemStack> purchase, String nameKey) {
        super(properties);
        this.price = price;
        this.purchase = purchase;
        this.nameKey = nameKey;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!DNF.isDNFKiller(player)) {
            player.displayClientMessage(Component.translatable("message.dnf.item.killer_only")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (buy(player, price, purchase.get(), nameKey)) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.starrailexpress.dnf_blood_purchase.tooltip", price)
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable(nameKey).withStyle(ChatFormatting.GRAY));
    }

    static boolean buy(ServerPlayer player, int price, ItemStack purchase, String nameKey) {
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (!component.spendBlood(price)) {
            player.displayClientMessage(Component.translatable("message.dnf.killer.not_enough_blood", price)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!player.addItem(purchase.copy())) {
            player.drop(purchase.copy(), false);
        }
        player.displayClientMessage(Component.translatable("message.dnf.blood_shop.bought",
                Component.translatable(nameKey), component.getBlood()).withStyle(ChatFormatting.RED), true);
        return true;
    }
}
