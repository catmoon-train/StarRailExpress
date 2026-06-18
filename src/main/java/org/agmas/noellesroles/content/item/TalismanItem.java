package org.agmas.noellesroles.content.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEffects;

public class TalismanItem extends Item {

    public TalismanItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide()) return;

        if (player.getInventory().contains(itemStack)) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.LOW_SAN_SHADER_RESISTANCE,
                    50,
                    0,
                    true,
                    false,
                    false
            ));
            player.addEffect(new MobEffectInstance(
                    ModEffects.MOOD_REGENERATION,
                    50,
                    0,
                    true,
                    false,
                    false
            ));
        }
    }
}
