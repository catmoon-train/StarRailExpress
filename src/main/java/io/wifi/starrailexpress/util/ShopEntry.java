package io.wifi.starrailexpress.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.SRE;

public class ShopEntry {
    public final ItemStack stack;
    public final int price;
    public final Type type;

    public enum Type {
        WEAPON("gui/shop_slot_weapon"),
        POISON("gui/shop_slot_poison"),
        TOOL("gui/shop_slot_tool");

        final ResourceLocation texture;

        Type(String texture) {
            this.texture = SRE.watheId(texture);
        }

        public ResourceLocation getTexture() {
            return texture;
        }
    }

    public ShopEntry(ItemStack stack, int price, Type type) {
        this.stack = stack;
        this.price = price;
        this.type = type;
    }

    public boolean canBuy(@NotNull Player player) {
        return true;
    }

    public boolean onBuy(@NotNull Player player) {
        return insertStackInFreeSlot(player, this.stack.copy());
    }

    public static boolean insertStackInFreeSlot(@NotNull Player player, ItemStack stackToInsert) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                player.getInventory().setItem(i, stackToInsert);
                return true;
            }
        }
        return false;
    }

    public ItemStack stack() {
        return this.stack;
    }

    public int price() {
        return this.price;
    }

    public Type type() {
        return type;
    }
}