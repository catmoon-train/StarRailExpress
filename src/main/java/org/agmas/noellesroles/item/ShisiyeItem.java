package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.item.CocktailItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ShisiyeItem extends CocktailItem {

    public ShisiyeItem(Properties settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        stack = super.finishUsingItem(stack, world, user);
        if (user instanceof ServerPlayer) {
            user.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    20 * 20,
                    9,
                    false, // ambient - 环境效果（粒子更少更透明）
                    true, // showParticles - 不显示粒子
                    true // showIcon - 不显示图标
            ));
        }
        return stack;
    }
}
