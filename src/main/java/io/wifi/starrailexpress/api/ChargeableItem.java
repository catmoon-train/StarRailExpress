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

package io.wifi.starrailexpress.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 蓄力物品接口，允许其他MOD添加自定义蓄力行为
 * 使用方法：在物品类实现本 interface（建议！！！），或使用 ChargeableItemRegistry.register(物品, 蓄力实现类); 来注册
 */
public interface ChargeableItem {
    /**
     * 获取最大蓄力时间
     * 
     * @param stack  物品堆栈
     * @param player 玩家
     * @return 最大蓄力时间（刻）
     */
    default int getMaxChargeTime(ItemStack stack, Player player) {
        int t = stack.getItem().getUseDuration(stack, player);
        if (t > 0) {
            return t;
        }
        return 0;
    }

    /**
     * 获取当前蓄力百分比
     * 
     * @param stack          物品堆栈
     * @param player         玩家
     * @param ticksUsingItem 当前使用物品的刻数
     * @return 蓄力百分比 (0.0-1.0)
     */
    default float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return (float) ticksUsingItem / (float) getMaxChargeTime(stack, player);
    }

    /**
     * 当蓄力完成时触发的回调
     * 
     * @param stack  物品堆栈
     * @param player 玩家
     */
    default void onFullyCharged(ItemStack stack, Player player) {
        // 默认空实现
    }

    /**
     * 获取蓄力时的最大条
     * 
     * @param stack  物品堆栈
     * @param player 玩家
     * @return 最大体力值
     */
    default float getMaxStamina(ItemStack stack, Player player) {
        return (float) getMaxChargeTime(stack, player); // 默认值
    }

    /**
     * 是否启用特殊的视觉效果（如屏幕边缘闪烁）
     * 
     * @param stack  物品堆栈
     * @param player 玩家
     * @return 是否启用特殊视觉效果
     */
    default boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return false;
    }
}