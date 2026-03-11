package io.wifi.starrailexpress.index;

import java.util.function.BiConsumer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ReplaceableItems {
    public Item LETTER;
    public BiConsumer<ItemStack, ServerPlayer> LETTER_UpdateItemFunc;
}
