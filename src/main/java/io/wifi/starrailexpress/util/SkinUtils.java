package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.SRECosmetics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * 皮肤系统工具类，提供统一的皮肤操作接口
 */
public class SkinUtils {
    
    /**
     * 设置物品皮肤
     * @param player 玩家
     * @param itemStack 物品堆栈
     * @param skinName 皮肤名称
     */
    public static void setItemSkin(Player player, ItemStack itemStack, String skinName) {
        if (itemStack.getItem() instanceof io.wifi.starrailexpress.item.SkinableItem) {
            SRECosmetics.setSkin(player, itemStack, skinName);
            // 同时更新玩家皮肤组件
            SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
            String itemTypeName = getItemTypeName(itemStack);
            skinsComponent.setEquippedSkinForItemType(itemTypeName, skinName);
            // 更新数据同步
            skinsComponent.setSkinInDataSync(itemStack, skinName);
        }
    }
    
    /**
     * 检查物品是否支持皮肤系统
     * @param itemStack 物品堆栈
     * @return 是否支持皮肤
     */
    public static boolean isItemSkinnable(ItemStack itemStack) {
        return itemStack.getItem() instanceof io.wifi.starrailexpress.item.SkinableItem;
    }
    
    /**
     * 获取玩家的皮肤统计信息
     * @param player 玩家
     * @return 皮肤统计信息字符串
     */
    public static String getPlayerSkinStats(Player player) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        Map<String, String> equippedSkins = skinsComponent.getEquippedSkins();
        Map<String, Map<String, Boolean>> unlockedSkins = skinsComponent.getUnlockedSkins();
        
        int totalEquipped = equippedSkins.size();
        int totalUnlocked = 0;
        
        for (Map<String, Boolean> itemSkins : unlockedSkins.values()) {
            for (Boolean unlocked : itemSkins.values()) {
                if (unlocked) {
                    totalUnlocked++;
                }
            }
        }
        
        return String.format("Equipped: %d, Unlocked: %d", totalEquipped, totalUnlocked);
    }
    
    /**
     * 重置玩家的所有皮肤设置
     * @param player 玩家
     */
    public static void resetPlayerSkins(Player player) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.getEquippedSkins().clear();
        skinsComponent.getUnlockedSkins().clear();
    }
    
    /**
     * 从物品堆栈获取物品类型名称
     * @param itemStack 物品堆栈
     * @return 物品类型名称
     */
    public static String getItemTypeName(ItemStack itemStack) {
        Item item = itemStack.getItem();
        String itemId = BuiltInRegistries.ITEM.getKey(item).getPath();
        return itemId.toLowerCase();
    }
}