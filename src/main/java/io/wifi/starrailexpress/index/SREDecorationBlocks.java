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
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;

/** Decorative blocks used by map builders and themed scene layouts. */
public final class SREDecorationBlocks {

    public static final ResourceKey<CreativeModeTab> THEME_DECORATION_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, SRE.id("theme_decorations"));

    public static final Block WARNING_LINE = registerBlock("warning_line",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block MISSING_MATERIAL = registerBlock("missing_material",
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE));
    public static final Block LIGHT_BLUE_OAK_LOG = registerBlock("light_blue_oak_log",
            new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)));
    public static final Block LIGHT_BLUE_OAK_PLANKS = registerBlock("light_blue_oak_planks",
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));
    public static final Block DEEP_BLUE_OAK_PLANKS = registerBlock("deep_blue_oak_planks",
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));
    public static final Block DEEP_BLUE_BRICKS = registerBlock("deep_blue_bricks",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS));
    public static final Block PURE_BLUE_BLOCK = registerBlock("pure_blue_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_CONCRETE));
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
    public static final Block DEEP_BLUE_OAK_STAIRS = registerBlock("deep_blue_oak_stairs",
            new StairBlock(DEEP_BLUE_OAK_PLANKS.defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(DEEP_BLUE_OAK_PLANKS)));
    public static final Block DEEP_BLUE_OAK_SLAB = registerBlock("deep_blue_oak_slab",
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(DEEP_BLUE_OAK_PLANKS)));
    public static final Block DEEP_BLUE_OAK_FENCE = registerBlock("deep_blue_oak_fence",
            new FenceBlock(BlockBehaviour.Properties.ofFullCopy(DEEP_BLUE_OAK_PLANKS)));
    public static final Block DEEP_BLUE_BRICK_STAIRS = registerBlock("deep_blue_brick_stairs",
            new StairBlock(DEEP_BLUE_BRICKS.defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(DEEP_BLUE_BRICKS)));
    public static final Block DEEP_BLUE_BRICK_SLAB = registerBlock("deep_blue_brick_slab",
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(DEEP_BLUE_BRICKS)));
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
    public static final Block PURPLE_POPPY = registerBlock("purple_poppy",
            new FlowerBlock(MobEffects.NIGHT_VISION, 5.0f,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY)));
    public static final Block GRAY_BLUE_OBSIDIAN = registerBlock("gray_blue_obsidian",
            BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN));
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

    // Newly drawn containment-facility materials, kept separate from the original SCP set.
    public static final Block SCP_SECURE_FLOOR = registerBlock("scp_secure_floor",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block SCP_CLEANROOM_TILE = registerBlock("scp_cleanroom_tile",
            BlockBehaviour.Properties.ofFullCopy(Blocks.QUARTZ_BLOCK));
    public static final Block SCP_CEILING_PANEL = registerBlock("scp_ceiling_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block SCP_AIRLOCK_PANEL = registerBlock("scp_airlock_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block SCP_BULKHEAD_PANEL = registerBlock("scp_bulkhead_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block SCP_ACCESS_CONTROL_PANEL = registerBlock("scp_access_control_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block SCP_CONTROL_CONSOLE = registerBlock("scp_control_console",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_CONCRETE));
    public static final Block SCP_OBSERVATION_SCREEN = registerBlock("scp_observation_screen",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_CONCRETE));
    public static final Block SCP_LOCKDOWN_PANEL = registerBlock("scp_lockdown_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.RED_CONCRETE));
    public static final Block SCP_QUARANTINE_PANEL = registerBlock("scp_quarantine_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.ORANGE_CONCRETE));
    public static final Block SCP_BIOHAZARD_PANEL = registerBlock("scp_biohazard_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GREEN_CONCRETE));
    public static final Block SCP_RADIATION_PANEL = registerBlock("scp_radiation_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block SCP_REACTOR_PANEL = registerBlock("scp_reactor_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_CONCRETE));
    public static final Block SCP_RESEARCH_PANEL = registerBlock("scp_research_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.PURPLE_CONCRETE));
    public static final Block SCP_MAINTENANCE_GRATE = registerBlock("scp_maintenance_grate",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block SCP_VENT_GRILLE = registerBlock("scp_vent_grille",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block SCP_CABLE_TRAY = registerBlock("scp_cable_tray",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block SCP_ACOUSTIC_PANEL = registerBlock("scp_acoustic_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block SCP_SEALED_BLAST_DOOR = registerBlock("scp_sealed_blast_door",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block SCP_FOUNDATION_BRICK = registerBlock("scp_foundation_brick",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS));
    public static final Block SCP_CONTAINMENT_FOAM = registerBlock("scp_containment_foam",
            BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_CONCRETE));
    public static final Block SCP_EMERGENCY_LIGHT_PANEL = registerBlock("scp_emergency_light_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.RED_CONCRETE));
    public static final Block SCP_TEST_CHAMBER_PANEL = registerBlock("scp_test_chamber_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.PURPLE_CONCRETE));
    public static final Block SCP_SEALED_BULKHEAD = registerBlock("scp_sealed_bulkhead",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block BLUE_SIGNAL_LOST = registerBlock("blue_signal_lost",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_CONCRETE));

    // Backrooms-themed materials: a separate set of newly drawn liminal facility textures.
    public static final Block BACKROOMS_YELLOW_WALLPAPER = registerBlock("backrooms_yellow_wallpaper",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block BACKROOMS_STAINED_WALLPAPER = registerBlock("backrooms_stained_wallpaper",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block BACKROOMS_OFFICE_CARPET = registerBlock("backrooms_office_carpet",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BROWN_CONCRETE));
    public static final Block BACKROOMS_WET_CONCRETE = registerBlock("backrooms_wet_concrete",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block BACKROOMS_FLUORESCENT_CEILING = registerBlock("backrooms_fluorescent_ceiling",
            BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHT_GRAY_CONCRETE));
    public static final Block BACKROOMS_EXPOSED_CEILING = registerBlock("backrooms_exposed_ceiling",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block BACKROOMS_YELLOW_PIPES = registerBlock("backrooms_yellow_pipes",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block BACKROOMS_SERVICE_DOOR = registerBlock("backrooms_service_door",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block BACKROOMS_EMERGENCY_PANEL = registerBlock("backrooms_emergency_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.RED_CONCRETE));
    public static final Block BACKROOMS_HAZARD_FLOOR = registerBlock("backrooms_hazard_floor",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block BACKROOMS_MOLDY_PLASTER = registerBlock("backrooms_moldy_plaster",
            BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHT_GRAY_CONCRETE));
    public static final Block BACKROOMS_PEELING_WALLPAPER = registerBlock("backrooms_peeling_wallpaper",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block BACKROOMS_WALL_CLOCK = registerBlock("backrooms_wall_clock",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block BACKROOMS_CUBICLE_PANEL = registerBlock("backrooms_cubicle_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block BACKROOMS_ELEVATOR_PANEL = registerBlock("backrooms_elevator_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block BACKROOMS_UTILITY_BRICK = registerBlock("backrooms_utility_brick",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_CONCRETE));
    public static final Block BACKROOMS_DAMAGED_ACOUSTIC = registerBlock("backrooms_damaged_acoustic",
            BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_CONCRETE));
    public static final Block BACKROOMS_FLICKERING_LIGHT = registerBlock("backrooms_flickering_light",
            BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHT_GRAY_CONCRETE));
    public static final Block BACKROOMS_FILING_CABINET = registerBlock("backrooms_filing_cabinet",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block BACKROOMS_SHADOW_DOORWAY = registerBlock("backrooms_shadow_doorway",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_CONCRETE));
    public static final Block BACKROOMS_RED_CARPET = registerBlock("backrooms_red_carpet",
            BlockBehaviour.Properties.ofFullCopy(Blocks.RED_CONCRETE));
    public static final Block BACKROOMS_BROWN_CARPET = registerBlock("backrooms_brown_carpet",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BROWN_CONCRETE));
    public static final Block BACKROOMS_DAMP_CEILING = registerBlock("backrooms_damp_ceiling",
            BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHT_GRAY_CONCRETE));
    public static final Block BACKROOMS_WATER_STAINED_TILE = registerBlock("backrooms_water_stained_tile",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block BACKROOMS_YELLOW_METAL_PANEL = registerBlock("backrooms_yellow_metal_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));
    public static final Block BACKROOMS_RUST_VENT = registerBlock("backrooms_rust_vent",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block BACKROOMS_SERVER_RACK = registerBlock("backrooms_server_rack",
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_CONCRETE));
    public static final Block BACKROOMS_STAIRWELL_WALL = registerBlock("backrooms_stairwell_wall",
            BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE));
    public static final Block BACKROOMS_DIRTY_LOCKER = registerBlock("backrooms_dirty_locker",
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Block BACKROOMS_BLACK_MOLD_PANEL = registerBlock("backrooms_black_mold_panel",
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE));

    private static final Item[] TAB_ITEMS = {
            registerBlockItem(WARNING_LINE),
            registerBlockItem(MISSING_MATERIAL),
            registerBlockItem(LIGHT_BLUE_OAK_LOG),
            registerBlockItem(LIGHT_BLUE_OAK_PLANKS),
            registerBlockItem(DEEP_BLUE_OAK_PLANKS),
            registerBlockItem(DEEP_BLUE_BRICKS),
            registerBlockItem(PURE_BLUE_BLOCK),
            registerBlockItem(MAGENTA_GRASS_BLOCK),
            registerBlockItem(SIGNAL_LOST),
            registerBlockItem(LIGHT_BLUE_OAK_STAIRS),
            registerBlockItem(LIGHT_BLUE_OAK_SLAB),
            registerBlockItem(LIGHT_BLUE_OAK_FENCE),
            registerBlockItem(DEEP_BLUE_OAK_STAIRS),
            registerBlockItem(DEEP_BLUE_OAK_SLAB),
            registerBlockItem(DEEP_BLUE_OAK_FENCE),
            registerBlockItem(DEEP_BLUE_BRICK_STAIRS),
            registerBlockItem(DEEP_BLUE_BRICK_SLAB),
            registerBlockItem(LIGHT_BLUE_OAK_DOOR),
            registerBlockItem(LIGHT_BLUE_DIRT_PATH),
            registerStandingAndWallItem(BLUE_TORCH, WALL_BLUE_TORCH),
            registerBlockItem(BLACK_TALL_GRASS),
            registerBlockItem(LIGHT_BLUE_FLOWERING_AZALEA),
            registerBlockItem(PURPLE_POPPY),
            registerBlockItem(GRAY_BLUE_OBSIDIAN),
            registerBlockItem(LIGHT_PURPLE_SPORE_BLOCK),
            registerBlockItem(SCP_REINFORCED_CONCRETE),
            registerBlockItem(SCP_CONTAINMENT_PANEL),
            registerBlockItem(SCP_REINFORCED_GLASS),
            registerBlockItem(SCP_CLEARANCE_STRIPE),
            registerBlockItem(SCP_BREACH_SCREEN),
            registerBlockItem(SCP_SECURE_FLOOR),
            registerBlockItem(SCP_CLEANROOM_TILE),
            registerBlockItem(SCP_CEILING_PANEL),
            registerBlockItem(SCP_AIRLOCK_PANEL),
            registerBlockItem(SCP_BULKHEAD_PANEL),
            registerBlockItem(SCP_ACCESS_CONTROL_PANEL),
            registerBlockItem(SCP_CONTROL_CONSOLE),
            registerBlockItem(SCP_OBSERVATION_SCREEN),
            registerBlockItem(SCP_LOCKDOWN_PANEL),
            registerBlockItem(SCP_QUARANTINE_PANEL),
            registerBlockItem(SCP_BIOHAZARD_PANEL),
            registerBlockItem(SCP_RADIATION_PANEL),
            registerBlockItem(SCP_REACTOR_PANEL),
            registerBlockItem(SCP_RESEARCH_PANEL),
            registerBlockItem(SCP_MAINTENANCE_GRATE),
            registerBlockItem(SCP_VENT_GRILLE),
            registerBlockItem(SCP_CABLE_TRAY),
            registerBlockItem(SCP_ACOUSTIC_PANEL),
            registerBlockItem(SCP_SEALED_BLAST_DOOR),
            registerBlockItem(SCP_FOUNDATION_BRICK),
            registerBlockItem(SCP_CONTAINMENT_FOAM),
            registerBlockItem(SCP_EMERGENCY_LIGHT_PANEL),
            registerBlockItem(SCP_TEST_CHAMBER_PANEL),
            registerBlockItem(SCP_SEALED_BULKHEAD),
            registerBlockItem(BLUE_SIGNAL_LOST),
            registerBlockItem(BACKROOMS_YELLOW_WALLPAPER),
            registerBlockItem(BACKROOMS_STAINED_WALLPAPER),
            registerBlockItem(BACKROOMS_OFFICE_CARPET),
            registerBlockItem(BACKROOMS_WET_CONCRETE),
            registerBlockItem(BACKROOMS_FLUORESCENT_CEILING),
            registerBlockItem(BACKROOMS_EXPOSED_CEILING),
            registerBlockItem(BACKROOMS_YELLOW_PIPES),
            registerBlockItem(BACKROOMS_SERVICE_DOOR),
            registerBlockItem(BACKROOMS_EMERGENCY_PANEL),
            registerBlockItem(BACKROOMS_HAZARD_FLOOR),
            registerBlockItem(BACKROOMS_MOLDY_PLASTER),
            registerBlockItem(BACKROOMS_PEELING_WALLPAPER),
            registerBlockItem(BACKROOMS_WALL_CLOCK),
            registerBlockItem(BACKROOMS_CUBICLE_PANEL),
            registerBlockItem(BACKROOMS_ELEVATOR_PANEL),
            registerBlockItem(BACKROOMS_UTILITY_BRICK),
            registerBlockItem(BACKROOMS_DAMAGED_ACOUSTIC),
            registerBlockItem(BACKROOMS_FLICKERING_LIGHT),
            registerBlockItem(BACKROOMS_FILING_CABINET),
            registerBlockItem(BACKROOMS_SHADOW_DOORWAY),
            registerBlockItem(BACKROOMS_RED_CARPET),
            registerBlockItem(BACKROOMS_BROWN_CARPET),
            registerBlockItem(BACKROOMS_DAMP_CEILING),
            registerBlockItem(BACKROOMS_WATER_STAINED_TILE),
            registerBlockItem(BACKROOMS_YELLOW_METAL_PANEL),
            registerBlockItem(BACKROOMS_RUST_VENT),
            registerBlockItem(BACKROOMS_SERVER_RACK),
            registerBlockItem(BACKROOMS_STAIRWELL_WALL),
            registerBlockItem(BACKROOMS_DIRTY_LOCKER),
            registerBlockItem(BACKROOMS_BLACK_MOLD_PANEL)
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
