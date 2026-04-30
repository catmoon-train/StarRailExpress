package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DNFFoodItem extends Item {
    public DNFFoodItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, world, entity);
        if (!world.isClientSide && entity instanceof ServerPlayer serverPlayer && DNF.isDayNightFightMode(world)) {
            DNFPlayerComponent.KEY.get(serverPlayer).markAteFood(serverPlayer);
        }
        return result;
    }
}
