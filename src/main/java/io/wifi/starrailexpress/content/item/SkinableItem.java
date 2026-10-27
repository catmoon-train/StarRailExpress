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

package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.client.util.SREClientUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 可切换皮肤的物品
 * 实现此接口的物品可以在皮肤管理界面中进行皮肤更换
 */
public abstract class SkinableItem extends Item {
    public SkinableItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        Player player = tooltipContext instanceof Player p ? p : null;
        if (player == null && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            player = SREClientUtils.getClientPlayer();
        }
        ItemSkinManager.appendSkinTooltip(itemStack, list, player);
        super.appendHoverText(itemStack, tooltipContext, list, tooltipFlag);
    }

    public abstract String getItemSkinType();

    /**
     * 获取物品的默认皮肤名称
     * 
     * @return 默认皮肤名称
     */
    public String getDefaultSkin() {
        return "default";
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (entity instanceof Player player) {
            ItemSkinManager.applyEquippedSkin(player, itemStack);
        }
    }

    /**
     * 获取物品支持的皮肤列表
     * 
     * @return 皮肤名称数组
     */
    public String[] getAvailableSkins() {
        return new String[] { "default" };
    }

    public ItemStack getDefaultInstanceForRenderer(Player player) {
        ItemStack stack = this.getDefaultInstance();
        if (stack.get(SREDataComponentTypes.SKIN) == null) {
            stack.set(SREDataComponentTypes.SKIN, ItemSkinManager.getEquippedSkin(player, stack));
        }
        return stack;
    }
}
