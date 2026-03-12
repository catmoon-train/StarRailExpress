package io.wifi.starrailexpress.util;

import java.util.function.Predicate;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// Author: wifi_left
public class TMMItemUtils {
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

    public static int hasItem(Player player, Predicate<ItemStack> predicate) {
        int result = player.getInventory().clearOrCountMatchingItems(predicate, 0,
                player.inventoryMenu.getCraftSlots());
        // player.containerMenu.broadcastChanges();
        // player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int hasItem(Player player, Item item) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, 0,
                player.inventoryMenu.getCraftSlots());
        // player.containerMenu.broadcastChanges();
        // player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

    public static int hasItem(Player player, TagKey<Item> item) {
        Predicate<ItemStack> predicate = (itemStack) -> {
            return itemStack.is(item);
        };
        int result = player.getInventory().clearOrCountMatchingItems(predicate, 0,
                player.inventoryMenu.getCraftSlots());
        // player.containerMenu.broadcastChanges();
        // player.inventoryMenu.slotsChanged(player.getInventory());
        return result;
    }

}
