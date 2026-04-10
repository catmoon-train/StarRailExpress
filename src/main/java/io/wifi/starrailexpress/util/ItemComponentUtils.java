package io.wifi.starrailexpress.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ItemComponentUtils {
    public static void setTagIntValue(ItemStack stack, String key, int value) {
        // 获取现有的自定义数据
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag;

        if (customData != null) {
            // 复制现有数据
            tag = customData.copyTag();
        } else {
            tag = new CompoundTag();
        }
        tag.putInt(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // 从 ItemStack 读取
    public static int getTagIntValue(ItemStack stack, String key) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(key)) {
                return tag.getInt(key);
            }
        }
        return 0;
    }
}
