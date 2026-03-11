package io.wifi.starrailexpress.api.impl;

import io.wifi.starrailexpress.api.ChargeableItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 手榴弹的蓄力实现
 */
public class GrenadeChargeableItem implements ChargeableItem {
    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return 20; // 20刻（1秒）蓄力时间
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return 20.0f;
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return false;
    }
}