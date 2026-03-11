package io.wifi.starrailexpress.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 基础可换肤物品类，实现ItemWithSkin接口
 */
public class BaseSkinnableItem extends Item implements ItemWithSkin {
    
    public BaseSkinnableItem(Properties settings) {
        super(settings);
    }
    
    @Override
    public String getDefaultSkin() {
        return "default";
    }
    
    @Override
    public String[] getAvailableSkins() {
        return new String[]{"default"};
    }
    
    /**
     * 获取物品的皮肤标识符
     * @param stack 物品堆栈
     * @return 皮肤标识符
     */
    public String getSkin(ItemStack stack) {
        // 这里可以通过NBT或其他方式获取皮肤信息
        // 为了兼容现有代码，我们返回默认值
        return getDefaultSkin();
    }
}