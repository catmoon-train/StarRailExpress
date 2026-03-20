package org.agmas.noellesroles.repack.items;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class PillItem extends Item {
    public PillItem(Properties settings) {
        super(settings);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        ItemStack result = super.finishUsingItem(stack, world, user);
        if (user instanceof Player player) {
            if (!world.isClientSide) {
                SREPlayerPoisonComponent.KEY.get(player).init();
            }
        }
        return result;
    }
}