/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.index;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Decorative blocks used by map builders and themed scene layouts. */
public final class SREDecorationBlocks {

    public static final ResourceKey<CreativeModeTab> THEME_DECORATION_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, SRE.id("theme_decorations"));

    public static final Block WARNING_LINE = registerBlock("warning_line",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block MISSING_MATERIAL = registerBlock("missing_material",
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE));
    public static final Block LIGHT_BLUE_OAK_LOG = registerBlock("light_blue_oak_log",
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG));
    public static final Block LIGHT_BLUE_OAK_PLANKS = registerBlock("light_blue_oak_planks",
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));
    public static final Block MAGENTA_GRASS_BLOCK = registerBlock("magenta_grass_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK));
    public static final Block SIGNAL_LOST = registerBlock("signal_lost",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_CONCRETE));

    // SCP-inspired facility pieces: original, generic containment-facility styling.
    public static final Block SCP_REINFORCED_CONCRETE = registerBlock("scp_reinforced_concrete",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block SCP_CONTAINMENT_PANEL = registerBlock("scp_containment_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block SCP_REINFORCED_GLASS = registerBlock("scp_reinforced_glass",
            BlockBehaviour.Properties.ofFullCopy(Blocks.TINTED_GLASS));
    public static final Block SCP_CLEARANCE_STRIPE = registerBlock("scp_clearance_stripe",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block SCP_BREACH_SCREEN = registerBlock("scp_breach_screen",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_CONCRETE));

    private static final Item[] TAB_ITEMS = {
            registerBlockItem(WARNING_LINE),
            registerBlockItem(MISSING_MATERIAL),
            registerBlockItem(LIGHT_BLUE_OAK_LOG),
            registerBlockItem(LIGHT_BLUE_OAK_PLANKS),
            registerBlockItem(MAGENTA_GRASS_BLOCK),
            registerBlockItem(SIGNAL_LOST),
            registerBlockItem(SCP_REINFORCED_CONCRETE),
            registerBlockItem(SCP_CONTAINMENT_PANEL),
            registerBlockItem(SCP_REINFORCED_GLASS),
            registerBlockItem(SCP_CLEARANCE_STRIPE),
            registerBlockItem(SCP_BREACH_SCREEN)
    };

    private SREDecorationBlocks() {
    }

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, THEME_DECORATION_TAB,
                FabricItemGroup.builder()
                        .title(Component.translatable("item_group.starrailexpress.theme_decorations"))
                        .icon(() -> new ItemStack(WARNING_LINE))
                        .build());

        ItemGroupEvents.modifyEntriesEvent(THEME_DECORATION_TAB).register(entries -> {
            for (Item item : TAB_ITEMS) {
                entries.accept(item);
            }
        });
    }

    private static Block registerBlock(String id, BlockBehaviour.Properties properties) {
        return Registry.register(BuiltInRegistries.BLOCK, SRE.id(id), new Block(properties));
    }

    private static Item registerBlockItem(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        BlockItem item = new BlockItem(block, new Item.Properties());
        item.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }
}
