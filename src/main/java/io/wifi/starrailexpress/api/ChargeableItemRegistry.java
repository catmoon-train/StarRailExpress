package io.wifi.starrailexpress.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 蓄力物品注册表，用于管理所有可蓄力的物品
 */
public class ChargeableItemRegistry {
    private static final Map<Item, ChargeableItem> CHARGEABLE_ITEMS = new HashMap<>();

    /**
     * 注册蓄力物品
     * @param item 物品
     * @param chargeableItem 蓄力行为实现
     */
    public static void register(Item item, ChargeableItem chargeableItem) {
        CHARGEABLE_ITEMS.put(item, chargeableItem);
    }

    /**
     * 检查物品是否支持蓄力
     * @param item 物品
     * @return 是否支持蓄力
     */
    public static boolean isChargeable(Item item) {
        return CHARGEABLE_ITEMS.containsKey(item);
    }

    /**
     * 获取物品的蓄力行为实现
     * @param item 物品
     * @return 蓄力行为实现，如果不存在则返回null
     */
    public static ChargeableItem getChargeable(Item item) {
        return CHARGEABLE_ITEMS.get(item);
    }

    /**
     * 检查物品堆栈是否支持蓄力
     * @param stack 物品堆栈
     * @return 是否支持蓄力
     */
    public static boolean isChargeableStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && isChargeable(stack.getItem());
    }

    /**
     * 获取物品的蓄力信息
     * @param stack 物品堆栈
     * @param player 玩家
     * @return 蓄力信息，如果物品不支持蓄力则返回null
     */
    public static ChargeInfo getChargeInfo(ItemStack stack, Player player) {
        if (!isChargeableStack(stack)) {
            return null;
        }

        ChargeableItem chargeable = getChargeable(stack.getItem());
        if (chargeable == null) {
            return null;
        }

        int maxChargeTime = chargeable.getMaxChargeTime(stack, player);
        int ticksUsingItem = player.getTicksUsingItem();
        float chargePercentage = chargeable.getChargePercentage(stack, player, ticksUsingItem);
        
        return new ChargeInfo(
            maxChargeTime,
            ticksUsingItem,
            chargePercentage,
            chargeable.getMaxStamina(stack, player),
            chargeable.hasSpecialVisualEffects(stack, player)
        );
    }

    /**
     * 调用蓄力完成回调
     * @param stack 物品堆栈
     * @param player 玩家
     */
    public static void onFullyCharged(ItemStack stack, Player player) {
        if (!isChargeableStack(stack)) {
            return;
        }

        ChargeableItem chargeable = getChargeable(stack.getItem());
        if (chargeable != null) {
            chargeable.onFullyCharged(stack, player);
        }
    }

    /**
     * 蓄力信息数据类
     */
    public static class ChargeInfo {
        public final int maxChargeTime;
        public final int currentTicksUsing;
        public final float chargePercentage;
        public final float maxStamina;
        public final boolean hasSpecialVisualEffects;

        public ChargeInfo(int maxChargeTime, int currentTicksUsing, float chargePercentage, 
                         float maxStamina, boolean hasSpecialVisualEffects) {
            this.maxChargeTime = maxChargeTime;
            this.currentTicksUsing = currentTicksUsing;
            this.chargePercentage = chargePercentage;
            this.maxStamina = maxStamina;
            this.hasSpecialVisualEffects = hasSpecialVisualEffects;
        }
    }
}