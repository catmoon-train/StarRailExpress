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

package io.wifi.starrailexpress.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ItemComponentUtils {
    public static void setCustomDataTagIntValue(ItemStack stack, String key, int value) {
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
    public static int getCustomDataTagIntValue(ItemStack stack, String key) {
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
