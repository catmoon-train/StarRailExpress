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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.core.Direction;

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

    public static final Block LIGHT_BLUE_OAK_STAIRS = registerBlock("light_blue_oak_stairs",
            new StairBlock(LIGHT_BLUE_OAK_PLANKS.defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(LIGHT_BLUE_OAK_PLANKS)));
    public static final Block LIGHT_BLUE_OAK_SLAB = registerBlock("light_blue_oak_slab",
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(LIGHT_BLUE_OAK_PLANKS)));
    public static final Block LIGHT_BLUE_OAK_FENCE = registerBlock("light_blue_oak_fence",
            new FenceBlock(BlockBehaviour.Properties.ofFullCopy(LIGHT_BLUE_OAK_PLANKS)));
    public static final Block LIGHT_BLUE_OAK_DOOR = registerBlock("light_blue_oak_door",
            new DoorBlock(BlockSetType.OAK, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_DOOR)));
    public static final Block LIGHT_BLUE_DIRT_PATH = registerBlock("light_blue_dirt_path",
            new DirtPathBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT_PATH)));

    public static final Block BLUE_TORCH = registerBlock("blue_torch",
            new TorchBlock(ParticleTypes.SOUL_FIRE_FLAME, BlockBehaviour.Properties.ofFullCopy(Blocks.TORCH)));
    public static final Block WALL_BLUE_TORCH = registerBlock("wall_blue_torch",
            new WallTorchBlock(ParticleTypes.SOUL_FIRE_FLAME,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WALL_TORCH)));
    public static final Block BLACK_TALL_GRASS = registerBlock("black_tall_grass",
            new DoublePlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS)));
    public static final Block LIGHT_BLUE_FLOWERING_AZALEA = registerBlock("light_blue_flowering_azalea",
            new AzaleaBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FLOWERING_AZALEA)));
    public static final Block LIGHT_PURPLE_SPORE_BLOCK = registerBlock("light_purple_spore_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.MOSS_BLOCK));

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
            registerBlockItem(LIGHT_BLUE_OAK_STAIRS),
            registerBlockItem(LIGHT_BLUE_OAK_SLAB),
            registerBlockItem(LIGHT_BLUE_OAK_FENCE),
            registerBlockItem(LIGHT_BLUE_OAK_DOOR),
            registerBlockItem(LIGHT_BLUE_DIRT_PATH),
            registerStandingAndWallItem(BLUE_TORCH, WALL_BLUE_TORCH),
            registerBlockItem(BLACK_TALL_GRASS),
            registerBlockItem(LIGHT_BLUE_FLOWERING_AZALEA),
            registerBlockItem(LIGHT_PURPLE_SPORE_BLOCK),
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

    private static <T extends Block> T registerBlock(String id, T block) {
        return Registry.register(BuiltInRegistries.BLOCK, SRE.id(id), block);
    }

    private static Item registerBlockItem(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        BlockItem item = new BlockItem(block, new Item.Properties());
        item.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    private static Item registerStandingAndWallItem(Block standingBlock, Block wallBlock) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(standingBlock);
        StandingAndWallBlockItem item = new StandingAndWallBlockItem(
                standingBlock, wallBlock, new Item.Properties(), Direction.DOWN);
        item.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }
}
