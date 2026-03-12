package io.wifi.starrailexpress.index.wathe_bridge;

import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import io.wifi.starrailexpress.block.*;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMProperties;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.BlockSettingsAdditions;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamily;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import java.util.function.ToIntFunction;

@SuppressWarnings("unchecked")
public interface WatheBridgerBlocks {
    BlockRegistrar registrar = new BlockRegistrar(SRE.WATHE_MOD_ID);

    // Metallic blocks
    Block TARNISHED_GOLD = registrar.createWithItem("tarnished_gold", new Block(BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).sound(SoundType.NETHERITE_BLOCK)), WatheBridgerItems.BUILDING_GROUP);
    Block TARNISHED_GOLD_STAIRS = registrar.createWithItem("tarnished_gold_stairs", new StairBlock(TARNISHED_GOLD.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(TARNISHED_GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block TARNISHED_GOLD_SLAB = registrar.createWithItem("tarnished_gold_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(TARNISHED_GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block TARNISHED_GOLD_WALL = registrar.createWithItem("tarnished_gold_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(TARNISHED_GOLD).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block TARNISHED_GOLD_PILLAR = registrar.createWithItem("tarnished_gold_pillar", new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(TARNISHED_GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block GOLD = registrar.createWithItem("gold", new Block(BlockBehaviour.Properties.ofFullCopy(TARNISHED_GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block GOLD_STAIRS = registrar.createWithItem("gold_stairs", new StairBlock(GOLD.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block GOLD_SLAB = registrar.createWithItem("gold_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block GOLD_WALL = registrar.createWithItem("gold_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(GOLD).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block GOLD_PILLAR = registrar.createWithItem("gold_pillar", new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block PRISTINE_GOLD = registrar.createWithItem("pristine_gold", new Block(BlockBehaviour.Properties.ofFullCopy(TARNISHED_GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block PRISTINE_GOLD_STAIRS = registrar.createWithItem("pristine_gold_stairs", new StairBlock(PRISTINE_GOLD.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(PRISTINE_GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block PRISTINE_GOLD_SLAB = registrar.createWithItem("pristine_gold_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(PRISTINE_GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block PRISTINE_GOLD_WALL = registrar.createWithItem("pristine_gold_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(PRISTINE_GOLD).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block PRISTINE_GOLD_PILLAR = registrar.createWithItem("pristine_gold_pillar", new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(PRISTINE_GOLD)), WatheBridgerItems.BUILDING_GROUP);
    Block WHITE_HULL = registrar.createWithItem("white_hull", new Block(BlockBehaviour.Properties.ofFullCopy(TARNISHED_GOLD).mapColor(MapColor.SNOW)), WatheBridgerItems.BUILDING_GROUP);
    Block WHITE_HULL_STAIRS = registrar.createWithItem("white_hull_stairs", new StairBlock(WHITE_HULL.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(WHITE_HULL)), WatheBridgerItems.BUILDING_GROUP);
    Block WHITE_HULL_SLAB = registrar.createWithItem("white_hull_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(WHITE_HULL)), WatheBridgerItems.BUILDING_GROUP);
    Block WHITE_HULL_WALL = registrar.createWithItem("white_hull_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(WHITE_HULL).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block CULLING_WHITE_HULL = registrar.createWithItem("culling_white_hull", new CullingBlock(BlockBehaviour.Properties.ofFullCopy(WHITE_HULL).noOcclusion()), WatheBridgerItems.BUILDING_GROUP);
    Block BLACK_HULL = registrar.createWithItem("black_hull", new Block(BlockBehaviour.Properties.ofFullCopy(WHITE_HULL).mapColor(MapColor.COLOR_BLACK)), WatheBridgerItems.BUILDING_GROUP);
    Block BLACK_HULL_STAIRS = registrar.createWithItem("black_hull_stairs", new StairBlock(BLACK_HULL.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(BLACK_HULL)), WatheBridgerItems.BUILDING_GROUP);
    Block BLACK_HULL_SLAB = registrar.createWithItem("black_hull_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(BLACK_HULL)), WatheBridgerItems.BUILDING_GROUP);
    Block BLACK_HULL_WALL = registrar.createWithItem("black_hull_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(BLACK_HULL).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block CULLING_BLACK_HULL = registrar.createWithItem("culling_black_hull", new CullingBlock(BlockBehaviour.Properties.ofFullCopy(BLACK_HULL).noOcclusion()), WatheBridgerItems.BUILDING_GROUP);
    Block BLACK_HULL_SHEETS = registrar.createWithItem("black_hull_sheets", new Block(BlockBehaviour.Properties.ofFullCopy(BLACK_HULL)), WatheBridgerItems.BUILDING_GROUP);
    Block BLACK_HULL_SHEET_STAIRS = registrar.createWithItem("black_hull_sheet_stairs", new StairBlock(BLACK_HULL_SHEETS.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(BLACK_HULL_SHEETS)), WatheBridgerItems.BUILDING_GROUP);
    Block BLACK_HULL_SHEET_SLAB = registrar.createWithItem("black_hull_sheet_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(BLACK_HULL_SHEETS)), WatheBridgerItems.BUILDING_GROUP);
    Block BLACK_HULL_SHEET_WALL = registrar.createWithItem("black_hull_sheet_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(BLACK_HULL_SHEETS).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block GOLD_BAR = registrar.createWithItem("gold_bar", new BarBlock(BlockBehaviour.Properties.ofFullCopy(TARNISHED_GOLD).noOcclusion().strength(0.5f)), WatheBridgerItems.DECORATION_GROUP);
    Block GOLD_LEDGE = registrar.createWithItem("gold_ledge", new LedgeBlock(BlockBehaviour.Properties.ofFullCopy(TARNISHED_GOLD).noOcclusion().strength(0.5f).dynamicShape()), WatheBridgerItems.DECORATION_GROUP);
    Block METAL_SHEET = registrar.createWithItem("metal_sheet", new Block(BlockBehaviour.Properties.of().strength(2f).sound(SoundType.COPPER)), WatheBridgerItems.BUILDING_GROUP);
    Block METAL_SHEET_STAIRS = registrar.createWithItem("metal_sheet_stairs", new StairBlock(METAL_SHEET.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(METAL_SHEET)), WatheBridgerItems.BUILDING_GROUP);
    Block METAL_SHEET_SLAB = registrar.createWithItem("metal_sheet_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(METAL_SHEET)), WatheBridgerItems.BUILDING_GROUP);
    Block METAL_SHEET_WALL = registrar.createWithItem("metal_sheet_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(METAL_SHEET).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block METAL_SHEET_WALKWAY = registrar.createWithItem("metal_sheet_walkway", new WalkwayBlock(BlockBehaviour.Properties.ofFullCopy(METAL_SHEET).sound(SoundType.COPPER_GRATE).noOcclusion()), WatheBridgerItems.BUILDING_GROUP);
    Block METAL_SHEET_DOOR = registrar.createWithItem("metal_sheet_door", new DoorBlock(SetType.METAL_SHEET, BlockBehaviour.Properties.of().requiresCorrectToolForDrops().strength(5.0F).noOcclusion().sound(SoundType.COPPER).pushReaction(PushReaction.DESTROY)), WatheBridgerItems.BUILDING_GROUP);
    Block COCKPIT_DOOR = registrar.createWithItem("cockpit_door", new DoorBlock(SetType.METAL_SHEET, BlockBehaviour.Properties.ofFullCopy(METAL_SHEET_DOOR)), WatheBridgerItems.BUILDING_GROUP);
    Block STAINLESS_STEEL = registrar.createWithItem("stainless_steel", new Block(BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops()), WatheBridgerItems.BUILDING_GROUP);
    Block STAINLESS_STEEL_STAIRS = registrar.createWithItem("stainless_steel_stairs", new StairBlock(STAINLESS_STEEL.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block STAINLESS_STEEL_SLAB = registrar.createWithItem("stainless_steel_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block STAINLESS_STEEL_WALL = registrar.createWithItem("stainless_steel_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block STAINLESS_STEEL_WALKWAY = registrar.createWithItem("stainless_steel_walkway", new WalkwayBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL).sound(SoundType.COPPER_GRATE).noOcclusion()), WatheBridgerItems.BUILDING_GROUP);
    Block STAINLESS_STEEL_BRANCH = createBranch("stainless_steel_branch", WatheBridgerBlocks.STAINLESS_STEEL, registrar);
    Block STAINLESS_STEEL_PILLAR = registrar.createWithItem("stainless_steel_pillar", new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_STEEL = registrar.createWithItem("dark_steel", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_STEEL_STAIRS = registrar.createWithItem("dark_steel_stairs", new StairBlock(DARK_STEEL.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(DARK_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_STEEL_SLAB = registrar.createWithItem("dark_steel_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_STEEL_WALL = registrar.createWithItem("dark_steel_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_STEEL_WALKWAY = registrar.createWithItem("dark_steel_walkway", new WalkwayBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).sound(SoundType.COPPER_GRATE).noOcclusion()), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_STEEL_BRANCH = createBranch("dark_steel_branch", WatheBridgerBlocks.DARK_STEEL, registrar);
    Block DARK_STEEL_PILLAR = registrar.createWithItem("dark_steel_pillar", new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block STAINLESS_STEEL_BAR = registrar.createWithItem("stainless_steel_bar", new BarBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL).noOcclusion().strength(0.5f)), WatheBridgerItems.DECORATION_GROUP);
    Block RAIL_BEAM = registrar.createWithItem("rail_beam", new RailBeamBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL).forceSolidOn()), WatheBridgerItems.DECORATION_GROUP);

    // Doors
    Block SMALL_GLASS_DOOR = registrar.createWithItem("small_glass_door", new SmallDoorBlock(() -> TMMBlockEntities.SMALL_GLASS_DOOR, BlockBehaviour.Properties.of().dynamicShape().strength(-1, 3600000).mapColor(MapColor.NONE).noLootTable().noOcclusion().isValidSpawn(Blocks::never).pushReaction(PushReaction.BLOCK).sound(SoundType.COPPER_BULB)), WatheBridgerItems.DECORATION_GROUP);
    Block SMALL_WOOD_DOOR = registrar.createWithItem("small_wood_door", new SmallDoorBlock(() -> TMMBlockEntities.SMALL_WOOD_DOOR, BlockBehaviour.Properties.ofFullCopy(SMALL_GLASS_DOOR).sound(SoundType.COPPER)), WatheBridgerItems.DECORATION_GROUP);

    // Fancy steel
    Block ANTHRACITE_STEEL = registrar.createWithItem("anthracite_steel", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block ANTHRACITE_STEEL_PANEL = registrar.createWithItem("anthracite_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block ANTHRACITE_STEEL_TILES = registrar.createWithItem("anthracite_steel_tiles", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block ANTHRACITE_STEEL_TILES_PANEL = registrar.createWithItem("anthracite_steel_tiles_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_ANTHRACITE_STEEL = registrar.createWithItem("smooth_anthracite_steel", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_ANTHRACITE_STEEL_STAIRS = registrar.createWithItem("smooth_anthracite_steel_stairs", new StairBlock(ANTHRACITE_STEEL.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_ANTHRACITE_STEEL_SLAB = registrar.createWithItem("smooth_anthracite_steel_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_ANTHRACITE_STEEL_PANEL = registrar.createWithItem("smooth_anthracite_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_ANTHRACITE_STEEL_WALL = registrar.createWithItem("smooth_anthracite_steel_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block ANTHRACITE_STEEL_DOOR = registrar.createWithItem("anthracite_steel_door", new TrainDoorBlock(() -> TMMBlockEntities.ANTHRACITE_STEEL_DOOR, BlockBehaviour.Properties.ofFullCopy(SMALL_GLASS_DOOR).sound(SoundType.COPPER)), WatheBridgerItems.DECORATION_GROUP);
    Block KHAKI_STEEL = registrar.createWithItem("khaki_steel", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block KHAKI_STEEL_PANEL = registrar.createWithItem("khaki_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block KHAKI_STEEL_TILES = registrar.createWithItem("khaki_steel_tiles", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block KHAKI_STEEL_TILES_PANEL = registrar.createWithItem("khaki_steel_tiles_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_KHAKI_STEEL = registrar.createWithItem("smooth_khaki_steel", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_KHAKI_STEEL_STAIRS = registrar.createWithItem("smooth_khaki_steel_stairs", new StairBlock(KHAKI_STEEL.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_KHAKI_STEEL_SLAB = registrar.createWithItem("smooth_khaki_steel_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_KHAKI_STEEL_PANEL = registrar.createWithItem("smooth_khaki_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_KHAKI_STEEL_WALL = registrar.createWithItem("smooth_khaki_steel_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block KHAKI_STEEL_DOOR = registrar.createWithItem("khaki_steel_door", new TrainDoorBlock(() -> TMMBlockEntities.KHAKI_STEEL_DOOR, BlockBehaviour.Properties.ofFullCopy(SMALL_GLASS_DOOR).sound(SoundType.COPPER)), WatheBridgerItems.DECORATION_GROUP);
    Block MAROON_STEEL = registrar.createWithItem("maroon_steel", new Block(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block MAROON_STEEL_PANEL = registrar.createWithItem("maroon_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block MAROON_STEEL_TILES = registrar.createWithItem("maroon_steel_tiles", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block MAROON_STEEL_TILES_PANEL = registrar.createWithItem("maroon_steel_tiles_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MAROON_STEEL = registrar.createWithItem("smooth_maroon_steel", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MAROON_STEEL_STAIRS = registrar.createWithItem("smooth_maroon_steel_stairs", new StairBlock(MAROON_STEEL.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MAROON_STEEL_SLAB = registrar.createWithItem("smooth_maroon_steel_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MAROON_STEEL_PANEL = registrar.createWithItem("smooth_maroon_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MAROON_STEEL_WALL = registrar.createWithItem("smooth_maroon_steel_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block MAROON_STEEL_DOOR = registrar.createWithItem("maroon_steel_door", new TrainDoorBlock(() -> TMMBlockEntities.MAROON_STEEL_DOOR, BlockBehaviour.Properties.ofFullCopy(SMALL_GLASS_DOOR).sound(SoundType.COPPER)), WatheBridgerItems.DECORATION_GROUP);
    Block MUNTZ_STEEL = registrar.createWithItem("muntz_steel", new Block(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block MUNTZ_STEEL_PANEL = registrar.createWithItem("muntz_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block MUNTZ_STEEL_TILES = registrar.createWithItem("muntz_steel_tiles", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block MUNTZ_STEEL_TILES_PANEL = registrar.createWithItem("muntz_steel_tiles_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MUNTZ_STEEL = registrar.createWithItem("smooth_muntz_steel", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MUNTZ_STEEL_STAIRS = registrar.createWithItem("smooth_muntz_steel_stairs", new StairBlock(MUNTZ_STEEL.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MUNTZ_STEEL_SLAB = registrar.createWithItem("smooth_muntz_steel_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MUNTZ_STEEL_PANEL = registrar.createWithItem("smooth_muntz_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MUNTZ_STEEL_WALL = registrar.createWithItem("smooth_muntz_steel_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block MUNTZ_STEEL_DOOR = registrar.createWithItem("muntz_steel_door", new TrainDoorBlock(() -> TMMBlockEntities.MUNTZ_STEEL_DOOR, BlockBehaviour.Properties.ofFullCopy(SMALL_GLASS_DOOR).sound(SoundType.COPPER)), WatheBridgerItems.DECORATION_GROUP);
    Block NAVY_STEEL = registrar.createWithItem("navy_steel", new Block(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block NAVY_STEEL_PANEL = registrar.createWithItem("navy_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block NAVY_STEEL_TILES = registrar.createWithItem("navy_steel_tiles", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block NAVY_STEEL_TILES_PANEL = registrar.createWithItem("navy_steel_tiles_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_NAVY_STEEL = registrar.createWithItem("smooth_navy_steel", new Block(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_NAVY_STEEL_STAIRS = registrar.createWithItem("smooth_navy_steel_stairs", new StairBlock(NAVY_STEEL.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_NAVY_STEEL_SLAB = registrar.createWithItem("smooth_navy_steel_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_NAVY_STEEL_PANEL = registrar.createWithItem("smooth_navy_steel_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_NAVY_STEEL_WALL = registrar.createWithItem("smooth_navy_steel_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block NAVY_STEEL_DOOR = registrar.createWithItem("navy_steel_door", new TrainDoorBlock(() -> TMMBlockEntities.NAVY_STEEL_DOOR, BlockBehaviour.Properties.ofFullCopy(SMALL_GLASS_DOOR).sound(SoundType.COPPER)), WatheBridgerItems.DECORATION_GROUP);

    // Glass
    Block HULL_GLASS = registrar.createWithItem("hull_glass", new PrivacyGlassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_STAINED_GLASS).strength(-1.0f, 3600000.0f)), WatheBridgerItems.BUILDING_GROUP);
    Block RHOMBUS_HULL_GLASS = registrar.createWithItem("rhombus_hull_glass", new StainedGlassBlock(DyeColor.BLACK, BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_STAINED_GLASS).strength(-1.0f, 3600000.0f)), WatheBridgerItems.BUILDING_GROUP);
    Block RHOMBUS_GLASS = registrar.createWithItem("rhombus_glass", new StainedGlassBlock(DyeColor.BLACK, BlockBehaviour.Properties.ofFullCopy(Blocks.BLACK_STAINED_GLASS)), WatheBridgerItems.BUILDING_GROUP);
    Block GOLDEN_GLASS_PANEL = registrar.createWithItem("golden_glass_panel", new GlassPanelBlock(BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS).isValidSpawn(Blocks::never)), WatheBridgerItems.DECORATION_GROUP);
    Block PRIVACY_GLASS_PANEL = registrar.createWithItem("privacy_glass_panel", new PrivacyGlassPanelBlock(BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS).noOcclusion().isValidSpawn(Blocks::never)), WatheBridgerItems.DECORATION_GROUP);
    Block CULLING_GLASS = registrar.createWithItem("culling_glass", new CullingGlassBlock(BlockBehaviour.Properties.of().forceSolidOn().strength(-1.0f, 3600000.0f).isValidSpawn(Blocks::never).sound(SoundType.GLASS)), WatheBridgerItems.DECORATION_GROUP);

    // Stones
    Block MARBLE = registrar.createWithItem("marble", new Block(BlockBehaviour.Properties.of().strength(2f).sound(SoundType.CALCITE)), WatheBridgerItems.BUILDING_GROUP);
    Block MARBLE_STAIRS = registrar.createWithItem("marble_stairs", new StairBlock(MARBLE.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(MARBLE)), WatheBridgerItems.BUILDING_GROUP);
    Block MARBLE_SLAB = registrar.createWithItem("marble_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(MARBLE)), WatheBridgerItems.BUILDING_GROUP);
    Block MARBLE_WALL = registrar.createWithItem("marble_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(MARBLE).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block MARBLE_MOSAIC = registrar.createWithItem("marble_mosaic", new GlazedTerracottaBlock(BlockBehaviour.Properties.ofFullCopy(MARBLE)), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_MARBLE = registrar.createWithItem("dark_marble", new Block(BlockBehaviour.Properties.ofFullCopy(MARBLE)), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_MARBLE_STAIRS = registrar.createWithItem("dark_marble_stairs", new StairBlock(DARK_MARBLE.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(DARK_MARBLE)), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_MARBLE_SLAB = registrar.createWithItem("dark_marble_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(DARK_MARBLE)), WatheBridgerItems.BUILDING_GROUP);
    Block DARK_MARBLE_WALL = registrar.createWithItem("dark_marble_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(DARK_MARBLE).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);
    Block MARBLE_TILES = registrar.createWithItem("marble_tiles", new Block(BlockBehaviour.Properties.of().strength(2f).sound(SoundType.CALCITE)), WatheBridgerItems.BUILDING_GROUP);
    Block MARBLE_TILE_STAIRS = registrar.createWithItem("marble_tile_stairs", new StairBlock(MARBLE_TILES.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(MARBLE_TILES)), WatheBridgerItems.BUILDING_GROUP);
    Block MARBLE_TILE_SLAB = registrar.createWithItem("marble_tile_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(MARBLE_TILES)), WatheBridgerItems.BUILDING_GROUP);
    Block MARBLE_TILE_WALL = registrar.createWithItem("marble_tile_wall", new WallBlock(BlockBehaviour.Properties.ofFullCopy(MARBLE_TILES).forceSolidOn()), WatheBridgerItems.BUILDING_GROUP);

    // Carpets
    Block RED_MOQUETTE = registrar.createWithItem("red_moquette", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.RED_WOOL).strength(-1.0f, 3600000.0f)), WatheBridgerItems.BUILDING_GROUP);
    Block BROWN_MOQUETTE = registrar.createWithItem("brown_moquette", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BROWN_WOOL).strength(-1.0f, 3600000.0f)), WatheBridgerItems.BUILDING_GROUP);
    Block BLUE_MOQUETTE = registrar.createWithItem("blue_moquette", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_WOOL).strength(-1.0f, 3600000.0f)), WatheBridgerItems.BUILDING_GROUP);

    // Woods
    Block MAHOGANY_PLANKS = registrar.createWithItem("mahogany_planks", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.MANGROVE_PLANKS).strength(-1.0f, 3600000.0f).sound(SoundType.CHERRY_WOOD)), WatheBridgerItems.BUILDING_GROUP);
    Block MAHOGANY_STAIRS = registrar.createWithItem("mahogany_stairs", new StairBlock(MAHOGANY_PLANKS.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(MAHOGANY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block MAHOGANY_SLAB = registrar.createWithItem("mahogany_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(MAHOGANY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block MAHOGANY_HERRINGBONE = registrar.createWithItem("mahogany_herringbone", new Block(BlockBehaviour.Properties.ofFullCopy(MAHOGANY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block MAHOGANY_HERRINGBONE_STAIRS = registrar.createWithItem("mahogany_herringbone_stairs", new StairBlock(MAHOGANY_HERRINGBONE.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(MAHOGANY_HERRINGBONE)), WatheBridgerItems.BUILDING_GROUP);
    Block MAHOGANY_HERRINGBONE_SLAB = registrar.createWithItem("mahogany_herringbone_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(MAHOGANY_HERRINGBONE)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MAHOGANY = registrar.createWithItem("smooth_mahogany", new Block(BlockBehaviour.Properties.ofFullCopy(MAHOGANY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MAHOGANY_STAIRS = registrar.createWithItem("smooth_mahogany_stairs", new StairBlock(SMOOTH_MAHOGANY.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(SMOOTH_MAHOGANY)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_MAHOGANY_SLAB = registrar.createWithItem("smooth_mahogany_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(SMOOTH_MAHOGANY)), WatheBridgerItems.BUILDING_GROUP);
    Block MAHOGANY_PANEL = registrar.createWithItem("mahogany_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(MAHOGANY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block MAHOGANY_CABINET = registrar.createWithItem("mahogany_cabinet", new CabinetBlock(BlockBehaviour.Properties.ofFullCopy(MAHOGANY_PLANKS).noOcclusion()), WatheBridgerItems.BUILDING_GROUP);
    Block MAHOGANY_BOOKSHELF = registrar.createWithItem("mahogany_bookshelf", new Block(BlockBehaviour.Properties.ofFullCopy(MAHOGANY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block BUBINGA_PLANKS = registrar.createWithItem("bubinga_planks", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.ACACIA_PLANKS).strength(-1.0f, 3600000.0f).sound(SoundType.CHERRY_WOOD)), WatheBridgerItems.BUILDING_GROUP);
    Block BUBINGA_STAIRS = registrar.createWithItem("bubinga_stairs", new StairBlock(BUBINGA_PLANKS.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(BUBINGA_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block BUBINGA_SLAB = registrar.createWithItem("bubinga_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(BUBINGA_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block BUBINGA_HERRINGBONE = registrar.createWithItem("bubinga_herringbone", new Block(BlockBehaviour.Properties.ofFullCopy(BUBINGA_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block BUBINGA_HERRINGBONE_STAIRS = registrar.createWithItem("bubinga_herringbone_stairs", new StairBlock(BUBINGA_HERRINGBONE.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(MAHOGANY_HERRINGBONE)), WatheBridgerItems.BUILDING_GROUP);
    Block BUBINGA_HERRINGBONE_SLAB = registrar.createWithItem("bubinga_herringbone_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(BUBINGA_HERRINGBONE)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_BUBINGA = registrar.createWithItem("smooth_bubinga", new Block(BlockBehaviour.Properties.ofFullCopy(BUBINGA_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_BUBINGA_STAIRS = registrar.createWithItem("smooth_bubinga_stairs", new StairBlock(SMOOTH_BUBINGA.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(SMOOTH_BUBINGA)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_BUBINGA_SLAB = registrar.createWithItem("smooth_bubinga_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(SMOOTH_BUBINGA)), WatheBridgerItems.BUILDING_GROUP);
    Block BUBINGA_PANEL = registrar.createWithItem("bubinga_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(BUBINGA_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block BUBINGA_CABINET = registrar.createWithItem("bubinga_cabinet", new CabinetBlock(BlockBehaviour.Properties.ofFullCopy(BUBINGA_PLANKS).noOcclusion()), WatheBridgerItems.BUILDING_GROUP);
    Block BUBINGA_BOOKSHELF = registrar.createWithItem("bubinga_bookshelf", new Block(BlockBehaviour.Properties.ofFullCopy(BUBINGA_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block EBONY_PLANKS = registrar.createWithItem("ebony_planks", new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_PLANKS).strength(-1.0f, 3600000.0f).sound(SoundType.CHERRY_WOOD)), WatheBridgerItems.BUILDING_GROUP);
    Block EBONY_STAIRS = registrar.createWithItem("ebony_stairs", new StairBlock(EBONY_PLANKS.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(EBONY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block EBONY_SLAB = registrar.createWithItem("ebony_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(EBONY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block EBONY_HERRINGBONE = registrar.createWithItem("ebony_herringbone", new Block(BlockBehaviour.Properties.ofFullCopy(EBONY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block EBONY_HERRINGBONE_STAIRS = registrar.createWithItem("ebony_herringbone_stairs", new StairBlock(EBONY_HERRINGBONE.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(MAHOGANY_HERRINGBONE)), WatheBridgerItems.BUILDING_GROUP);
    Block EBONY_HERRINGBONE_SLAB = registrar.createWithItem("ebony_herringbone_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(EBONY_HERRINGBONE)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_EBONY = registrar.createWithItem("smooth_ebony", new Block(BlockBehaviour.Properties.ofFullCopy(EBONY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_EBONY_STAIRS = registrar.createWithItem("smooth_ebony_stairs", new StairBlock(SMOOTH_EBONY.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(SMOOTH_EBONY)), WatheBridgerItems.BUILDING_GROUP);
    Block SMOOTH_EBONY_SLAB = registrar.createWithItem("smooth_ebony_slab", new SlabBlock(BlockBehaviour.Properties.ofFullCopy(SMOOTH_EBONY)), WatheBridgerItems.BUILDING_GROUP);
    Block EBONY_PANEL = registrar.createWithItem("ebony_panel", new PanelBlock(BlockBehaviour.Properties.ofFullCopy(EBONY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block EBONY_CABINET = registrar.createWithItem("ebony_cabinet", new CabinetBlock(BlockBehaviour.Properties.ofFullCopy(EBONY_PLANKS).noOcclusion()), WatheBridgerItems.BUILDING_GROUP);
    Block TRIMMED_EBONY_STAIRS = registrar.createWithItem("trimmed_ebony_stairs", new TrimmedStairsBlock(BlockBehaviour.Properties.ofFullCopy(EBONY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);
    Block EBONY_BOOKSHELF = registrar.createWithItem("ebony_bookshelf", new Block(BlockBehaviour.Properties.ofFullCopy(EBONY_PLANKS)), WatheBridgerItems.BUILDING_GROUP);

    // Vents
    Block STAINLESS_STEEL_VENT_SHAFT = registrar.createWithItem("stainless_steel_vent_shaft", new VentShaftBlock(BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).sound(TMMSounds.VENT_SHAFT).mapColor(MapColor.COLOR_GRAY)), WatheBridgerItems.DECORATION_GROUP);
    Block STAINLESS_STEEL_VENT_HATCH = registrar.createWithItem("stainless_steel_vent_hatch", new VentHatchBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL_VENT_SHAFT).strength(0.3f).sound(SoundType.COPPER).noOcclusion()), WatheBridgerItems.DECORATION_GROUP);
    Block DARK_STEEL_VENT_HATCH = registrar.createWithItem("dark_steel_vent_hatch", new VentHatchBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL_VENT_HATCH)), WatheBridgerItems.DECORATION_GROUP);
    Block TARNISHED_GOLD_VENT_HATCH = registrar.createWithItem("tarnished_gold_vent_hatch", new VentHatchBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL_VENT_HATCH)), WatheBridgerItems.DECORATION_GROUP);
    Block DARK_STEEL_VENT_SHAFT = registrar.createWithItem("dark_steel_vent_shaft", new VentShaftBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL_VENT_SHAFT)), WatheBridgerItems.DECORATION_GROUP);
    Block TARNISHED_GOLD_VENT_SHAFT = registrar.createWithItem("tarnished_gold_vent_shaft", new VentShaftBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL_VENT_SHAFT)), WatheBridgerItems.DECORATION_GROUP);

    // Furniture / Decor
    Block STAINLESS_STEEL_LADDER = registrar.createWithItem("stainless_steel_ladder", new TrainLadderBlock(BlockBehaviour.Properties.of().noOcclusion().strength(0.5f).sound(SoundType.LANTERN)), WatheBridgerItems.DECORATION_GROUP);
    Block OAK_BRANCH = createBranch("oak_branch", Blocks.OAK_WOOD, registrar);
    Block SPRUCE_BRANCH = createBranch("spruce_branch", Blocks.SPRUCE_WOOD, registrar);
    Block BIRCH_BRANCH = createBranch("birch_branch", Blocks.BIRCH_WOOD, registrar);
    Block JUNGLE_BRANCH = createBranch("jungle_branch", Blocks.JUNGLE_WOOD, registrar);
    Block ACACIA_BRANCH = createBranch("acacia_branch", Blocks.ACACIA_WOOD, registrar);
    Block DARK_OAK_BRANCH = createBranch("dark_oak_branch", Blocks.DARK_OAK_WOOD, registrar);
    Block MANGROVE_BRANCH = createBranch("mangrove_branch", Blocks.MANGROVE_WOOD, registrar);
    Block CHERRY_BRANCH = createBranch("cherry_branch", Blocks.CHERRY_WOOD, registrar);
    Block BAMBOO_POLE = createBranch("bamboo_pole", Blocks.BAMBOO_BLOCK, registrar);
    Block CRIMSON_STIPE = createBranch("crimson_stipe", Blocks.CRIMSON_HYPHAE, registrar);
    Block WARPED_STIPE = createBranch("warped_stipe", Blocks.WARPED_HYPHAE, registrar);
    Block STRIPPED_OAK_BRANCH = createBranch("stripped_oak_branch", Blocks.STRIPPED_OAK_WOOD, registrar);
    Block STRIPPED_SPRUCE_BRANCH = createBranch("stripped_spruce_branch", Blocks.STRIPPED_SPRUCE_WOOD, registrar);
    Block STRIPPED_BIRCH_BRANCH = createBranch("stripped_birch_branch", Blocks.STRIPPED_BIRCH_WOOD, registrar);
    Block STRIPPED_JUNGLE_BRANCH = createBranch("stripped_jungle_branch", Blocks.STRIPPED_JUNGLE_WOOD, registrar);
    Block STRIPPED_ACACIA_BRANCH = createBranch("stripped_acacia_branch", Blocks.STRIPPED_ACACIA_WOOD, registrar);
    Block STRIPPED_DARK_OAK_BRANCH = createBranch("stripped_dark_oak_branch", Blocks.STRIPPED_DARK_OAK_WOOD, registrar);
    Block STRIPPED_MANGROVE_BRANCH = createBranch("stripped_mangrove_branch", Blocks.STRIPPED_MANGROVE_WOOD, registrar);
    Block STRIPPED_CHERRY_BRANCH = createBranch("stripped_cherry_branch", Blocks.STRIPPED_CHERRY_WOOD, registrar);
    Block STRIPPED_BAMBOO_POLE = createBranch("stripped_bamboo_pole", Blocks.STRIPPED_BAMBOO_BLOCK, registrar);
    Block STRIPPED_CRIMSON_STIPE = createBranch("stripped_crimson_stipe", Blocks.STRIPPED_CRIMSON_HYPHAE, registrar);
    Block STRIPPED_WARPED_STIPE = createBranch("stripped_warped_stipe", Blocks.STRIPPED_WARPED_HYPHAE, registrar);
    Block TRIMMED_RAILING_POST = registrar.create("trimmed_railing_post", new RailingPostBlock(BlockBehaviour.Properties.of().sound(SoundType.CHERRY_WOOD_HANGING_SIGN).strength(1f).noOcclusion()));
    Block DIAGONAL_TRIMMED_RAILING = registrar.create("diagonal_trimmed_railing", new DiagonalRailingBlock(BlockBehaviour.Properties.ofFullCopy(TRIMMED_RAILING_POST)));
    Block TRIMMED_RAILING = registrar.createWithItem("trimmed_railing", new RailingBlock(DIAGONAL_TRIMMED_RAILING, TRIMMED_RAILING_POST, BlockBehaviour.Properties.ofFullCopy(TRIMMED_RAILING_POST)), WatheBridgerItems.DECORATION_GROUP);
    Block PANEL_STRIPES = registrar.createWithItem("panel_stripes", new PanelStripesBlock(BlockBehaviour.Properties.of().sound(SoundType.CHISELED_BOOKSHELF).strength(0.5f).noOcclusion()), WatheBridgerItems.DECORATION_GROUP);
    Block CARGO_BOX = registrar.createWithItem("cargo_box", new CargoBoxBlock(BlockBehaviour.Properties.of().strength(1).sound(SoundType.COPPER).mapColor(MapColor.COLOR_GRAY).noOcclusion()), WatheBridgerItems.DECORATION_GROUP);
    Block WHITE_LOUNGE_COUCH = registrar.createWithItem("white_lounge_couch", new LoungeCouch(BlockBehaviour.Properties.of().noOcclusion().strength(0.5f).sound(SoundType.CHISELED_BOOKSHELF)), WatheBridgerItems.DECORATION_GROUP);
    
    Block WHITE_OTTOMAN = registrar.createWithItem("white_ottoman", new OttomanBlock(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block BLUE_LOUNGE_COUCH = registrar.createWithItem("blue_lounge_couch", new LoungeCouch(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block GREEN_LOUNGE_COUCH = registrar.createWithItem("green_lounge_couch", new LoungeCouch(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block RED_LEATHER_COUCH = registrar.createWithItem("red_leather_couch", new LeatherCouch(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block BROWN_LEATHER_COUCH = registrar.createWithItem("brown_leather_couch", new LeatherCouch(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block BEIGE_LEATHER_COUCH = registrar.createWithItem("beige_leather_couch", new LeatherCouch(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block COFFEE_TABLE = registrar.createWithItem("coffee_table", new CoffeeTableBlock(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block BAR_TABLE = registrar.createWithItem("bar_table", new BarTableBlock(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block BAR_STOOL = registrar.createWithItem("bar_stool", new BarStoolBlock(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block WHITE_TRIMMED_BED = registrar.createWithItem("white_trimmed_bed", new TrimmedBedBlock(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block RED_TRIMMED_BED = registrar.createWithItem("red_trimmed_bed", new TrimmedBedBlock(BlockBehaviour.Properties.ofFullCopy(WHITE_LOUNGE_COUCH)), WatheBridgerItems.DECORATION_GROUP);
    Block HORN = registrar.createWithItem("horn", new HornBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHAIN).noOcclusion().noCollission()), WatheBridgerItems.DECORATION_GROUP);

    // Lamps
    Block TRIMMED_LANTERN = registrar.createWithItem("trimmed_lantern", new TrimmedLanternBlock(BlockBehaviour.Properties.of().strength(0.5f).noOcclusion().lightLevel(createLightLevelFromLitPoweredBlockState(15)).sound(SoundType.LANTERN)), WatheBridgerItems.DECORATION_GROUP);
    Block WALL_LAMP = registrar.createWithItem("wall_lamp", new WallLampBlock(BlockBehaviour.Properties.ofFullCopy(TRIMMED_LANTERN).lightLevel(createLightLevelFromLitPoweredBlockState(15))), WatheBridgerItems.DECORATION_GROUP);
    Block NEON_PILLAR = registrar.createWithItem("neon_pillar", new NeonPillarBlock(BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.COPPER_BULB).lightLevel(createLightLevelFromLitPoweredBlockState(15))), WatheBridgerItems.DECORATION_GROUP);
    Block NEON_TUBE = registrar.createWithItem("neon_tube", new NeonTubeBlock(BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.COPPER_BULB).lightLevel(createLightLevelFromLitPoweredBlockState(15))), WatheBridgerItems.DECORATION_GROUP);

    Block SMALL_BUTTON = registrar.createWithItem("small_button", new SmallButtonBlock(BlockBehaviour.Properties.of().sound(SoundType.CHERRY_WOOD).noOcclusion().noCollission().strength(-1.0f, 3600000.0f)), WatheBridgerItems.DECORATION_GROUP);
    Block ELEVATOR_BUTTON = registrar.createWithItem("elevator_button", new ElevatorButtonBlock(BlockBehaviour.Properties.ofFullCopy(SMALL_BUTTON)), WatheBridgerItems.DECORATION_GROUP);
    Block STAINLESS_STEEL_SPRINKLER = registrar.createWithItem("stainless_steel_sprinkler", new SprinklerBlock(BlockBehaviour.Properties.of().strength(0.5f).noOcclusion().sound(SoundType.LANTERN)), WatheBridgerItems.DECORATION_GROUP);
    Block GOLD_SPRINKLER = registrar.createWithItem("gold_sprinkler", new SprinklerBlock(BlockBehaviour.Properties.ofFullCopy(STAINLESS_STEEL_SPRINKLER)), WatheBridgerItems.DECORATION_GROUP);
    Block GOLD_ORNAMENT = registrar.createWithItem("gold_ornament", new OrnamentBlock(BlockBehaviour.Properties.of().noOcclusion().noCollission().strength(0.25f).sound(SoundType.COPPER)), WatheBridgerItems.DECORATION_GROUP);

    // Wheels
    Block WHEEL = registrar.createWithItem("wheel", new WheelBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion().sound(SoundType.COPPER)), WatheBridgerItems.DECORATION_GROUP);
    Block RUSTED_WHEEL = registrar.createWithItem("rusted_wheel", new WheelBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion().sound(SoundType.COPPER)), WatheBridgerItems.DECORATION_GROUP);

    // Platters
    Block FOOD_PLATTER = registrar.createWithItem("food_platter", new FoodPlatterBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_GLAZED_TERRACOTTA)
                    .noOcclusion()
                    .sound(SoundType.COPPER)
                    .instabreak()
                    .noCollission()
    ), WatheBridgerItems.DECORATION_GROUP);
    Block DRINK_TRAY = registrar.createWithItem("drink_tray", new DrinkTrayBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_GLAZED_TERRACOTTA)
                    .noOcclusion()
                    .sound(SoundType.CHERRY_WOOD)
                    .instabreak()
    ), WatheBridgerItems.DECORATION_GROUP);
    Block CHIMNEY = registrar.createWithItem("chimney", new ChimneyBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).noCollission()), WatheBridgerItems.DECORATION_GROUP);

    // Op
    Block BARRIER_PANEL = registrar.createWithItem("barrier_panel", new BarrierPanelBlock(BlockBehaviour.Properties.ofFullCopy(ANTHRACITE_STEEL_PANEL).strength(-1.0F, 3600000.8F).noOcclusion().sound(SoundType.STONE)), new Item.Properties().rarity(Rarity.EPIC), CreativeModeTabs.OP_BLOCKS);
    Block LIGHT_BARRIER = registrar.createWithItem("light_barrier", new LightBarrierBlock(((BlockSettingsAdditions) BlockBehaviour.Properties.ofFullCopy(Blocks.BARRIER)).tmm$setCollidable(false)), new Item.Properties().rarity(Rarity.EPIC), CreativeModeTabs.OP_BLOCKS);

    private static Block createBranch(String name, Block wood, BlockRegistrar registrar) {
        return registrar.createWithItem(name, new BranchBlock(BlockBehaviour.Properties.ofFullCopy(wood).mapColor(wood.defaultMapColor())), WatheBridgerItems.DECORATION_GROUP);
    }

    private static ToIntFunction<BlockState> createLightLevelFromLitPoweredBlockState(int litLevel) {
        return state -> state.getValue(BlockStateProperties.LIT) && state.getValue(TMMProperties.ACTIVE) ? litLevel : 0;
    }

    interface Family {
        BlockFamily TARNISHED_GOLD = new BlockFamily.Builder(WatheBridgerBlocks.TARNISHED_GOLD)
                .stairs(TARNISHED_GOLD_STAIRS)
                .slab(TARNISHED_GOLD_SLAB)
                .wall(TARNISHED_GOLD_WALL)
                .getFamily();
        BlockFamily GOLD = new BlockFamily.Builder(WatheBridgerBlocks.GOLD)
                .stairs(GOLD_STAIRS)
                .slab(GOLD_SLAB)
                .wall(GOLD_WALL)
                .getFamily();
        BlockFamily PRISTINE_GOLD = new BlockFamily.Builder(WatheBridgerBlocks.PRISTINE_GOLD)
                .stairs(PRISTINE_GOLD_STAIRS)
                .slab(PRISTINE_GOLD_SLAB)
                .wall(PRISTINE_GOLD_WALL)
                .getFamily();

        BlockFamily METAL_SHEET = new BlockFamily.Builder(WatheBridgerBlocks.METAL_SHEET)
                .stairs(METAL_SHEET_STAIRS)
                .slab(METAL_SHEET_SLAB)
                .wall(METAL_SHEET_WALL)
                .door(METAL_SHEET_DOOR)
                .getFamily();

        BlockFamily STAINLESS_STEEL = new BlockFamily.Builder(WatheBridgerBlocks.STAINLESS_STEEL)
                .stairs(STAINLESS_STEEL_STAIRS)
                .slab(STAINLESS_STEEL_SLAB)
                .wall(STAINLESS_STEEL_WALL)
                .getFamily();

        BlockFamily DARK_STEEL = new BlockFamily.Builder(WatheBridgerBlocks.DARK_STEEL)
                .stairs(DARK_STEEL_STAIRS)
                .slab(DARK_STEEL_SLAB)
                .wall(DARK_STEEL_WALL)
                .getFamily();

        BlockFamily SMOOTH_ANTHRACITE_STEEL = new BlockFamily.Builder(WatheBridgerBlocks.SMOOTH_ANTHRACITE_STEEL)
                .stairs(SMOOTH_ANTHRACITE_STEEL_STAIRS)
                .slab(SMOOTH_ANTHRACITE_STEEL_SLAB)
                .wall(SMOOTH_ANTHRACITE_STEEL_WALL)
                .getFamily();

        BlockFamily SMOOTH_KHAKI_STEEL = new BlockFamily.Builder(WatheBridgerBlocks.SMOOTH_KHAKI_STEEL)
                .stairs(SMOOTH_KHAKI_STEEL_STAIRS)
                .slab(SMOOTH_KHAKI_STEEL_SLAB)
                .wall(SMOOTH_KHAKI_STEEL_WALL)
                .getFamily();

        BlockFamily SMOOTH_MAROON_STEEL = new BlockFamily.Builder(WatheBridgerBlocks.SMOOTH_MAROON_STEEL)
                .stairs(SMOOTH_MAROON_STEEL_STAIRS)
                .slab(SMOOTH_MAROON_STEEL_SLAB)
                .wall(SMOOTH_MAROON_STEEL_WALL)
                .getFamily();

        BlockFamily SMOOTH_MUNTZ_STEEL = new BlockFamily.Builder(WatheBridgerBlocks.SMOOTH_MUNTZ_STEEL)
                .stairs(SMOOTH_MUNTZ_STEEL_STAIRS)
                .slab(SMOOTH_MUNTZ_STEEL_SLAB)
                .wall(SMOOTH_MUNTZ_STEEL_WALL)
                .getFamily();

        BlockFamily SMOOTH_NAVY_STEEL = new BlockFamily.Builder(WatheBridgerBlocks.SMOOTH_NAVY_STEEL)
                .stairs(SMOOTH_NAVY_STEEL_STAIRS)
                .slab(SMOOTH_NAVY_STEEL_SLAB)
                .wall(SMOOTH_NAVY_STEEL_WALL)
                .getFamily();

        BlockFamily MARBLE = new BlockFamily.Builder(WatheBridgerBlocks.MARBLE)
                .stairs(MARBLE_STAIRS)
                .slab(MARBLE_SLAB)
                .wall(MARBLE_WALL)
                .getFamily();

        BlockFamily MARBLE_TILE = new BlockFamily.Builder(WatheBridgerBlocks.MARBLE_TILES)
                .stairs(MARBLE_TILE_STAIRS)
                .slab(MARBLE_TILE_SLAB)
                .wall(MARBLE_TILE_WALL)
                .getFamily();

        BlockFamily DARK_MARBLE = new BlockFamily.Builder(WatheBridgerBlocks.DARK_MARBLE)
                .stairs(DARK_MARBLE_STAIRS)
                .slab(DARK_MARBLE_SLAB)
                .wall(DARK_MARBLE_WALL)
                .getFamily();

        BlockFamily WHITE_HULL = new BlockFamily.Builder(WatheBridgerBlocks.WHITE_HULL)
                .stairs(WHITE_HULL_STAIRS)
                .slab(WHITE_HULL_SLAB)
                .wall(WHITE_HULL_WALL)
                .getFamily();

        BlockFamily BLACK_HULL = new BlockFamily.Builder(WatheBridgerBlocks.BLACK_HULL)
                .stairs(BLACK_HULL_STAIRS)
                .slab(BLACK_HULL_SLAB)
                .wall(BLACK_HULL_WALL)
                .getFamily();

        BlockFamily BLACK_HULL_SHEET = new BlockFamily.Builder(WatheBridgerBlocks.BLACK_HULL_SHEETS)
                .stairs(BLACK_HULL_SHEET_STAIRS)
                .slab(BLACK_HULL_SHEET_SLAB)
                .wall(BLACK_HULL_SHEET_WALL)
                .getFamily();

        BlockFamily MAHOGANY = new BlockFamily.Builder(WatheBridgerBlocks.MAHOGANY_PLANKS)
                .stairs(MAHOGANY_STAIRS)
                .slab(MAHOGANY_SLAB)
                .getFamily();

        BlockFamily MAHOGANY_HERRINGBONE = new BlockFamily.Builder(WatheBridgerBlocks.MAHOGANY_HERRINGBONE)
                .stairs(MAHOGANY_HERRINGBONE_STAIRS)
                .slab(MAHOGANY_HERRINGBONE_SLAB)
                .getFamily();

        BlockFamily SMOOTH_MAHOGANY = new BlockFamily.Builder(WatheBridgerBlocks.SMOOTH_MAHOGANY)
                .stairs(SMOOTH_MAHOGANY_STAIRS)
                .slab(SMOOTH_MAHOGANY_SLAB)
                .getFamily();

        BlockFamily BUBINGA = new BlockFamily.Builder(WatheBridgerBlocks.BUBINGA_PLANKS)
                .stairs(BUBINGA_STAIRS)
                .slab(BUBINGA_SLAB)
                .getFamily();

        BlockFamily BUBINGA_HERRINGBONE = new BlockFamily.Builder(WatheBridgerBlocks.BUBINGA_HERRINGBONE)
                .stairs(BUBINGA_HERRINGBONE_STAIRS)
                .slab(BUBINGA_HERRINGBONE_SLAB)
                .getFamily();

        BlockFamily SMOOTH_BUBINGA = new BlockFamily.Builder(WatheBridgerBlocks.SMOOTH_BUBINGA)
                .stairs(SMOOTH_BUBINGA_STAIRS)
                .slab(SMOOTH_BUBINGA_SLAB)
                .getFamily();

        BlockFamily EBONY = new BlockFamily.Builder(WatheBridgerBlocks.EBONY_PLANKS)
                .stairs(EBONY_STAIRS)
                .slab(EBONY_SLAB)
                .getFamily();

        BlockFamily EBONY_HERRINGBONE = new BlockFamily.Builder(WatheBridgerBlocks.EBONY_HERRINGBONE)
                .stairs(EBONY_HERRINGBONE_STAIRS)
                .slab(EBONY_HERRINGBONE_SLAB)
                .getFamily();

        BlockFamily SMOOTH_EBONY = new BlockFamily.Builder(WatheBridgerBlocks.SMOOTH_EBONY)
                .stairs(SMOOTH_EBONY_STAIRS)
                .slab(SMOOTH_EBONY_SLAB)
                .getFamily();
    }

    interface SetType {
        BlockSetType METAL_SHEET = BlockSetTypeBuilder.copyOf(BlockSetType.COPPER)
                .openableByHand(true)
                .openableByWindCharge(true)
                .buttonActivatedByArrows(true)
                .build(SRE.id("metal_sheet"));

    }

    static void initialize() {
        BranchBlock.STRIPPED_BRANCHES.put(STAINLESS_STEEL_BRANCH, STAINLESS_STEEL);
        BranchBlock.STRIPPED_BRANCHES.put(OAK_BRANCH, STRIPPED_OAK_BRANCH);
        BranchBlock.STRIPPED_BRANCHES.put(SPRUCE_BRANCH, STRIPPED_SPRUCE_BRANCH);
        BranchBlock.STRIPPED_BRANCHES.put(BIRCH_BRANCH, STRIPPED_BIRCH_BRANCH);
        BranchBlock.STRIPPED_BRANCHES.put(JUNGLE_BRANCH, STRIPPED_JUNGLE_BRANCH);
        BranchBlock.STRIPPED_BRANCHES.put(ACACIA_BRANCH, STRIPPED_ACACIA_BRANCH);
        BranchBlock.STRIPPED_BRANCHES.put(DARK_OAK_BRANCH, STRIPPED_DARK_OAK_BRANCH);
        BranchBlock.STRIPPED_BRANCHES.put(MANGROVE_BRANCH, STRIPPED_MANGROVE_BRANCH);
        BranchBlock.STRIPPED_BRANCHES.put(CHERRY_BRANCH, STRIPPED_CHERRY_BRANCH);
        BranchBlock.STRIPPED_BRANCHES.put(BAMBOO_POLE, STRIPPED_BAMBOO_POLE);
        BranchBlock.STRIPPED_BRANCHES.put(CRIMSON_STIPE, STRIPPED_CRIMSON_STIPE);
        BranchBlock.STRIPPED_BRANCHES.put(WARPED_STIPE, STRIPPED_WARPED_STIPE);

        FlammableBlockRegistry flammableBlockRegistry = FlammableBlockRegistry.getDefaultInstance();
        flammableBlockRegistry.add(OAK_BRANCH, 5, 20);
        flammableBlockRegistry.add(STRIPPED_OAK_BRANCH, 5, 20);
        flammableBlockRegistry.add(SPRUCE_BRANCH, 5, 20);
        flammableBlockRegistry.add(STRIPPED_SPRUCE_BRANCH, 5, 20);
        flammableBlockRegistry.add(BIRCH_BRANCH, 5, 20);
        flammableBlockRegistry.add(STRIPPED_BIRCH_BRANCH, 5, 20);
        flammableBlockRegistry.add(JUNGLE_BRANCH, 5, 20);
        flammableBlockRegistry.add(STRIPPED_JUNGLE_BRANCH, 5, 20);
        flammableBlockRegistry.add(ACACIA_BRANCH, 5, 20);
        flammableBlockRegistry.add(STRIPPED_ACACIA_BRANCH, 5, 20);
        flammableBlockRegistry.add(DARK_OAK_BRANCH, 5, 20);
        flammableBlockRegistry.add(STRIPPED_DARK_OAK_BRANCH, 5, 20);
        flammableBlockRegistry.add(MANGROVE_BRANCH, 5, 20);
        flammableBlockRegistry.add(STRIPPED_MANGROVE_BRANCH, 5, 20);
        flammableBlockRegistry.add(CHERRY_BRANCH, 5, 20);
        flammableBlockRegistry.add(STRIPPED_CHERRY_BRANCH, 5, 20);
        flammableBlockRegistry.add(BAMBOO_POLE, 5, 20);
        flammableBlockRegistry.add(STRIPPED_BAMBOO_POLE, 5, 20);

        registrar.registerEntries();

        BuiltInRegistries.BLOCK.addAlias(SRE.watheId("small_train_door"), SRE.id("navy_steel_door"));
        BuiltInRegistries.ITEM.addAlias(SRE.watheId("small_train_door"), SRE.id("navy_steel_door"));
    }
}
