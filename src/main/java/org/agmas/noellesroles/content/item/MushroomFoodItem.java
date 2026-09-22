package org.agmas.noellesroles.content.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEffects;

public class MushroomFoodItem extends Item {
    private static final int NORMAL_DURATION = 20 * 20;
    private final boolean poisonous;

    public MushroomFoodItem(Properties properties, boolean poisonous) {
        super(properties);
        this.poisonous = poisonous;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        ItemStack result = super.finishUsingItem(stack, level, user);
        if (!level.isClientSide) {
            if (poisonous) {
                user.addEffect(new MobEffectInstance(ModEffects.GLITCH, NORMAL_DURATION, 0, false, true, true));
                user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, NORMAL_DURATION, 0, false, true, true));
                user.addEffect(new MobEffectInstance(ModEffects.PUPPET_WANDER, 3 * 20, 0, false, true, true));
            } else {
                user.addEffect(new MobEffectInstance(ModEffects.NEGATIVE_EFFECT_RESISTANCE,
                        NORMAL_DURATION, 0, false, true, true));
            }
        }
        return result;
    }
}
