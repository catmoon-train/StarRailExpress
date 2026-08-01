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

package io.wifi.starrailexpress.index;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import static io.wifi.starrailexpress.cca.SREPlayerSkinsComponent.KEY;

public interface SRECosmetics {
    // 不再重复注册，而是使用PlayerSkinsComponent中已注册的实例

    static String getSkin(ItemStack itemStack) {
        // 获取物品的owner NBT数据，如果没有则使用默认UUID
        String skin = itemStack.getOrDefault(SREDataComponentTypes.SKIN, "default");
        return skin;
    }

    static void setSkin(Player player, ItemStack itemStack, String skinName) {
        // 只有上传数据在客户端，服务器不能datasync
        final var playerSkinsComponent = KEY.get(player);
        playerSkinsComponent.getEquippedSkins().put(BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath(),
                skinName);
        playerSkinsComponent.sync();
    }
}