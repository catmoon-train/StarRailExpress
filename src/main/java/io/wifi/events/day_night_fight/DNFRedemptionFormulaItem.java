package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class DNFRedemptionFormulaItem extends Item {
    private static final int HEART_COST = 5;

    public DNFRedemptionFormulaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide || !(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!DNF.isDayNightFightMode(world) || (!DNF.isDNFPoisoner(player) && !DNF.isDNFChef(player))) {
            player.displayClientMessage(Component.translatable("message.dnf.redemption.wrong_role")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (!component.isRedemptionRecipeUnlocked()) {
            player.displayClientMessage(Component.translatable("message.dnf.redemption.recipe_locked")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (component.isRedemptionPotionCrafted()) {
            player.displayClientMessage(Component.translatable("message.dnf.redemption.already_crafted")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!hasItem(player, DNFItems.TOXIC_HEART, HEART_COST)
                || !hasItem(player, DNFItems.WATER_BOTTLE, 1)
                || !hasItem(player, Items.GLASS_BOTTLE, 1)) {
            player.displayClientMessage(Component.translatable("message.dnf.redemption.missing_ingredients")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(stack);
        }

        consumeItem(player, DNFItems.TOXIC_HEART, HEART_COST);
        consumeItem(player, DNFItems.WATER_BOTTLE, 1);
        consumeItem(player, Items.GLASS_BOTTLE, 1);
        DNFItems.giveOrDrop(player, new ItemStack(DNFItems.REDEMPTION_POTION, 2));
        player.displayClientMessage(Component.translatable("message.dnf.redemption.crafted")
                .withStyle(ChatFormatting.DARK_GREEN), false);

        ServerPlayer partner = component.getRedemptionPartner(player.serverLevel());
        component.markRedemptionPotionCrafted(player);
        if (partner != null) {
            DNFPlayerComponent.KEY.get(partner).markRedemptionPotionCrafted(partner);
        }
        DNF.triggerRedemptionEnding(player.serverLevel());
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        DNFItems.appendDnfTooltip(stack, context, tooltip, flag,
                "item.starrailexpress.dnf_redemption_formula.tooltip");
    }

    private static boolean hasItem(ServerPlayer player, Item item, int count) {
        int found = 0;
        for (List<ItemStack> compartment : player.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (stack.is(item)) {
                    found += stack.getCount();
                    if (found >= count) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void consumeItem(ServerPlayer player, Item item, int count) {
        int remaining = count;
        for (List<ItemStack> compartment : player.getInventory().compartments) {
            for (ItemStack stack : compartment) {
                if (!stack.is(item)) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
                if (remaining <= 0) {
                    return;
                }
            }
        }
    }
}
