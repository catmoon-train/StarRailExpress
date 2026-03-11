package io.wifi.starrailexpress.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 蓄力物品接口，允许其他MOD添加自定义蓄力行为
 */
public interface ChargeableItem {
    /**
     * 获取最大蓄力时间
     * @param stack 物品堆栈
     * @param player 玩家
     * @return 最大蓄力时间（刻）
     */
    int getMaxChargeTime(ItemStack stack, Player player);

    /**
     * 获取当前蓄力百分比
     * @param stack 物品堆栈
     * @param player 玩家
     * @param ticksUsingItem 当前使用物品的刻数
     * @return 蓄力百分比 (0.0-1.0)
     */
    float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem);

    /**
     * 当蓄力完成时触发的回调
     * @param stack 物品堆栈
     * @param player 玩家
     */
    default void onFullyCharged(ItemStack stack, Player player) {
        // 默认空实现
    }

    /**
     * 获取蓄力时的最大体力值
     * @param stack 物品堆栈
     * @param player 玩家
     * @return 最大体力值
     */
    default float getMaxStamina(ItemStack stack, Player player) {
        return 8.0f; // 默认值
    }

    /**
     * 是否启用特殊的视觉效果（如屏幕边缘闪烁）
     * @param stack 物品堆栈
     * @param player 玩家
     * @return 是否启用特殊视觉效果
     */
    default boolean hasSpecialVisualEffects(ItemStack stack, Player player) {
        return false;
    }
}