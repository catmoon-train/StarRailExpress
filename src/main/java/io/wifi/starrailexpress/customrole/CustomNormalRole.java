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

package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.network.CustomRoleClientNetwork;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持自定义商店和初始物品的自定义职业基类
 */
public class CustomNormalRole extends ExtraEffectRole {
    private List<ShopEntry> customShop;
    private List<ItemStack> defaultItems = List.of();

    public CustomNormalRole(ResourceLocation id, int color, boolean isInnocent, boolean canUseKiller,
            SRERole.MoodType mood, int maxSprintTime, boolean canSeeTime,
            ArrayList<MobEffectInstance> effects, List<ShopEntry> shop) {
        super(id, color, isInnocent, canUseKiller, mood, maxSprintTime, canSeeTime, effects);
        this.customShop = shop;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        if (customShop != null) {
            if (customShop.isEmpty()) {
                if (canUseKiller()) {
                    return ShopContent.getDefaultKnifeEntries();
                }
            }
            return customShop;
        }
        return super.getShopEntries();
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        if (defaultItems != null && !defaultItems.isEmpty())
            return defaultItems;
        return super.getDefaultItems();
    }

    public void setCustomShop(List<ShopEntry> shop) {
        this.customShop = shop;
    }

    public void setDefaultItems(List<ItemStack> items) {
        this.defaultItems = items;
    }

    private static String fixNewlines(String text) {
        return text.replace("\\n", "\n");
    }

    @Override
    public Component getName() {
        var id = this.identifier();
        var customData = CustomRoleLoader
                .getCustomRoleData(id.getPath());
        if (customData != null && !customData.displayName.isEmpty()) {
            return Component.literal(customData.displayName);
        }
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            // 服务端不可用时，回退到客户端网络同步的数据
            customData = CustomRoleClientNetwork
                    .getSyncedRole(id.getPath());
            if (customData != null && !customData.displayName.isEmpty()) {
                return Component.literal(customData.displayName);
            }
        }
        return Component.literal(id.toString());
    }

    @Override
    public Component getDescription() {
        var id = this.identifier();
        var cd = CustomRoleLoader.getCustomRoleData(id.getPath());
        if (cd != null && !cd.description.isEmpty()) {
            return Component.literal(fixNewlines(cd.description));
        }
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            cd = CustomRoleClientNetwork.getSyncedRole(id.getPath());
            if (cd != null && !cd.description.isEmpty()) {
                return Component.literal(fixNewlines(cd.description));
            }
        }
        return Component.literal(id.toString());

    }

    @Override
    public Component getSimpleDescription() {
        return getDescription();
    }

    @Override
    public boolean hasSimpleDescription() {
        return true;
    }

    @Override
    public Component getGoal() {
        var cd = CustomRoleLoader.getCustomRoleData(this.identifier().getPath());
        if (cd != null && !cd.goals.isEmpty())
            return Component.literal(cd.goals);
        return super.getGoal();
    }
}
