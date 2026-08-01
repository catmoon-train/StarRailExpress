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
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

// Author: wifi_left
public class SREItemUtils {
    public static int clearItem(Player player, Item item) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, -1,
                player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int clearItem(Player player, TagKey<Item> item) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, -1,
                player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int clearItem(Player player, Item item, int count) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, count,
                player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int clearItem(Player player, TagKey<Item> item, int count) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, count,
                player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int clearItem(Player player) {
        int result = player.getInventory().clearOrCountMatchingItems((t) -> true, -1,
                player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int clearItem(Player player, Predicate<ItemStack> predicate) {
        int result = player.getInventory().clearOrCountMatchingItems(predicate, -1,
                player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int clearItem(Player player, Predicate<ItemStack> predicate, int count) {
        int result = player.getInventory().clearOrCountMatchingItems(predicate, count,
                player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static boolean hasItem(Player player, Predicate<ItemStack> predicate) {
        int result = player.getInventory().clearOrCountMatchingItems(predicate, 0,
                player.inventoryMenu.getCraftSlots());
        // player.containerMenu.broadcastChanges();
        // player.inventoryMenu.slotsChanged(player.getInventory());
        return result > 0;
    }

    public static boolean hasItem(Player player, Item item) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            if (itemStack.getItem() == Items.BUNDLE) {
                BundleContents bundleContents = itemStack.get(DataComponents.BUNDLE_CONTENTS);
                if (bundleContents != null) {
                    return bundleContents.itemCopyStream().anyMatch(itemStack1 -> itemStack1.is(item));
                }
            }
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, 0,
                player.inventoryMenu.getCraftSlots());
        // player.containerMenu.broadcastChanges();
        // player.inventoryMenu.slotsChanged(player.getInventory());
        return result > 0;
    }

    public static boolean hasItem(Player player, TagKey<Item> item) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            if (itemStack.getItem() == Items.BUNDLE) {
                BundleContents bundleContents = itemStack.get(DataComponents.BUNDLE_CONTENTS);
                if (bundleContents != null) {
                    return bundleContents.itemCopyStream().anyMatch(itemStack1 -> itemStack1.is(item));
                }
            }
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, 0,
                player.inventoryMenu.getCraftSlots());
        // player.containerMenu.broadcastChanges();
        // player.inventoryMenu.slotsChanged(player.getInventory());
        return result > 0;
    }

    public static int countItem(Player player, Predicate<ItemStack> predicate) {
        int result = player.getInventory().clearOrCountMatchingItems(predicate, 0,
                player.inventoryMenu.getCraftSlots());
        // player.containerMenu.broadcastChanges();
        // player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int countItem(Player player, Item item) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, 0,
                player.inventoryMenu.getCraftSlots());
        // player.containerMenu.broadcastChanges();
        // player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int countItem(Player player, TagKey<Item> item) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, 0,
                player.inventoryMenu.getCraftSlots());
        // player.containerMenu.broadcastChanges();
        // player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static boolean insertStackInFreeSlot(@NotNull Player player, ItemStack sstack) {
        var stackToInsert = sstack.copy();
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stackToInsert, stack)) {
                int leftCount = 0;
                leftCount = stackToInsert.getCount() + stack.getCount() - stack.getMaxStackSize();

                if (leftCount <= 0) {
                    stack.setCount(stack.getCount() + stackToInsert.getCount());
                } else {
                    stack.setCount(stack.getMaxStackSize());
                }
                player.getInventory().setItem(i, stack);
                if (leftCount <= 0)
                    return true;
                else
                    stackToInsert.setCount(leftCount);
            }
        }
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                player.getInventory().setItem(i, stackToInsert);
                return true;
            }
        }
        return false;
    }

    public static int getHotbarFreeSlot(@NotNull Player player) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static boolean hasHotbarFreeSlot(@NotNull Player player) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
