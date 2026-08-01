/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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