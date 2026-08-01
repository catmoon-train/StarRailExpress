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
import net.minecraft.ChatFormatting;
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
        String itemName = this.getItemSkinType();
        if (itemName == null)
            return;
        Player player = null;
        if (tooltipContext instanceof Player p) {
            player = p;
        } else {
            player = null;
        }
        if (player == null) {
            if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
                player = SREClientUtils.getClientPlayer();
                if (player == null)
                    return;
            }
        }

        String skinName = "default";
        skinName = ItemSkinManager.getEquippedSkin(player, itemStack);
        if (skinName == null) {
            skinName = "default";
        }
        ItemSkinManager.Skin skin = ItemSkinManager.Skin.fromString(itemName, skinName);

        if (skin != null) {
            list.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.translatableWithFallback(
                            "screen.sre.skins." + itemName + "." + (skin.tooltipName) + ".name", skin.tooltipName)
                            .withStyle(style -> style.withColor(skin.getColor()))));
        } else if (skinName.equals("default") || skinName == null) {
            list.add(Component.translatable("tip.skin").withStyle(style -> style.withColor(Colors.GRAY))
                    .append(Component.translatableWithFallback("screen.sre.skins.default", "Default"))
                    .withStyle(ChatFormatting.GRAY));
        }
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
            if (itemStack.get(SREDataComponentTypes.SKIN) == null) {
                itemStack.set(SREDataComponentTypes.SKIN, ItemSkinManager.getEquippedSkin(player, itemStack));
            }
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
}
