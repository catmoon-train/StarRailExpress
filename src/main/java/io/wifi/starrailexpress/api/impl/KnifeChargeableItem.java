package io.wifi.starrailexpress.api.impl;

import java.awt.Color;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.client.StaminaRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 刀的蓄力实现
 */
public class KnifeChargeableItem implements ChargeableItem {
    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        // 拥有2D视角效果时，蓄力时间降至0.2秒（4刻）
        if (player.hasEffect(ModEffects.TWO_DIMENSIONAL_CAMERA)) {
            return 4;
        }
        return 8; // 10刻（0.5秒）蓄力时间
    }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.min((float) ticksUsingItem / getMaxChargeTime(stack, player), 1f);
    }

    @Override
    public float getMaxStamina(ItemStack stack, Player player) {
        return getMaxChargeTime(stack, player);
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return true;
    }

    @Override
    public void onFullyCharged(ItemStack stack, Player player) {
        // 触发屏幕边缘效果
        StaminaRenderer.triggerScreenEdgeEffect(Color.WHITE.getRGB(), 300L, 0.5f);
        // 默认会触发
    }
}