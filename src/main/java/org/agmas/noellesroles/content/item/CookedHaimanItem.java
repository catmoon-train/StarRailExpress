package org.agmas.noellesroles.content.item;

import org.agmas.noellesroles.init.ModEffects;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CookedHaimanItem extends Item {

    public CookedHaimanItem(Properties properties) {
        super(properties);
    }

    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        var stack = super.finishUsingItem(itemStack, level, livingEntity);
        livingEntity.addEffect(ModEffects.of(MobEffects.NIGHT_VISION, 8 * 20, 1, false, true, true));
        return stack;
    }

}
