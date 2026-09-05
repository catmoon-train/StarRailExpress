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

package io.wifi.starrailexpress.client.util;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.RoleInitialItems;
import org.agmas.noellesroles.init.RoleShopHandler;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品 → 商店职业 / 开局发放职业。随 {@link RoleShopHandler#shopRegister()} 版本号重建。
 */
public final class ShopItemTooltipIndex {
    private static int builtVersion = -1;
    private static Map<Item, List<SRERole>> shopRolesByItem = Map.of();
    private static Map<Item, List<SRERole>> startRolesByItem = Map.of();

    private ShopItemTooltipIndex() {
    }

    public static List<SRERole> rolesFor(Item item) {
        return lookupAfterBuild(item, true);
    }

    public static List<SRERole> startingRolesFor(Item item) {
        return lookupAfterBuild(item, false);
    }

    private static List<SRERole> lookupAfterBuild(Item item, boolean shop) {
        if (item == null || item == Items.AIR) {
            return List.of();
        }
        ensureBuilt();
        List<SRERole> roles = (shop ? shopRolesByItem : startRolesByItem).get(item);
        return roles == null ? List.of() : roles;
    }

    private static void ensureBuilt() {
        int version = RoleShopHandler.shopVersion;
        if (builtVersion == version && (!shopRolesByItem.isEmpty() || !startRolesByItem.isEmpty())) {
            return;
        }
        builtVersion = version;
        Map<Item, List<SRERole>> shop = new IdentityHashMap<>();
        Map<Item, List<SRERole>> start = new IdentityHashMap<>();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (role == null) {
                continue;
            }
            indexShop(role, shop);
            indexStarting(role, start);
        }
        sortRoles(shop);
        sortRoles(start);
        shopRolesByItem = shop;
        startRolesByItem = start;
    }

    private static void indexShop(SRERole role, Map<Item, List<SRERole>> target) {
        List<ShopEntry> entries;
        try {
            entries = ShopContent.getShopEntries(role, null);
        } catch (Exception ignored) {
            return;
        }
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (ShopEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            ItemStack stack;
            try {
                stack = entry.stack();
            } catch (Exception ignored) {
                continue;
            }
            addRole(target, stack, role);
        }
    }

    private static void indexStarting(SRERole role, Map<Item, List<SRERole>> target) {
        List<ItemStack> items;
        try {
            items = RoleInitialItems.getInitialItemsForRole(role);
        } catch (Exception ignored) {
            return;
        }
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ItemStack stack : items) {
            addRole(target, stack, role);
        }
    }

    private static void addRole(Map<Item, List<SRERole>> target, ItemStack stack, SRERole role) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item == Items.AIR || item == Items.BARRIER) {
            return;
        }
        List<SRERole> list = target.computeIfAbsent(item, unused -> new ArrayList<>());
        if (!list.contains(role)) {
            list.add(role);
        }
    }

    private static void sortRoles(Map<Item, List<SRERole>> map) {
        Comparator<SRERole> byName = Comparator.comparing(
                role -> RoleUtils.getRoleName(role).getString(), String.CASE_INSENSITIVE_ORDER);
        for (List<SRERole> list : map.values()) {
            list.sort(byName);
        }
    }
}
