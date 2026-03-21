package org.agmas.noellesroles.item.charge_item;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.client.StaminaRenderer;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class StalkerKnifeChargeItem implements ChargeableItem {
    @Override
    public int getMaxChargeTime(ItemStack itemStack, Player player) {
        Integer i = itemStack.get(SREDataComponentTypes.WEAPON_USED_TIME);
        return i==null ? 12 : i;
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        Integer i = stack.get(SREDataComponentTypes.WEAPON_USED_TIME);
        return i==null ? 12 : i;
    }

    @Override
    public void onFullyCharged(ItemStack stack, Player player) {
        // 触发屏幕边缘效果
        StaminaRenderer.triggerScreenEdgeEffect(0xFF0000, 300L, 0.5f);
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }
}