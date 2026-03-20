package org.agmas.noellesroles.repack.items;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.repack.HSRConstants;
import org.agmas.noellesroles.repack.HSRItems;
import org.jetbrains.annotations.NotNull;

public class PillItem extends Item {
    public PillItem(Properties settings) {
        super(settings);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        var tag = customData.copyTag();
        boolean poisonous = false;
        if (tag.contains(HSRItems.PILL_POISONOUS_KEY)) {
            poisonous = tag.getBoolean(HSRItems.PILL_POISONOUS_KEY);
        }
        ItemStack result = super.finishUsingItem(stack, world, user);
        if (user instanceof Player player && !world.isClientSide) {
            if (poisonous) {
                SREPlayerPoisonComponent.KEY.get(player).setPoisonTicks(HSRConstants.toxinPoisonTime, player.getUUID());
            } else {
                SREPlayerPoisonComponent.KEY.get(player).init();
            }
        }
        return result;
    }
}