package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class DNFWaterItem extends Item {
    public DNFWaterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide && player instanceof ServerPlayer serverPlayer && DNF.isDayNightFightMode(world)) {
            DNFPlayerComponent.KEY.get(serverPlayer).markDrankWater(serverPlayer);
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
            if (!serverPlayer.isCreative()) {
                stack.shrink(1);
                DNFItems.giveOrDrop(serverPlayer, new ItemStack(Items.GLASS_BOTTLE));
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }
}
