package io.wifi.starrailexpress.index;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModBlocks;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;

public interface SREItems {
    // 新式注册物品。
    public static final ItemRegistrar registrar = new ItemRegistrar(Noellesroles.MOD_ID);

    // 古法注册物品。主要包含一个物品对应多个方块的情况
    Item TRAIN_TORCH = registerBlock(
            new StandingAndWallBlockItem(SREBlocks.TRAIN_TORCH, SREBlocks.WALL_TRAIN_TORCH, new Item.Properties(),
                    Direction.DOWN));

    public static Item registerBlock(BlockItem blockItem) {
        return registerBlock(blockItem.getBlock(), blockItem);
    }

    public static Item registerBlock(Block block, Item item) {
        return registerItem(BuiltInRegistries.BLOCK.getKey(block), item);
    }

    public static Item registerItem(String string, Item item) {
        return registerItem(ResourceLocation.tryParse(string), item);
    }

    public static Item registerItem(ResourceLocation resourceLocation, Item item) {
        return registerItem(ResourceKey.create(BuiltInRegistries.ITEM.key(), resourceLocation), item);
    }

    public static Item registerItem(ResourceKey<Item> resourceKey, Item item) {
        if (item instanceof BlockItem bi) {
            bi.registerBlocks(Item.BY_BLOCK, item);
        }
        return Registry.register(BuiltInRegistries.ITEM, resourceKey, item);
    }

    @SafeVarargs
    public static Item register(Item item, String id, ResourceKey<CreativeModeTab>... extraGroups) {
        ResourceKey<CreativeModeTab>[] allGroups = java.util.Arrays.copyOf(extraGroups, extraGroups.length + 1);
        allGroups[extraGroups.length] = TMMItems.SRE_ALL_GROUP;
        var registeredItem = registrar.create(id, item, allGroups);
        TMMDescItems.introItems.add(registeredItem);

        return registeredItem;
    }

    public static void init() {
        // 修改物品分类
        ItemGroupEvents.modifyEntriesEvent(ModBlocks.BLOCK_CREATIVE_GROUP)
                .register((itemGroup) -> itemGroup.accept(TRAIN_TORCH));
    }
}
