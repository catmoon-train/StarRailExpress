package io.wifi.starrailexpress.datagen;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.block.*;
import io.wifi.starrailexpress.block.property.CouchArms;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.core.Direction;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.blockstates.*;
import net.minecraft.data.models.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class TMMModelGen extends FabricModelProvider {

    protected static final ModelTemplate THICK_BAR = new ModelTemplate(
            Optional.of(SRE.id("block/template_thick_bar")),
            Optional.empty(),
            TextureSlot.TEXTURE);
    protected static final ModelTemplate THICK_BAR_TOP = new ModelTemplate(
            Optional.of(SRE.id("block/template_thick_bar_top")),
            Optional.of("_top"),
            TextureSlot.TEXTURE);
    protected static final ModelTemplate THICK_BAR_BOTTOM = new ModelTemplate(
            Optional.of(SRE.id("block/template_thick_bar_bottom")),
            Optional.of("_bottom"),
            TextureSlot.TEXTURE);
    private static final TextureSlot SPYGLASS_KEY = TextureSlot.create("spyglass");
    private static final TextureSlot SHELF_KEY = TextureSlot.create("shelf");
    private static final ModelTemplate BRANCH_FACE = template(
            "block/template_branch_face", "_face", TextureSlot.SIDE
    );
    private static final ModelTemplate BRANCH_FACE_HORIZONTAL = template(
            "block/template_branch_face_horizontal", "_face_horizontal", TextureSlot.SIDE
    );
    private static final ModelTemplate BRANCH_FRONT = template(
            "block/template_branch_front", "_front", TextureSlot.TOP, TextureSlot.SIDE
    );
    private static final ModelTemplate BRANCH_BACK = template(
            "block/template_branch_back", "_back", TextureSlot.TOP, TextureSlot.SIDE
    );
    private static final ModelTemplate BRANCH_INVENTORY = template(
            "block/template_branch_inventory", "_inventory", TextureSlot.TOP, TextureSlot.SIDE
    );
    private static final ModelTemplate VENT_SHAFT_SIDE = template(
            "block/template_vent_shaft_side", TextureSlot.SIDE, TextureSlot.INSIDE
    );
    private static final ModelTemplate VENT_SHAFT_SIDE_VERTICAL = template(
            "block/template_vent_shaft_side_vertical", "_side_vertical", TextureSlot.SIDE, TextureSlot.INSIDE
    );
    private static final ModelTemplate VENT_SHAFT_SIDE_OPENING = template(
            "block/template_vent_shaft_side_opening", "_side_opening", TextureSlot.SIDE
    );
    private static final ModelTemplate VENT_SHAFT_INVENTORY = template(
            "block/template_vent_shaft_inventory", "_inventory", TextureSlot.SIDE, TextureSlot.END, TextureSlot.INSIDE
    );
    private static final ModelTemplate WALKWAY_TOP = template(
            "block/template_walkway_top", "_top", TextureSlot.SIDE, TextureSlot.TOP
    );
    private static final ModelTemplate WALKWAY_BOTTOM = template(
            "block/template_walkway_bottom", "_bottom", TextureSlot.SIDE, TextureSlot.TOP
    );
    private static final ModelTemplate LOUNGE_COUCH_LEFT = template(
            "block/template_lounge_couch_left", "_left", TextureSlot.TEXTURE
    );
    private static final ModelTemplate LOUNGE_COUCH_RIGHT = template(
            "block/template_lounge_couch_right", "_right", TextureSlot.TEXTURE
    );
    private static final ModelTemplate LOUNGE_COUCH_SINGLE = template(
            "block/template_lounge_couch_single", "_single", TextureSlot.TEXTURE
    );
    private static final ModelTemplate LOUNGE_COUCH_NO_ARMS = template(
            "block/template_lounge_couch_no_arms", "_no_arms", TextureSlot.TEXTURE
    );
    private static final ModelTemplate TRIMMED_STAIR_SUPPORT = template(
            "block/template_trimmed_stair_support", "_support", TextureSlot.TEXTURE
    );
    private static final ModelTemplate TRIMMED_STAIRS = template(
            "block/template_trimmed_stairs", TextureSlot.TEXTURE
    );
    private static final ModelTemplate PANEL = template(
            "block/template_panel", TextureSlot.ALL
    );
    private static final ModelTemplate LADDER = template(
            ResourceLocation.withDefaultNamespace("block/ladder"), TextureSlot.TEXTURE, TextureSlot.PARTICLE
    );
    private static final ModelTemplate TRIMMED_LANTERN_FLOOR = template(
            "block/template_trimmed_lantern_floor", TextureSlot.TOP, TextureSlot.BOTTOM, TextureSlot.SIDE
    );
    private static final ModelTemplate TRIMMED_LANTERN_CEILING = template(
            "block/template_trimmed_lantern_ceiling", TextureSlot.TOP, TextureSlot.BOTTOM, TextureSlot.SIDE
    );
    private static final ModelTemplate TRIMMED_LANTERN_WALL = template(
            "block/template_trimmed_lantern_wall", TextureSlot.TOP, TextureSlot.BOTTOM, TextureSlot.SIDE
    );
    private static final ModelTemplate WALL_LAMP_FLOOR = template(
            "block/template_wall_lamp_floor", TextureSlot.TEXTURE
    );
    private static final ModelTemplate WALL_LAMP_CEILING = template(
            "block/template_wall_lamp_ceiling", TextureSlot.TEXTURE
    );
    private static final ModelTemplate WALL_LAMP_WALL = template(
            "block/template_wall_lamp_wall", TextureSlot.TEXTURE
    );
    private static final ModelTemplate CARGO_BOX = template(
            "block/template_cargo_box", TextureSlot.TOP, TextureSlot.SIDE, TextureSlot.BOTTOM
    );
    private static final ModelTemplate CARGO_BOX_OPEN = template(
            "block/template_cargo_box_open", "_open", TextureSlot.TOP, TextureSlot.SIDE, TextureSlot.BOTTOM
    );
    private static final ModelTemplate CABINET_OPEN = template(
            "block/template_cabinet_open", "_open", TextureSlot.FRONT, TextureSlot.SIDE, TextureSlot.WALL, SHELF_KEY
    );
    private static final ModelTemplate GLASS_PANEL = template(
            "block/template_glass_panel", TextureSlot.PANE, TextureSlot.EDGE
    );
    private static final ModelTemplate LEATHER_COUCH_LEFT = template(
            "block/template_leather_couch_left", "_left", TextureSlot.TEXTURE
    );
    private static final ModelTemplate LEATHER_COUCH_RIGHT = template(
            "block/template_leather_couch_right", "_right", TextureSlot.TEXTURE
    );
    private static final ModelTemplate LEATHER_COUCH = template(
            "block/template_leather_couch", TextureSlot.TEXTURE
    );
    private static final ModelTemplate LEATHER_COUCH_MIDDLE = template(
            "block/template_leather_couch_middle", "_middle", TextureSlot.TEXTURE
    );
    private static final ModelTemplate ORNAMENT_R0 = template(
            "block/template_ornament_r0", TextureSlot.TEXTURE
    );
    private static final ModelTemplate ORNAMENT_R90 = template(
            "block/template_ornament_r90", TextureSlot.TEXTURE
    );
    private static final ModelTemplate ORNAMENT_R180 = template(
            "block/template_ornament_r180", TextureSlot.TEXTURE
    );
    private static final ModelTemplate ORNAMENT_R270 = template(
            "block/template_ornament_r270", TextureSlot.TEXTURE
    );
    private static final ModelTemplate LEDGE = template(
            "block/template_ledge", TextureSlot.TEXTURE
    );
    private static final ModelTemplate BAR = template(
            "block/template_bar", TextureSlot.TEXTURE
    );
    private static final ModelTemplate BAR_TOP = template(
            "block/template_bar_top", "_top", TextureSlot.TEXTURE
    );
    private static final ModelTemplate BAR_BOTTOM = template(
            "block/template_bar_bottom", "_bottom", TextureSlot.TEXTURE
    );
    private static final ModelTemplate TRIMMED_BED_FOOT = template(
            "block/template_trimmed_bed_foot", "_foot", TextureSlot.TEXTURE
    );
    private static final ModelTemplate TRIMMED_BED_HEAD = template(
            "block/template_trimmed_bed_head", "_head", TextureSlot.TEXTURE
    );
    private static final ModelTemplate TRIMMED_BED_INVENTORY = template(
            "block/template_trimmed_bed_inventory", "_inventory", TextureSlot.TEXTURE
    );
    private static final ModelTemplate SPRINKLER = template(
            "block/template_sprinkler", TextureSlot.TEXTURE, TextureSlot.PARTICLE
    );
    private static final ModelTemplate VENT_HATCH = template(
            "block/template_vent_hatch", TextureSlot.TEXTURE
    );
    private static final ModelTemplate VENT_HATCH_OPEN = template(
            "block/template_vent_hatch_open", "_open", TextureSlot.TEXTURE
    );


    private final Map<Block, TexturedModel> uniqueModels = ImmutableMap.<Block, TexturedModel>builder()
            .build();

    public TMMModelGen(FabricDataOutput output) {
        super(output);
    }

    private static ModelTemplate template(ResourceLocation parent, @Nullable String variant, TextureSlot... requiredTextureKeys) {
        return new ModelTemplate(Optional.of(parent), Optional.ofNullable(variant), requiredTextureKeys);
    }

    private static ModelTemplate template(ResourceLocation parent, TextureSlot... requiredTextureKeys) {
        return template(parent, null, requiredTextureKeys);
    }

    private static ModelTemplate template(String parentName, @Nullable String variant, TextureSlot... requiredTextureKeys) {
        return template(SRE.id(parentName), variant, requiredTextureKeys);
    }

    private static ModelTemplate template(String parentName, TextureSlot... requiredTextureKeys) {
        return template(SRE.id(parentName), requiredTextureKeys);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generator) {
        this.registerVentShaft(generator, TMMBlocks.STAINLESS_STEEL_VENT_SHAFT);
        this.registerVentHatch(generator, TMMBlocks.STAINLESS_STEEL_VENT_HATCH);
        this.registerVentShaft(generator, TMMBlocks.DARK_STEEL_VENT_SHAFT);
        this.registerVentHatch(generator, TMMBlocks.DARK_STEEL_VENT_HATCH);
        this.registerVentShaft(generator, TMMBlocks.TARNISHED_GOLD_VENT_SHAFT);
        this.registerVentHatch(generator, TMMBlocks.TARNISHED_GOLD_VENT_HATCH);
        this.registerFamily(generator, TMMBlocks.Family.TARNISHED_GOLD);
        this.registerFamily(generator, TMMBlocks.Family.GOLD);
        this.registerFamily(generator, TMMBlocks.Family.PRISTINE_GOLD);
        generator.createRotatedPillarWithHorizontalVariant(TMMBlocks.TARNISHED_GOLD_PILLAR, TexturedModel.COLUMN_ALT, TexturedModel.COLUMN_HORIZONTAL_ALT);
        generator.createRotatedPillarWithHorizontalVariant(TMMBlocks.GOLD_PILLAR, TexturedModel.COLUMN_ALT, TexturedModel.COLUMN_HORIZONTAL_ALT);
        generator.createRotatedPillarWithHorizontalVariant(TMMBlocks.PRISTINE_GOLD_PILLAR, TexturedModel.COLUMN_ALT, TexturedModel.COLUMN_HORIZONTAL_ALT);
        this.registerFamily(generator, TMMBlocks.Family.METAL_SHEET);
        generator.createDoor(TMMBlocks.COCKPIT_DOOR);
        this.registerWalkway(generator, TMMBlocks.METAL_SHEET_WALKWAY);
        this.registerLadder(generator, TMMBlocks.STAINLESS_STEEL_LADDER);
        this.registerFamily(generator, TMMBlocks.Family.STAINLESS_STEEL);
        this.registerWalkway(generator, TMMBlocks.STAINLESS_STEEL_WALKWAY);
        this.registerBranch(generator, TMMBlocks.STAINLESS_STEEL_BRANCH, TMMBlocks.STAINLESS_STEEL);
        generator.createRotatedPillarWithHorizontalVariant(TMMBlocks.STAINLESS_STEEL_PILLAR, TexturedModel.COLUMN_ALT, TexturedModel.COLUMN_HORIZONTAL_ALT);
        this.registerFamily(generator, TMMBlocks.Family.DARK_STEEL);
        this.registerWalkway(generator, TMMBlocks.DARK_STEEL_WALKWAY);
        this.registerBranch(generator, TMMBlocks.DARK_STEEL_BRANCH, TMMBlocks.DARK_STEEL);
        generator.createRotatedPillarWithHorizontalVariant(TMMBlocks.DARK_STEEL_PILLAR, TexturedModel.COLUMN_ALT, TexturedModel.COLUMN_HORIZONTAL_ALT);
        generator.createTrivialCube(TMMBlocks.RHOMBUS_GLASS);
        this.registerGlassPanel(generator, TMMBlocks.GOLDEN_GLASS_PANEL);
        this.registerCullingGlass(generator);
        this.registerFamily(generator, TMMBlocks.Family.MARBLE);
        this.registerFamily(generator, TMMBlocks.Family.MARBLE_TILE);
        this.registerFamily(generator, TMMBlocks.Family.DARK_MARBLE);
        generator.createColoredBlockWithStateRotations(TexturedModel.GLAZED_TERRACOTTA, TMMBlocks.MARBLE_MOSAIC);
        this.registerFamily(generator, TMMBlocks.Family.WHITE_HULL);
        this.registerCulledBlock(generator, TMMBlocks.CULLING_WHITE_HULL, TMMBlocks.WHITE_HULL);
        this.registerFamily(generator, TMMBlocks.Family.BLACK_HULL);
        this.registerCulledBlock(generator, TMMBlocks.CULLING_BLACK_HULL, TMMBlocks.BLACK_HULL);
        this.registerFamily(generator, TMMBlocks.Family.BLACK_HULL_SHEET);
        this.registerFamily(generator, TMMBlocks.Family.MAHOGANY);
        this.registerFamily(generator, TMMBlocks.Family.MAHOGANY_HERRINGBONE);
        this.registerFamily(generator, TMMBlocks.Family.SMOOTH_MAHOGANY);
        this.registerPanel(generator, TMMBlocks.MAHOGANY_PANEL, TMMBlocks.SMOOTH_MAHOGANY);
        this.registerCabinet(generator, TMMBlocks.MAHOGANY_CABINET);
        this.registerVariedBookshelf(generator, TMMBlocks.MAHOGANY_BOOKSHELF, TMMBlocks.MAHOGANY_PLANKS);
        this.registerFamily(generator, TMMBlocks.Family.BUBINGA);
        this.registerFamily(generator, TMMBlocks.Family.BUBINGA_HERRINGBONE);
        this.registerFamily(generator, TMMBlocks.Family.SMOOTH_BUBINGA);
        this.registerPanel(generator, TMMBlocks.BUBINGA_PANEL, TMMBlocks.SMOOTH_BUBINGA);
        this.registerCabinet(generator, TMMBlocks.BUBINGA_CABINET);
        this.registerVariedBookshelf(generator, TMMBlocks.BUBINGA_BOOKSHELF, TMMBlocks.BUBINGA_PLANKS);
        this.registerFamily(generator, TMMBlocks.Family.EBONY);
        this.registerFamily(generator, TMMBlocks.Family.EBONY_HERRINGBONE);
        this.registerFamily(generator, TMMBlocks.Family.SMOOTH_EBONY);
        this.registerPanel(generator, TMMBlocks.EBONY_PANEL, TMMBlocks.SMOOTH_EBONY);
        this.registerCabinet(generator, TMMBlocks.EBONY_CABINET);
        this.registerTrimmedStairs(generator, TMMBlocks.TRIMMED_EBONY_STAIRS);
        this.registerVariedBookshelf(generator, TMMBlocks.EBONY_BOOKSHELF, TMMBlocks.EBONY_PLANKS);
        this.registerBranch(generator, TMMBlocks.OAK_BRANCH, Blocks.OAK_LOG);
        this.registerBranch(generator, TMMBlocks.SPRUCE_BRANCH, Blocks.SPRUCE_LOG);
        this.registerBranch(generator, TMMBlocks.BIRCH_BRANCH, Blocks.BIRCH_LOG);
        this.registerBranch(generator, TMMBlocks.JUNGLE_BRANCH, Blocks.JUNGLE_LOG);
        this.registerBranch(generator, TMMBlocks.ACACIA_BRANCH, Blocks.ACACIA_LOG);
        this.registerBranch(generator, TMMBlocks.DARK_OAK_BRANCH, Blocks.DARK_OAK_LOG);
        this.registerBranch(generator, TMMBlocks.MANGROVE_BRANCH, Blocks.MANGROVE_LOG);
        this.registerBranch(generator, TMMBlocks.CHERRY_BRANCH, Blocks.CHERRY_LOG);
        this.registerPole(generator, TMMBlocks.BAMBOO_POLE, Blocks.BAMBOO_BLOCK);
        this.registerBranch(generator, TMMBlocks.CRIMSON_STIPE, Blocks.CRIMSON_STEM);
        this.registerBranch(generator, TMMBlocks.WARPED_STIPE, Blocks.WARPED_STEM);
        this.registerBranch(generator, TMMBlocks.STRIPPED_OAK_BRANCH, Blocks.STRIPPED_OAK_LOG);
        this.registerBranch(generator, TMMBlocks.STRIPPED_SPRUCE_BRANCH, Blocks.STRIPPED_SPRUCE_LOG);
        this.registerBranch(generator, TMMBlocks.STRIPPED_BIRCH_BRANCH, Blocks.STRIPPED_BIRCH_LOG);
        this.registerBranch(generator, TMMBlocks.STRIPPED_JUNGLE_BRANCH, Blocks.STRIPPED_JUNGLE_LOG);
        this.registerBranch(generator, TMMBlocks.STRIPPED_ACACIA_BRANCH, Blocks.STRIPPED_ACACIA_LOG);
        this.registerBranch(generator, TMMBlocks.STRIPPED_DARK_OAK_BRANCH, Blocks.STRIPPED_DARK_OAK_LOG);
        this.registerBranch(generator, TMMBlocks.STRIPPED_MANGROVE_BRANCH, Blocks.STRIPPED_MANGROVE_LOG);
        this.registerBranch(generator, TMMBlocks.STRIPPED_CHERRY_BRANCH, Blocks.STRIPPED_CHERRY_LOG);
        this.registerPole(generator, TMMBlocks.STRIPPED_BAMBOO_POLE, Blocks.STRIPPED_BAMBOO_BLOCK);
        this.registerBranch(generator, TMMBlocks.STRIPPED_CRIMSON_STIPE, Blocks.STRIPPED_CRIMSON_STEM);
        this.registerBranch(generator, TMMBlocks.STRIPPED_WARPED_STIPE, Blocks.STRIPPED_WARPED_STEM);
        this.registerHorizontalAxisBlock(generator, TMMBlocks.PANEL_STRIPES, false);
        this.registerHorizontalAxisBlock(generator, TMMBlocks.RAIL_BEAM, true);
        this.registerRailing(generator, TMMBlocks.TRIMMED_RAILING, TMMBlocks.TRIMMED_RAILING_POST, TMMBlocks.DIAGONAL_TRIMMED_RAILING);
        this.registerCargoBox(generator, TMMBlocks.CARGO_BOX);
        this.registerLoungeCouch(generator, TMMBlocks.WHITE_LOUNGE_COUCH);
        generator.createNonTemplateHorizontalBlock(TMMBlocks.WHITE_OTTOMAN);
        this.registerBed(generator, TMMBlocks.WHITE_TRIMMED_BED);
        this.registerBed(generator, TMMBlocks.RED_TRIMMED_BED);
        this.registerLoungeCouch(generator, TMMBlocks.BLUE_LOUNGE_COUCH);
        this.registerLoungeCouch(generator, TMMBlocks.GREEN_LOUNGE_COUCH);
        this.registerLeatherCouch(generator, TMMBlocks.RED_LEATHER_COUCH);
        this.registerLeatherCouch(generator, TMMBlocks.BROWN_LEATHER_COUCH);
        this.registerLeatherCouch(generator, TMMBlocks.BEIGE_LEATHER_COUCH);
        generator.createNonTemplateModelBlock(TMMBlocks.COFFEE_TABLE);
        generator.createNonTemplateModelBlock(TMMBlocks.BAR_TABLE);
        generator.createNonTemplateModelBlock(TMMBlocks.BAR_STOOL);
        this.registerBar(generator, TMMBlocks.GOLD_BAR);
        this.registerLedge(generator, TMMBlocks.GOLD_LEDGE, TMMBlocks.GOLD_BAR);
        this.registerBar(generator, TMMBlocks.STAINLESS_STEEL_BAR);
        this.registerTrimmedLantern(generator, TMMBlocks.TRIMMED_LANTERN, false);
        this.registerWallLamp(generator, TMMBlocks.WALL_LAMP, false);
        this.registerNeonPillar(generator, TMMBlocks.NEON_PILLAR, false);
        this.registerNeonTube(generator, TMMBlocks.NEON_TUBE, false);
        this.registerSprinkler(generator, TMMBlocks.STAINLESS_STEEL_SPRINKLER, TMMBlocks.STAINLESS_STEEL);
        this.registerSprinkler(generator, TMMBlocks.GOLD_SPRINKLER, TMMBlocks.GOLD);
        this.registerButton(generator, TMMBlocks.SMALL_BUTTON);
        this.registerButton(generator, TMMBlocks.ELEVATOR_BUTTON);
        this.registerOrnament(generator, TMMBlocks.GOLD_ORNAMENT);
        this.registerParticleBlockWithItemSprite(generator, TMMBlocks.SMALL_WOOD_DOOR, TMMBlocks.SMOOTH_EBONY);
        this.registerParticleBlockWithItemSprite(generator, TMMBlocks.SMALL_GLASS_DOOR, TMMBlocks.TARNISHED_GOLD_PILLAR);

        this.registerHullGlass(generator, TMMBlocks.HULL_GLASS);
        generator.createTrivialCube(TMMBlocks.RHOMBUS_HULL_GLASS);
        this.registerPrivacyGlassPanel(generator, TMMBlocks.PRIVACY_GLASS_PANEL);

        registerFancySteel(generator, TMMBlocks.ANTHRACITE_STEEL, TMMBlocks.SMOOTH_ANTHRACITE_STEEL, TMMBlocks.ANTHRACITE_STEEL_TILES, TMMBlocks.ANTHRACITE_STEEL_PANEL, TMMBlocks.ANTHRACITE_STEEL_TILES_PANEL, TMMBlocks.SMOOTH_ANTHRACITE_STEEL_PANEL, TMMBlocks.ANTHRACITE_STEEL_DOOR, TMMBlocks.Family.SMOOTH_ANTHRACITE_STEEL);
        registerFancySteel(generator, TMMBlocks.KHAKI_STEEL, TMMBlocks.SMOOTH_KHAKI_STEEL, TMMBlocks.KHAKI_STEEL_TILES, TMMBlocks.KHAKI_STEEL_PANEL, TMMBlocks.KHAKI_STEEL_TILES_PANEL, TMMBlocks.SMOOTH_KHAKI_STEEL_PANEL, TMMBlocks.KHAKI_STEEL_DOOR, TMMBlocks.Family.SMOOTH_KHAKI_STEEL);
        registerFancySteel(generator, TMMBlocks.MAROON_STEEL, TMMBlocks.SMOOTH_MAROON_STEEL, TMMBlocks.MAROON_STEEL_TILES, TMMBlocks.MAROON_STEEL_PANEL, TMMBlocks.MAROON_STEEL_TILES_PANEL, TMMBlocks.SMOOTH_MAROON_STEEL_PANEL, TMMBlocks.MAROON_STEEL_DOOR, TMMBlocks.Family.SMOOTH_MAROON_STEEL);
        registerFancySteel(generator, TMMBlocks.MUNTZ_STEEL, TMMBlocks.SMOOTH_MUNTZ_STEEL, TMMBlocks.MUNTZ_STEEL_TILES, TMMBlocks.MUNTZ_STEEL_PANEL, TMMBlocks.MUNTZ_STEEL_TILES_PANEL, TMMBlocks.SMOOTH_MUNTZ_STEEL_PANEL, TMMBlocks.MUNTZ_STEEL_DOOR, TMMBlocks.Family.SMOOTH_MUNTZ_STEEL);
        registerFancySteel(generator, TMMBlocks.NAVY_STEEL, TMMBlocks.SMOOTH_NAVY_STEEL, TMMBlocks.NAVY_STEEL_TILES, TMMBlocks.NAVY_STEEL_PANEL, TMMBlocks.NAVY_STEEL_TILES_PANEL, TMMBlocks.SMOOTH_NAVY_STEEL_PANEL, TMMBlocks.NAVY_STEEL_DOOR, TMMBlocks.Family.SMOOTH_NAVY_STEEL);

        this.registerParticleBlockWithItemSprite(generator, TMMBlocks.WHEEL, TMMBlocks.DARK_STEEL);
        this.registerParticleBlockWithItemSprite(generator, TMMBlocks.RUSTED_WHEEL, TMMBlocks.DARK_STEEL);
        generator.createTrivialCube(TMMBlocks.RED_MOQUETTE);
        generator.createTrivialCube(TMMBlocks.BROWN_MOQUETTE);
        generator.createTrivialCube(TMMBlocks.BLUE_MOQUETTE);
        generator.createNonTemplateModelBlock(TMMBlocks.FOOD_PLATTER);
        generator.createNonTemplateHorizontalBlock(TMMBlocks.DRINK_TRAY);
        this.registerPanel(generator, TMMBlocks.BARRIER_PANEL, TextureMapping.getBlockTexture(TMMBlocks.BARRIER_PANEL));


        generator.createAirLikeBlock(TMMBlocks.LIGHT_BARRIER, TMMBlocks.LIGHT_BARRIER.asItem());
        generator.createSimpleFlatItemModel(TMMBlocks.LIGHT_BARRIER.asItem());
        generator.createNonTemplateHorizontalBlock(TMMBlocks.HORN);
        generator.createSimpleFlatItemModel(TMMBlocks.CHIMNEY.asItem());
    }

    private void registerFancySteel(BlockModelGenerators generator, Block block, Block smooth, Block tiles, Block panel, Block tilesPanel, Block smoothPanel, Block door, BlockFamily family) {
        generator.createTrivialCube(block);
        this.registerPanel(generator, panel, block);
        generator.createTrivialCube(tiles);
        this.registerPanel(generator, tilesPanel, tiles);
        this.registerFamily(generator, family);
        this.registerPanel(generator, smoothPanel, smooth);
        this.registerParticleBlockWithItemSprite(generator, door, block);
    }

    public static final ModelTemplate SMALL_ITEM = item("small_item", TextureSlot.LAYER0);

    private static ModelTemplate item(String parent, TextureSlot... requiredTextureKeys) {
        return new ModelTemplate(Optional.of(SRE.id("item/" + parent)), Optional.empty(), requiredTextureKeys);
    }

    @Override
    public void generateItemModels(ItemModelGenerators generator) {
        generator.generateFlatItem(TMMItems.POISON_VIAL, SMALL_ITEM);
        generator.generateFlatItem(TMMItems.SCORPION, SMALL_ITEM);
        generator.generateFlatItem(TMMItems.OLD_FASHIONED, SMALL_ITEM);
        generator.generateFlatItem(TMMItems.MOJITO, SMALL_ITEM);
        generator.generateFlatItem(TMMItems.MARTINI, SMALL_ITEM);
        generator.generateFlatItem(TMMItems.COSMOPOLITAN, SMALL_ITEM);
        generator.generateFlatItem(TMMItems.CHAMPAGNE, SMALL_ITEM);
        generator.generateFlatItem(TMMItems.GRENADE, SMALL_ITEM);
        generator.generateFlatItem(TMMItems.FIRECRACKER, SMALL_ITEM);
    }

    private Variant variant() {
        return Variant.variant();
    }

    private <T> Variant variant(VariantProperty<T> variantSetting, T value) {
        return this.variant().with(variantSetting, value);
    }

    private <T> Variant variant(ResourceLocation model, VariantProperty<T> variantSetting, T value) {
        return this.model(model).with(variantSetting, value);
    }

    private Variant model(ResourceLocation model) {
        return this.variant(VariantProperties.MODEL, model);
    }

    private void registerBranch(BlockModelGenerators generator, Block branch, Block log) {
        this.registerBranch(generator, branch, log, TextureMapping.getBlockTexture(branch, "_top"));
    }

    private void registerPole(BlockModelGenerators generator, Block pole, Block block) {
        this.registerBranch(generator, pole, block, TextureMapping.getBlockTexture(block, "_top"));
    }

    private void registerBranch(BlockModelGenerators generator, Block branch, Block log, ResourceLocation topTexture) {
        TextureMapping faceMap = new TextureMapping().put(TextureSlot.SIDE, TextureMapping.getBlockTexture(log));
        TextureMapping map = faceMap.copyAndUpdate(TextureSlot.TOP, topTexture);
        ResourceLocation face = BRANCH_FACE.create(branch, faceMap, generator.modelOutput);
        ResourceLocation faceHorizontal = BRANCH_FACE_HORIZONTAL.create(branch, faceMap, generator.modelOutput);
        ResourceLocation front = BRANCH_FRONT.create(branch, map, generator.modelOutput);
        ResourceLocation back = BRANCH_BACK.create(branch, map, generator.modelOutput);
        ResourceLocation inventory = BRANCH_INVENTORY.create(branch, map, generator.modelOutput);
        generator.delegateItemModel(branch, inventory);
        MultiPartGenerator blockStateSupplier = MultiPartGenerator.multiPart(branch);
        for (Direction side : Direction.values())
            this.addBranchSide(blockStateSupplier, side, face, faceHorizontal, front, back);
        generator.blockStateOutput.accept(blockStateSupplier);
    }

    private Variant rotateBranchSide(Variant variant, Direction side) {
        return switch (side.getAxis()) {
            case X -> variant.with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90);
            case Y -> variant.with(VariantProperties.X_ROT, VariantProperties.Rotation.R270);
            case Z -> variant;
        };
    }

    private void addBranchSide(MultiPartGenerator blockStateSupplier, Direction side, ResourceLocation face, ResourceLocation faceHorizontal, ResourceLocation front, ResourceLocation back) {
        BooleanProperty sideProperty = BranchBlock.PROPERTY_BY_DIRECTION.get(side);
        BooleanProperty horizontalProperty1 = BranchBlock.PROPERTY_BY_DIRECTION.get(side.getAxis().isVertical() ? Direction.EAST : side.getClockWise());
        BooleanProperty horizontalProperty2 = BranchBlock.PROPERTY_BY_DIRECTION.get(side.getAxis().isVertical() ? Direction.WEST : side.getCounterClockWise());
        boolean isFront = side.getAxisDirection() == (side.getAxis().equals(Direction.Axis.Z) ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
        blockStateSupplier.with(
                Condition.condition().term(sideProperty, true),
                this.rotateBranchSide(Variant.variant().with(VariantProperties.MODEL, isFront ? front : back), side)
        ).with(
                Condition.condition().term(sideProperty, false).term(horizontalProperty1, false).term(horizontalProperty2, false),
                this.rotateForFace(Variant.variant().with(VariantProperties.MODEL, face), side, false)
        ).with(
                Condition.condition().term(sideProperty, false).term(horizontalProperty1, true).term(horizontalProperty2, false),
                this.rotateForFace(Variant.variant().with(VariantProperties.MODEL, face), side, false)
        ).with(
                Condition.condition().term(sideProperty, false).term(horizontalProperty1, false).term(horizontalProperty2, true),
                this.rotateForFace(Variant.variant().with(VariantProperties.MODEL, face), side, false)
        ).with(
                Condition.condition().term(sideProperty, false).term(horizontalProperty1, true).term(horizontalProperty2, true),
                this.rotateForFace(Variant.variant().with(VariantProperties.MODEL, faceHorizontal), side, false)
        );
    }

    private Variant rotateForFace(Variant variant, Direction direction, boolean uvlock) {
        if (uvlock) {
            variant.with(VariantProperties.UV_LOCK, true);
        }
        switch (direction) {
            case EAST -> variant.with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90);
            case SOUTH -> variant.with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180);
            case WEST -> variant.with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270);
            case UP -> variant.with(VariantProperties.X_ROT, VariantProperties.Rotation.R270);
            case DOWN -> variant.with(VariantProperties.X_ROT, VariantProperties.Rotation.R90);
        }
        return variant;
    }

    private Variant rotateForFace(Variant variant, Direction direction) {
        return this.rotateForFace(variant, direction, false);
    }

    private void registerVentShaft(BlockModelGenerators generator, Block block) {
        TextureMapping openingTexture = new TextureMapping().put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_opening"));
        TextureMapping sideTexture = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                .put(TextureSlot.INSIDE, TextureMapping.getBlockTexture(block, "_inside"));
        TextureMapping junctionTexture = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_junction"))
                .put(TextureSlot.INSIDE, TextureMapping.getBlockTexture(block, "_junction_inside"));
        TextureMapping inventoryTexture = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                .put(TextureSlot.INSIDE, TextureMapping.getBlockTexture(block, "_inside"))
                .put(TextureSlot.END, TextureMapping.getBlockTexture(block, "_opening"));
        ResourceLocation openingModel = VENT_SHAFT_SIDE_OPENING.create(block, openingTexture, generator.modelOutput);
        ResourceLocation sideModel = VENT_SHAFT_SIDE.createWithSuffix(block, "_side", sideTexture, generator.modelOutput);
        ResourceLocation verticalModel = VENT_SHAFT_SIDE_VERTICAL.create(block, sideTexture, generator.modelOutput);
        ResourceLocation junctionModel = VENT_SHAFT_SIDE.createWithSuffix(block, "_junction", junctionTexture, generator.modelOutput);

        ResourceLocation inventoryModel = VENT_SHAFT_INVENTORY.create(block, inventoryTexture, generator.modelOutput);
        generator.delegateItemModel(block, inventoryModel);

        MultiPartGenerator blockStateSupplier = MultiPartGenerator.multiPart(block);
        for (Direction direction : Direction.values()) {
            boolean horizontalAxis = direction.getAxis().isHorizontal();
            boolean isUp = direction == Direction.UP;
            Direction leftDirection = horizontalAxis ? direction.getClockWise() : Direction.EAST;
            Direction topDirection = horizontalAxis ? Direction.UP : isUp ? Direction.SOUTH : Direction.NORTH;

            BooleanProperty property = PipeBlock.PROPERTY_BY_DIRECTION.get(direction);
            BooleanProperty left = PipeBlock.PROPERTY_BY_DIRECTION.get(leftDirection);
            BooleanProperty right = PipeBlock.PROPERTY_BY_DIRECTION.get(leftDirection.getOpposite());
            BooleanProperty top = PipeBlock.PROPERTY_BY_DIRECTION.get(topDirection);
            BooleanProperty bottom = PipeBlock.PROPERTY_BY_DIRECTION.get(topDirection.getOpposite());

            Condition whenSide = Condition.condition().term(property, true);
            Condition whenVertical = Condition.and(Condition.condition().term(top, false), Condition.condition().term(bottom, false));
            Condition whenNotVertical = Condition.and(Condition.condition().term(top, true), Condition.condition().term(bottom, true));
            Condition whenHorizontal = Condition.and(Condition.condition().term(left, false), Condition.condition().term(right, false));
            Condition whenNotHorizontal = Condition.and(Condition.condition().term(left, true), Condition.condition().term(right, true));

            this.addVentSide(blockStateSupplier, direction, openingModel, Condition.condition().term(property, false));
            this.addVentSide(blockStateSupplier, direction, sideModel, Condition.and(whenSide, whenHorizontal, whenNotVertical));
            this.addVentSide(blockStateSupplier, direction, verticalModel, Condition.and(whenSide, whenVertical, whenNotHorizontal));
            this.addVentSide(blockStateSupplier, direction, junctionModel, Condition.and(whenSide, Condition.or(
                    Condition.condition().term(top, true).term(bottom, false).term(left, false).term(right, false),
                    Condition.condition().term(top, false).term(bottom, true).term(left, false).term(right, false),
                    Condition.condition().term(top, false).term(bottom, false).term(left, true).term(right, false),
                    Condition.condition().term(top, false).term(bottom, false).term(left, false).term(right, true),
                    Condition.condition().term(top, false).term(bottom, false).term(left, false).term(right, false),
                    Condition.and(
                            Condition.or(Condition.condition().term(top, true), Condition.condition().term(bottom, true)),
                            Condition.or(Condition.condition().term(left, true), Condition.condition().term(right, true))
                    )
            )));

        }
        generator.blockStateOutput.accept(blockStateSupplier);
    }

    private void addVentSide(MultiPartGenerator blockStateSupplier, Direction direction, ResourceLocation model, Condition when) {
        blockStateSupplier.with(when, this.rotateForFace(Variant.variant().with(VariantProperties.MODEL, model), direction, false));
    }

    private void registerItemParticleBlock(BlockModelGenerators generator, Block block) {
        ResourceLocation model = ModelTemplates.PARTICLE_ONLY.create(ModelLocationUtils.getModelLocation(block), TextureMapping.particleFromItem(block.asItem()), generator.modelOutput);
        generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, model));
    }

    private void registerParticleBlockWithItemSprite(BlockModelGenerators generator, Block block, Block particleBlock) {
        ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(block.asItem()), TextureMapping.layer0(block.asItem()), generator.modelOutput);
        this.registerParticleBlock(generator, block, particleBlock);
    }

    private void registerParticleBlock(BlockModelGenerators generator, Block block, Block particleBlock) {
        ResourceLocation model = ModelTemplates.PARTICLE_ONLY.create(ModelLocationUtils.getModelLocation(block), TextureMapping.particle(particleBlock), generator.modelOutput);
        generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, model));
    }

    private void registerFamily(BlockModelGenerators generator, BlockFamily family) {
        TexturedModel texturedModel = this.uniqueModels.getOrDefault(family.getBaseBlock(), TexturedModel.CUBE.get(family.getBaseBlock()));
        generator.new BlockFamilyProvider(texturedModel.getMapping()).fullBlock(family.getBaseBlock(), texturedModel.getTemplate()).generateFor(family);
    }

    private void registerWalkway(BlockModelGenerators generator, Block block) {
        TextureMapping textureMap = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block));
        ResourceLocation top = WALKWAY_TOP.createWithSuffix(block, "_top", textureMap, generator.modelOutput);
        ResourceLocation bottom = WALKWAY_BOTTOM.createWithSuffix(block, "_bottom", textureMap, generator.modelOutput);
        generator.delegateItemModel(block, bottom);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block)
                .with(PropertyDispatch.property(WalkwayBlock.HALF)
                        .select(Half.TOP, Variant.variant().with(VariantProperties.MODEL, top))
                        .select(Half.BOTTOM, Variant.variant().with(VariantProperties.MODEL, bottom))));
    }

    private void registerCouch(BlockModelGenerators generator, Block block, ModelTemplate left, ModelTemplate right, ModelTemplate single, ModelTemplate noArms) {
        TextureMapping textureMap = new TextureMapping()
                .put(TextureSlot.TEXTURE, TextureMapping.getBlockTexture(block));
        ResourceLocation leftModel = left.create(block, textureMap, generator.modelOutput);
        ResourceLocation rightModel = right.create(block, textureMap, generator.modelOutput);
        ResourceLocation singleModel = single.create(block, textureMap, generator.modelOutput);
        ResourceLocation noArmsModel = noArms.create(block, textureMap, generator.modelOutput);
        generator.delegateItemModel(block, singleModel);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block)
                .with(PropertyDispatch.property(CouchBlock.ARMS)
                        .select(CouchArms.LEFT, Variant.variant().with(VariantProperties.MODEL, leftModel))
                        .select(CouchArms.RIGHT, Variant.variant().with(VariantProperties.MODEL, rightModel))
                        .select(CouchArms.SINGLE, Variant.variant().with(VariantProperties.MODEL, singleModel))
                        .select(CouchArms.NO_ARMS, Variant.variant().with(VariantProperties.MODEL, noArmsModel)))
                .with(BlockModelGenerators.createHorizontalFacingDispatch()));
    }

    private void registerLoungeCouch(BlockModelGenerators generator, Block block) {
        this.registerCouch(generator, block, LOUNGE_COUCH_LEFT, LOUNGE_COUCH_RIGHT, LOUNGE_COUCH_SINGLE, LOUNGE_COUCH_NO_ARMS);
    }

    private void registerLeatherCouch(BlockModelGenerators generator, Block block) {
        this.registerCouch(generator, block, LEATHER_COUCH_LEFT, LEATHER_COUCH_RIGHT, LEATHER_COUCH, LEATHER_COUCH_MIDDLE);
    }

    private void registerTrimmedStairs(BlockModelGenerators generator, Block block) {
        TextureMapping supportTexture = TextureMapping.defaultTexture(ModelLocationUtils.getModelLocation(block, "_support"));
        TextureMapping singleTexture = TextureMapping.defaultTexture(ModelLocationUtils.getModelLocation(block, "_single"));
        TextureMapping leftTexture = TextureMapping.defaultTexture(ModelLocationUtils.getModelLocation(block, "_left"));
        TextureMapping rightTexture = TextureMapping.defaultTexture(ModelLocationUtils.getModelLocation(block, "_right"));
        TextureMapping middleTexture = TextureMapping.defaultTexture(ModelLocationUtils.getModelLocation(block, "_middle"));
        ResourceLocation support = TRIMMED_STAIR_SUPPORT.create(block, supportTexture, generator.modelOutput);
        ResourceLocation single = TRIMMED_STAIRS.createWithSuffix(block, "_single", singleTexture, generator.modelOutput);
        ResourceLocation left = TRIMMED_STAIRS.createWithSuffix(block, "_left", leftTexture, generator.modelOutput);
        ResourceLocation right = TRIMMED_STAIRS.createWithSuffix(block, "_right", rightTexture, generator.modelOutput);
        ResourceLocation middle = TRIMMED_STAIRS.createWithSuffix(block, "_middle", middleTexture, generator.modelOutput);
        MultiPartGenerator blockStateSupplier = MultiPartGenerator.multiPart(block);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            blockStateSupplier.with(Condition.condition()
                            .term(TrimmedStairsBlock.FACING, direction)
                            .term(TrimmedStairsBlock.LEFT, false)
                            .term(TrimmedStairsBlock.RIGHT, false),
                    this.rotateForFace(this.model(middle), direction)
            );
            blockStateSupplier.with(Condition.condition()
                            .term(TrimmedStairsBlock.FACING, direction)
                            .term(TrimmedStairsBlock.LEFT, true)
                            .term(TrimmedStairsBlock.RIGHT, false),
                    this.rotateForFace(this.model(left), direction)
            );
            blockStateSupplier.with(Condition.condition()
                            .term(TrimmedStairsBlock.FACING, direction)
                            .term(TrimmedStairsBlock.LEFT, false)
                            .term(TrimmedStairsBlock.RIGHT, true),
                    this.rotateForFace(this.model(right), direction)
            );
            blockStateSupplier.with(Condition.condition()
                            .term(TrimmedStairsBlock.FACING, direction)
                            .term(TrimmedStairsBlock.LEFT, true)
                            .term(TrimmedStairsBlock.RIGHT, true),
                    this.rotateForFace(this.model(single), direction)
            );
            blockStateSupplier.with(Condition.condition()
                            .term(TrimmedStairsBlock.FACING, direction)
                            .term(TrimmedStairsBlock.SUPPORT, true),
                    this.rotateForFace(this.model(support), direction)
            );
        }
        generator.delegateItemModel(block, single);
        generator.blockStateOutput.accept(blockStateSupplier);
    }

    private void registerLiquid(BlockModelGenerators generator, Item item, ResourceLocation texture) {
        // Leaves base model has a tintIndex of 0 for all faces
        ModelTemplates.LEAVES.create(ModelLocationUtils.getModelLocation(item), TextureMapping.cube(texture), generator.modelOutput);
    }

    private void registerColumn(BlockModelGenerators generator, Block block) {
        TextureMapping textureMap = TextureMapping.column(block);
        ResourceLocation model = ModelTemplates.CUBE_COLUMN.create(block, textureMap, generator.modelOutput);
        generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, model));
    }

    private void registerVariedBookshelf(BlockModelGenerators generator, Block block, Block planks) {
        TextureMapping textureMap = new TextureMapping()
                .put(TextureSlot.END, TextureMapping.getBlockTexture(planks))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block));
        TextureMapping altTextureMap = new TextureMapping()
                .put(TextureSlot.END, TextureMapping.getBlockTexture(planks))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_alt"));
        ResourceLocation model = ModelTemplates.CUBE_COLUMN.create(block, textureMap, generator.modelOutput);
        ResourceLocation altModel = ModelTemplates.CUBE_COLUMN.createWithSuffix(block, "_alt", altTextureMap, generator.modelOutput);
        List<ResourceLocation> models = List.of(model, altModel);
        generator.delegateItemModel(block, model);
        generator.blockStateOutput.accept(
                MultiVariantGenerator.multiVariant(block, BlockModelGenerators.wrapModels(models, variant -> variant).toArray(Variant[]::new))
        );
    }

    private void registerCabinet(BlockModelGenerators generator, Block block) {
        ResourceLocation sideTexture = TextureMapping.getBlockTexture(block, "_side");
        TextureMapping closedTexture = new TextureMapping()
                .put(TextureSlot.SIDE, sideTexture)
                .put(TextureSlot.TOP, sideTexture)
                .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(block, "_front"));
        TextureMapping openTexture = new TextureMapping()
                .put(TextureSlot.SIDE, sideTexture)
                .put(SHELF_KEY, TextureMapping.getBlockTexture(block, "_front_open_shelf"))
                .put(TextureSlot.WALL, TextureMapping.getBlockTexture(block, "_wall"))
                .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(block, "_front_open"));
        ResourceLocation closedModel = ModelTemplates.CUBE_ORIENTABLE.create(block, closedTexture, generator.modelOutput);
        ResourceLocation openModel = CABINET_OPEN.create(block, openTexture, generator.modelOutput);
        generator.delegateItemModel(block, closedModel);
        generator.blockStateOutput.accept(
                MultiVariantGenerator.multiVariant(block)
                        .with(BlockModelGenerators.createBooleanModelDispatch(CabinetBlock.OPEN, openModel, closedModel))
                        .with(BlockModelGenerators.createHorizontalFacingDispatch())
        );
    }

    private void registerPanel(BlockModelGenerators generator, Block block, Block textureBlock) {
        registerPanel(generator, block, TextureMapping.getBlockTexture(textureBlock));
    }

    private void registerPanel(BlockModelGenerators generator, Block block, ResourceLocation texture) {
        ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(block.asItem()), TextureMapping.layer0(texture), generator.modelOutput);
        ResourceLocation model = PANEL.create(block, TextureMapping.cube(texture), generator.modelOutput);
        MultiPartGenerator blockStateSupplier = MultiPartGenerator.multiPart(block);
        Condition.TerminalCondition propertyCondition = Condition.condition();
        BlockModelGenerators.MULTIFACE_GENERATOR.stream().map(Pair::getFirst)
                .forEach(property -> propertyCondition.term(property, false));

        for (Pair<BooleanProperty, Function<ResourceLocation, Variant>> pair : BlockModelGenerators.MULTIFACE_GENERATOR) {
            BooleanProperty facingProperty = pair.getFirst();
            Variant variant = pair.getSecond().apply(model);
            blockStateSupplier.with(Condition.condition().term(facingProperty, true), variant);
            blockStateSupplier.with(propertyCondition, variant);
        }

        generator.blockStateOutput.accept(blockStateSupplier);
    }

    private Variant rotateForAxis(Variant variant, Direction.Axis axis) {
        return switch (axis) {
            case X -> variant.with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270);
            case Y -> variant.with(VariantProperties.X_ROT, VariantProperties.Rotation.R90);
            case Z -> variant;
        };
    }

    private void registerBar(BlockModelGenerators generator, Block block) {
        TextureMapping textureMap = TextureMapping.defaultTexture(block);
        ResourceLocation model = BAR.create(block, textureMap, generator.modelOutput);
        ResourceLocation topModel = BAR_TOP.create(block, textureMap, generator.modelOutput);
        ResourceLocation bottomModel = BAR_BOTTOM.create(block, textureMap, generator.modelOutput);
        generator.createSimpleFlatItemModel(block.asItem());
        MultiPartGenerator blockStateSupplier = MultiPartGenerator.multiPart(block);
        for (Direction.Axis axis : Direction.Axis.values()) {
            blockStateSupplier.with(
                    Condition.condition().term(BarBlock.AXIS, axis),
                    this.rotateForAxis(this.model(model), axis)
            ).with(
                    Condition.condition().term(BarBlock.AXIS, axis).term(BarBlock.TOP, true),
                    this.rotateForAxis(this.model(topModel), axis)
            ).with(
                    Condition.condition().term(BarBlock.AXIS, axis).term(BarBlock.BOTTOM, true),
                    this.rotateForAxis(this.model(bottomModel), axis)
            );
        }
        generator.blockStateOutput.accept(blockStateSupplier);
    }

    private void registerLedge(BlockModelGenerators generator, Block block, Block barBlock) {
        TextureMapping textureMap = TextureMapping.defaultTexture(barBlock);
        ResourceLocation model = LEDGE.create(block, textureMap, generator.modelOutput);
        generator.blockStateOutput.accept(
                MultiVariantGenerator.multiVariant(block).with(PropertyDispatch.property(LedgeBlock.FACING)
                        .select(Direction.NORTH, this.model(model))
                        .select(Direction.EAST, this.model(model).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                        .select(Direction.SOUTH, this.model(model).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                        .select(Direction.WEST, this.model(model).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R270))
                )
        );
        generator.createSimpleFlatItemModel(block.asItem());
    }

    private void registerLadder(BlockModelGenerators generator, Block block) {
        TextureMapping textureMap = TextureMapping.defaultTexture(block).put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(block));
        LADDER.create(block, textureMap, generator.modelOutput);
        generator.createSimpleFlatItemModel(block);
        generator.createNonTemplateHorizontalBlock(block);
    }

    private void registerTrimmedLantern(BlockModelGenerators generator, Block block, boolean hasEmergencyVariant) {
        TextureMapping litTexture = new TextureMapping()
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side_lit"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bottom_lit"));
        TextureMapping unlitTexture = new TextureMapping()
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side_unlit"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bottom_unlit"));
        ResourceLocation litCeiling = TRIMMED_LANTERN_CEILING.createWithSuffix(block, "_ceiling_lit", litTexture, generator.modelOutput);
        ResourceLocation litWall = TRIMMED_LANTERN_WALL.createWithSuffix(block, "_wall_lit", litTexture, generator.modelOutput);
        ResourceLocation litFloor = TRIMMED_LANTERN_FLOOR.createWithSuffix(block, "_floor_lit", litTexture, generator.modelOutput);
        ResourceLocation unlitCeiling = TRIMMED_LANTERN_CEILING.createWithSuffix(block, "_ceiling_unlit", unlitTexture, generator.modelOutput);
        ResourceLocation unlitWall = TRIMMED_LANTERN_WALL.createWithSuffix(block, "_wall_unlit", unlitTexture, generator.modelOutput);
        ResourceLocation unlitFloor = TRIMMED_LANTERN_FLOOR.createWithSuffix(block, "_floor_unlit", unlitTexture, generator.modelOutput);

        TextureMapping unpoweredTexture = new TextureMapping()
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side_emergency"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bottom_emergency"));
        ResourceLocation unpoweredCeiling = hasEmergencyVariant ? TRIMMED_LANTERN_CEILING.createWithSuffix(block, "_ceiling_emergency", unpoweredTexture, generator.modelOutput) : unlitCeiling;
        ResourceLocation unpoweredWall = hasEmergencyVariant ? TRIMMED_LANTERN_WALL.createWithSuffix(block, "_wall_emergency", unpoweredTexture, generator.modelOutput) : unlitWall;
        ResourceLocation unpoweredFloor = hasEmergencyVariant ? TRIMMED_LANTERN_FLOOR.createWithSuffix(block, "_floor_emergency", unpoweredTexture, generator.modelOutput) : unlitFloor;

        generator.delegateItemModel(block, unlitFloor);
        MultiVariantGenerator blockStateSupplier = MultiVariantGenerator.multiVariant(block);
        blockStateSupplier.with(PropertyDispatch.properties(TrimmedLanternBlock.FACING, TrimmedLanternBlock.ACTIVE, TrimmedLanternBlock.LIT)
                // powered
                .select(Direction.NORTH, true, false, this.model(unlitWall))
                .select(Direction.EAST, true, false, this.rotateForFace(this.model(unlitWall), Direction.EAST, false))
                .select(Direction.SOUTH, true, false, this.rotateForFace(this.model(unlitWall), Direction.SOUTH, false))
                .select(Direction.WEST, true, false, this.rotateForFace(this.model(unlitWall), Direction.WEST, false))
                .select(Direction.UP, true, false, this.model(unlitFloor))
                .select(Direction.DOWN, true, false, this.model(unlitCeiling))
                .select(Direction.NORTH, true, true, this.model(litWall))
                .select(Direction.EAST, true, true, this.rotateForFace(this.model(litWall), Direction.EAST, false))
                .select(Direction.SOUTH, true, true, this.rotateForFace(this.model(litWall), Direction.SOUTH, false))
                .select(Direction.WEST, true, true, this.rotateForFace(this.model(litWall), Direction.WEST, false))
                .select(Direction.UP, true, true, this.model(litFloor))
                .select(Direction.DOWN, true, true, this.model(litCeiling))
                // unpowered
                .select(Direction.NORTH, false, false, this.model(unpoweredWall))
                .select(Direction.EAST, false, false, this.rotateForFace(this.model(unpoweredWall), Direction.EAST, false))
                .select(Direction.SOUTH, false, false, this.rotateForFace(this.model(unpoweredWall), Direction.SOUTH, false))
                .select(Direction.WEST, false, false, this.rotateForFace(this.model(unpoweredWall), Direction.WEST, false))
                .select(Direction.UP, false, false, this.model(unpoweredFloor))
                .select(Direction.DOWN, false, false, this.model(unpoweredCeiling))
                .select(Direction.NORTH, false, true, this.model(unpoweredWall))
                .select(Direction.EAST, false, true, this.rotateForFace(this.model(unpoweredWall), Direction.EAST, false))
                .select(Direction.SOUTH, false, true, this.rotateForFace(this.model(unpoweredWall), Direction.SOUTH, false))
                .select(Direction.WEST, false, true, this.rotateForFace(this.model(unpoweredWall), Direction.WEST, false))
                .select(Direction.UP, false, true, this.model(unpoweredFloor))
                .select(Direction.DOWN, false, true, this.model(unpoweredCeiling))
        );
        generator.blockStateOutput.accept(blockStateSupplier);
    }

    private PropertyDispatch wallMountedVariantMap(ResourceLocation model) {
        return PropertyDispatch.properties(FaceAttachedHorizontalDirectionalBlock.FACING, FaceAttachedHorizontalDirectionalBlock.FACE).generate((facing, face) -> {
            if (face == AttachFace.WALL) {
                return this.rotateForFace(this.model(model), facing, false);
            }
            return this.rotateForFace(this.rotateForFace(
                            this.model(model), face == AttachFace.FLOOR ? Direction.UP : Direction.DOWN, false),
                    facing.getOpposite(), false);
        });
    }

    private PropertyDispatch wallMountedVariantMap(BooleanProperty booleanProperty, ResourceLocation trueModel, ResourceLocation falseModel) {
        return PropertyDispatch.properties(FaceAttachedHorizontalDirectionalBlock.FACING, FaceAttachedHorizontalDirectionalBlock.FACE, booleanProperty).generate((facing, face, bl) -> {
            ResourceLocation model = bl ? trueModel : falseModel;
            if (face == AttachFace.WALL) {
                return this.rotateForFace(this.model(model), facing, false);
            }
            return this.rotateForFace(this.rotateForFace(
                            this.model(model), face == AttachFace.FLOOR ? Direction.UP : Direction.DOWN, false),
                    facing.getOpposite(), false);
        });
    }

    private void registerSprinkler(BlockModelGenerators generator, Block block, Block particleBlock) {
        ResourceLocation model = SPRINKLER.create(block, TextureMapping.defaultTexture(block).put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(particleBlock)), generator.modelOutput);
        generator.delegateItemModel(block, model);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block).with(this.wallMountedVariantMap(model)));
    }

    private void registerWallLamp(BlockModelGenerators generator, Block block, boolean hasEmergencyVariant) {
        TextureMapping litTexture = TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_lit"));
        TextureMapping unlitTexture = TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_unlit"));
        ResourceLocation litCeiling = WALL_LAMP_CEILING.createWithSuffix(block, "_ceiling_lit", litTexture, generator.modelOutput);
        ResourceLocation litWall = WALL_LAMP_WALL.createWithSuffix(block, "_wall_lit", litTexture, generator.modelOutput);
        ResourceLocation litFloor = WALL_LAMP_FLOOR.createWithSuffix(block, "_floor_lit", litTexture, generator.modelOutput);
        ResourceLocation unlitCeiling = WALL_LAMP_CEILING.createWithSuffix(block, "_ceiling_unlit", unlitTexture, generator.modelOutput);
        ResourceLocation unlitWall = WALL_LAMP_WALL.createWithSuffix(block, "_wall_unlit", unlitTexture, generator.modelOutput);
        ResourceLocation unlitFloor = WALL_LAMP_FLOOR.createWithSuffix(block, "_floor_unlit", unlitTexture, generator.modelOutput);

        TextureMapping unpoweredTexture = hasEmergencyVariant ? TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_emergency")) : unlitTexture;
        ResourceLocation unpoweredCeiling = hasEmergencyVariant ? WALL_LAMP_CEILING.createWithSuffix(block, "_ceiling_emergency", unpoweredTexture, generator.modelOutput) : unlitCeiling;
        ResourceLocation unpoweredWall = hasEmergencyVariant ? WALL_LAMP_WALL.createWithSuffix(block, "_wall_emergency", unpoweredTexture, generator.modelOutput) : unlitWall;
        ResourceLocation unpoweredFloor = hasEmergencyVariant ? WALL_LAMP_FLOOR.createWithSuffix(block, "_floor_emergency", unpoweredTexture, generator.modelOutput) : unlitFloor;

        generator.delegateItemModel(block, unlitFloor);
        MultiVariantGenerator blockStateSupplier = MultiVariantGenerator.multiVariant(block);
        blockStateSupplier.with(PropertyDispatch.properties(WallLampBlock.FACING, WallLampBlock.ACTIVE, WallLampBlock.LIT)
                // powered
                .select(Direction.NORTH, true, false, this.model(unlitWall))
                .select(Direction.EAST, true, false, this.rotateForFace(this.model(unlitWall), Direction.EAST, false))
                .select(Direction.SOUTH, true, false, this.rotateForFace(this.model(unlitWall), Direction.SOUTH, false))
                .select(Direction.WEST, true, false, this.rotateForFace(this.model(unlitWall), Direction.WEST, false))
                .select(Direction.UP, true, false, this.model(unlitFloor))
                .select(Direction.DOWN, true, false, this.model(unlitCeiling))
                .select(Direction.NORTH, true, true, this.model(litWall))
                .select(Direction.EAST, true, true, this.rotateForFace(this.model(litWall), Direction.EAST, false))
                .select(Direction.SOUTH, true, true, this.rotateForFace(this.model(litWall), Direction.SOUTH, false))
                .select(Direction.WEST, true, true, this.rotateForFace(this.model(litWall), Direction.WEST, false))
                .select(Direction.UP, true, true, this.model(litFloor))
                .select(Direction.DOWN, true, true, this.model(litCeiling))
                // unpowered
                .select(Direction.NORTH, false, false, this.model(unpoweredWall))
                .select(Direction.EAST, false, false, this.rotateForFace(this.model(unpoweredWall), Direction.EAST, false))
                .select(Direction.SOUTH, false, false, this.rotateForFace(this.model(unpoweredWall), Direction.SOUTH, false))
                .select(Direction.WEST, false, false, this.rotateForFace(this.model(unpoweredWall), Direction.WEST, false))
                .select(Direction.UP, false, false, this.model(unpoweredFloor))
                .select(Direction.DOWN, false, false, this.model(unpoweredCeiling))
                .select(Direction.NORTH, false, true, this.model(unpoweredWall))
                .select(Direction.EAST, false, true, this.rotateForFace(this.model(unpoweredWall), Direction.EAST, false))
                .select(Direction.SOUTH, false, true, this.rotateForFace(this.model(unpoweredWall), Direction.SOUTH, false))
                .select(Direction.WEST, false, true, this.rotateForFace(this.model(unpoweredWall), Direction.WEST, false))
                .select(Direction.UP, false, true, this.model(unpoweredFloor))
                .select(Direction.DOWN, false, true, this.model(unpoweredCeiling))
        );
        generator.blockStateOutput.accept(blockStateSupplier);
    }

    private void registerCargoBox(BlockModelGenerators generator, Block block) {
        TextureMapping closedTexture = new TextureMapping()
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bottom"));
        TextureMapping openTexture = new TextureMapping()
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top_open"))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bottom"));

        ResourceLocation closedModel = CARGO_BOX.create(block, closedTexture, generator.modelOutput);
        ResourceLocation openModel = CARGO_BOX_OPEN.create(block, openTexture, generator.modelOutput);
        ResourceLocation inventoryModel = ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(block, "_inventory", closedTexture, generator.modelOutput);
        generator.delegateItemModel(block, inventoryModel);
        MultiVariantGenerator blockStateSupplier = MultiVariantGenerator.multiVariant(block);
        blockStateSupplier.with(
                PropertyDispatch.properties(CargoBoxBlock.FACING, CargoBoxBlock.OPEN)
                        .generate((facing, open) -> this.rotateForFace(this.model(open ? openModel : closedModel), facing, false))
        );
        generator.blockStateOutput.accept(blockStateSupplier);
    }

    private void registerGlassPanel(BlockModelGenerators generator, Block block) {
        generator.createSimpleFlatItemModel(block, "_trim");
        TextureMapping textureMap = new TextureMapping()
                .put(TextureSlot.PANE, TextureMapping.getBlockTexture(block))
                .put(TextureSlot.EDGE, TextureMapping.getBlockTexture(block, "_trim"));
        ResourceLocation model = GLASS_PANEL.create(block, textureMap, generator.modelOutput);
        generator.blockStateOutput.accept(
                MultiVariantGenerator.multiVariant(block, this.model(model))
                        .with(PropertyDispatch.property(BlockStateProperties.FACING)
                                .select(Direction.UP, this.variant(VariantProperties.X_ROT, VariantProperties.Rotation.R90))
                                .select(Direction.DOWN, this.variant(VariantProperties.X_ROT, VariantProperties.Rotation.R270))
                                .select(Direction.SOUTH, this.variant())
                                .select(Direction.NORTH, this.variant(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                                .select(Direction.EAST, this.variant(VariantProperties.Y_ROT, VariantProperties.Rotation.R270))
                                .select(Direction.WEST, this.variant(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))));
    }

    private void registerCullingGlass(BlockModelGenerators generator) {
        Block block = TMMBlocks.CULLING_GLASS;
        generator.createSimpleFlatItemModel(block);
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        generator.blockStateOutput.accept(
                MultiVariantGenerator.multiVariant(block, this.model(model))
                        .with(PropertyDispatch.property(BlockStateProperties.FACING)
                                .select(Direction.UP, this.variant(VariantProperties.X_ROT, VariantProperties.Rotation.R90))
                                .select(Direction.DOWN, this.variant(VariantProperties.X_ROT, VariantProperties.Rotation.R270))
                                .select(Direction.SOUTH, this.variant())
                                .select(Direction.NORTH, this.variant(VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                                .select(Direction.EAST, this.variant(VariantProperties.Y_ROT, VariantProperties.Rotation.R270))
                                .select(Direction.WEST, this.variant(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))));
    }

    private void registerHorizontalAxisBlock(BlockModelGenerators generator, Block block, boolean registerInventoryParent) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        if (registerInventoryParent) generator.delegateItemModel(block, model);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block).with(
                PropertyDispatch.property(PanelStripesBlock.AXIS)
                        .select(Direction.Axis.X, this.model(model).with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                        .select(Direction.Axis.Z, this.model(model))
        ));
    }

    private void registerButton(BlockModelGenerators generator, Block block) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        ResourceLocation pressedModel = ModelLocationUtils.getModelLocation(block, "_pressed");
        generator.delegateItemModel(block, model);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block).with(
                this.wallMountedVariantMap(ButtonBlock.POWERED, pressedModel, model)
        ));
    }

    private void registerCulledBlock(BlockModelGenerators generator, Block block, Block textureBlock) {
        ResourceLocation model = ModelTemplates.SINGLE_FACE.create(block, TextureMapping.defaultTexture(textureBlock), generator.modelOutput);
        ResourceLocation modelGlass = ModelTemplates.SINGLE_FACE.createWithSuffix(block, "_glass", TextureMapping.defaultTexture(TextureMapping.getBlockTexture(TMMBlocks.HULL_GLASS)), generator.modelOutput);
        ModelTemplates.FLAT_ITEM.create(ModelLocationUtils.getModelLocation(block.asItem()), TextureMapping.layer0(textureBlock), generator.modelOutput);
        MultiPartGenerator blockStateSupplier = MultiPartGenerator.multiPart(block);
        for (Direction direction : Direction.values()) {
            blockStateSupplier.with(
                    Condition.condition().term(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), true),
                    this.rotateForFace(this.model(model), direction, true)
            );
            blockStateSupplier.with(
                    Condition.condition().term(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), false),
                    this.rotateForFace(this.model(modelGlass), direction, true)
            );
        }
        generator.blockStateOutput.accept(blockStateSupplier);
    }

    private void registerOrnament(BlockModelGenerators generator, Block block) {
        TextureMapping allTexture = TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_all"));
        TextureMapping endTexture = TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_end"));
        TextureMapping sideTexture = TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_side"));
        TextureMapping cornerTexture = TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_corner"));
        TextureMapping sidesTexture = TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_sides"));
        TextureMapping centerTexture = TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_center"));
        TextureMapping sidesCenterTexture = TextureMapping.defaultTexture(TextureMapping.getBlockTexture(block, "_sides_center"));
        ORNAMENT_R0.createWithSuffix(block, "_all", allTexture, generator.modelOutput);
        ORNAMENT_R0.createWithSuffix(block, "_center", centerTexture, generator.modelOutput);
        ORNAMENT_R0.createWithSuffix(block, "_left_right_center", sidesCenterTexture, generator.modelOutput);
        ORNAMENT_R0.createWithSuffix(block, "_left", sideTexture, generator.modelOutput);
        ORNAMENT_R90.createWithSuffix(block, "_top", sideTexture, generator.modelOutput);
        ORNAMENT_R180.createWithSuffix(block, "_right", sideTexture, generator.modelOutput);
        ORNAMENT_R270.createWithSuffix(block, "_bottom", sideTexture, generator.modelOutput);
        ORNAMENT_R0.createWithSuffix(block, "_left_bottom", cornerTexture, generator.modelOutput);
        ORNAMENT_R90.createWithSuffix(block, "_left_top", cornerTexture, generator.modelOutput);
        ORNAMENT_R180.createWithSuffix(block, "_right_top", cornerTexture, generator.modelOutput);
        ORNAMENT_R270.createWithSuffix(block, "_right_bottom", cornerTexture, generator.modelOutput);
        ORNAMENT_R0.createWithSuffix(block, "_left_right", sidesTexture, generator.modelOutput);
        ORNAMENT_R90.createWithSuffix(block, "_top_bottom", sidesTexture, generator.modelOutput);
        ORNAMENT_R0.createWithSuffix(block, "_left_right_top", endTexture, generator.modelOutput);
        ORNAMENT_R90.createWithSuffix(block, "_right_top_bottom", endTexture, generator.modelOutput);
        ORNAMENT_R180.createWithSuffix(block, "_left_right_bottom", endTexture, generator.modelOutput);
        ORNAMENT_R270.createWithSuffix(block, "_left_top_bottom", endTexture, generator.modelOutput);
        generator.createSimpleFlatItemModel(block, "_all");
        PropertyDispatch map = PropertyDispatch.properties(OrnamentBlock.FACING, OrnamentBlock.SHAPE).generate((facing, shape) ->
                this.rotateForFace(this.model(ModelLocationUtils.getModelLocation(block, "_" + shape.getSerializedName())), facing, false)
        );
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block).with(map));
    }

    private void registerSpaceHelmet(BlockModelGenerators generator, Block block) {
        generator.delegateItemModel(block.asItem(), ResourceLocation.withDefaultNamespace("item/template_skull"));

        ResourceLocation model = ModelTemplates.PARTICLE_ONLY.create(block, TextureMapping.particle(TMMBlocks.STAINLESS_STEEL), generator.modelOutput);
        generator.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, model));
    }

    private void registerHullGlass(BlockModelGenerators generator, Block block) {
        ResourceLocation model = ModelTemplates.CUBE_ALL.create(block, TextureMapping.cube(block), generator.modelOutput);
        ResourceLocation opaqueModel = ModelTemplates.CUBE_ALL.createWithSuffix(block, "_opaque", TextureMapping.cube(TextureMapping.getBlockTexture(block, "_opaque")), generator.modelOutput);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block).with(BlockModelGenerators.createBooleanModelDispatch(PrivacyGlassBlock.OPAQUE, opaqueModel, model)));
        generator.delegateItemModel(block, model);
    }

    private void registerPrivacyGlassPanel(BlockModelGenerators generator, Block block) {
        generator.createSimpleFlatItemModel(block, "_trim");
        TextureMapping textureMap = new TextureMapping()
                .put(TextureSlot.PANE, TextureMapping.getBlockTexture(block))
                .put(TextureSlot.EDGE, TextureMapping.getBlockTexture(block, "_trim"));
        TextureMapping opaqueTextureMap = new TextureMapping()
                .put(TextureSlot.PANE, TextureMapping.getBlockTexture(block, "_opaque"))
                .put(TextureSlot.EDGE, TextureMapping.getBlockTexture(block, "_trim"));

        ResourceLocation model = GLASS_PANEL.create(block, textureMap, generator.modelOutput);
        ResourceLocation opaqueModel = GLASS_PANEL.createWithSuffix(block, "_opaque", opaqueTextureMap, generator.modelOutput);

        generator.blockStateOutput.accept(
                MultiVariantGenerator.multiVariant(block)
                        .with(PropertyDispatch.properties(BlockStateProperties.FACING, PrivacyGlassPanelBlock.OPAQUE)
                                .select(Direction.UP, true, this.variant(opaqueModel, VariantProperties.X_ROT, VariantProperties.Rotation.R90))
                                .select(Direction.UP, false, this.variant(model, VariantProperties.X_ROT, VariantProperties.Rotation.R90))
                                .select(Direction.DOWN, true, this.variant(opaqueModel, VariantProperties.X_ROT, VariantProperties.Rotation.R270))
                                .select(Direction.DOWN, false, this.variant(model, VariantProperties.X_ROT, VariantProperties.Rotation.R270))
                                .select(Direction.SOUTH, true, this.model(opaqueModel))
                                .select(Direction.SOUTH, false, this.model(model))
                                .select(Direction.NORTH, true, this.variant(opaqueModel, VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                                .select(Direction.NORTH, false, this.variant(model, VariantProperties.Y_ROT, VariantProperties.Rotation.R180))
                                .select(Direction.EAST, true, this.variant(opaqueModel, VariantProperties.Y_ROT, VariantProperties.Rotation.R270))
                                .select(Direction.EAST, false, this.variant(model, VariantProperties.Y_ROT, VariantProperties.Rotation.R270))
                                .select(Direction.WEST, true, this.variant(opaqueModel, VariantProperties.Y_ROT, VariantProperties.Rotation.R90))
                                .select(Direction.WEST, false, this.variant(model, VariantProperties.Y_ROT, VariantProperties.Rotation.R90))));
    }

    private void registerRailing(BlockModelGenerators generator, Block block, Block post, Block diagonal) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        ResourceLocation inventoryModel = ModelLocationUtils.getModelLocation(block, "_inventory");
        ResourceLocation diagonalLeftModel = ModelLocationUtils.getModelLocation(block, "_diagonal_left");
        ResourceLocation diagonalRightModel = ModelLocationUtils.getModelLocation(block, "_diagonal_right");
        ResourceLocation diagonalTopLeftModel = ModelLocationUtils.getModelLocation(block, "_diagonal_top_left");
        ResourceLocation diagonalTopRightModel = ModelLocationUtils.getModelLocation(block, "_diagonal_top_right");
        ResourceLocation diagonalBottomLeftModel = ModelLocationUtils.getModelLocation(block, "_diagonal_bottom_left");
        ResourceLocation diagonalBottomRightModel = ModelLocationUtils.getModelLocation(block, "_diagonal_bottom_right");
        ResourceLocation postModel = ModelLocationUtils.getModelLocation(block, "_post");
        generator.delegateItemModel(block, inventoryModel);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block, this.model(model))
                .with(BlockModelGenerators.createHorizontalFacingDispatch()));
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(post, this.model(postModel))
                .with(BlockModelGenerators.createHorizontalFacingDispatch()));
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(diagonal)
                .with(PropertyDispatch.properties(DiagonalRailingBlock.FACING, DiagonalRailingBlock.LEFT, DiagonalRailingBlock.SHAPE).generate(
                        (facing, left, shape) -> {
                            Variant variant = this.model(switch (shape) {
                                case TOP -> left ? diagonalTopLeftModel : diagonalTopRightModel;
                                case MIDDLE -> left ? diagonalLeftModel : diagonalRightModel;
                                case BOTTOM -> left ? diagonalBottomLeftModel : diagonalBottomRightModel;
                            });
                            return this.rotateForFace(
                                    variant,
                                    left ? facing : facing.getOpposite(),
                                    false
                            );
                        }
                )));
    }

    private void registerBed(BlockModelGenerators generator, Block block) {
        TextureMapping textureMap = TextureMapping.defaultTexture(block);
        ResourceLocation inventoryModel = TRIMMED_BED_INVENTORY.create(block, textureMap, generator.modelOutput);
        ResourceLocation headModel = TRIMMED_BED_HEAD.create(block, textureMap, generator.modelOutput);
        ResourceLocation footModel = TRIMMED_BED_FOOT.create(block, textureMap, generator.modelOutput);
        generator.delegateItemModel(block, inventoryModel);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block)
                .with(PropertyDispatch.properties(TrimmedBedBlock.FACING, TrimmedBedBlock.PART).generate(
                        (facing, part) -> this.rotateForFace(
                                this.model(part == BedPart.HEAD ? headModel : footModel),
                                facing,
                                false
                        )
                )));
    }

    private void registerPipe(BlockModelGenerators generator, Block block) {
        TextureMapping textureMap = new TextureMapping()
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_top"))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"));
        ResourceLocation model = CARGO_BOX.create(block, textureMap, generator.modelOutput);
        ResourceLocation inventoryModel = ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(block, "_inventory", textureMap, generator.modelOutput);
        generator.delegateItemModel(block, inventoryModel);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block, this.model(model)).with(
                BlockModelGenerators.createFacingDispatch()
        ));
    }

    private void registerPump(BlockModelGenerators generator, Block block) {
        ResourceLocation model = ModelLocationUtils.getModelLocation(block);
        generator.delegateItemModel(block, model);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block, this.model(model)).with(
                BlockModelGenerators.createFacingDispatch()
        ));
    }

    private void registerVentHatch(BlockModelGenerators generator, Block block) {
        TextureMapping textureMap = TextureMapping.defaultTexture(block);
        ResourceLocation model = VENT_HATCH.create(block, textureMap, generator.modelOutput);
        ResourceLocation openModel = VENT_HATCH_OPEN.create(block, textureMap, generator.modelOutput);
        generator.createSimpleFlatItemModel(block);
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block).with(
                this.wallMountedVariantMap(VentHatchBlock.OPEN, openModel, model))
        );
    }

    private void registerNeonPillar(BlockModelGenerators generator, Block block, boolean hasEmergencyVariant) {
        TextureMapping litTextureMap = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_lit"))
                .put(TextureSlot.END, TextureMapping.getBlockTexture(block, "_top_lit"))
                .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(block, "_lit"));
        TextureMapping unlitTextureMap = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_unlit"))
                .put(TextureSlot.END, TextureMapping.getBlockTexture(block, "_top_unlit"))
                .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(block, "_unlit"));
        TextureMapping unpoweredTextureMap = hasEmergencyVariant ? new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_emergency"))
                .put(TextureSlot.END, TextureMapping.getBlockTexture(block, "_top_emergency"))
                .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(block, "_emergency")) : unlitTextureMap;
        ResourceLocation litModel = ModelTemplates.CUBE_COLUMN.createWithSuffix(block, "_lit", litTextureMap, generator.modelOutput);
        ResourceLocation litHorizontalModel = ModelTemplates.CUBE_COLUMN_HORIZONTAL.createWithSuffix(block, "_lit", litTextureMap, generator.modelOutput);
        ResourceLocation unlitModel = ModelTemplates.CUBE_COLUMN.createWithSuffix(block, "_unlit", unlitTextureMap, generator.modelOutput);
        ResourceLocation unlitHorizontalModel = ModelTemplates.CUBE_COLUMN_HORIZONTAL.createWithSuffix(block, "_unlit", unlitTextureMap, generator.modelOutput);
        ResourceLocation unpoweredModel = hasEmergencyVariant ? ModelTemplates.CUBE_COLUMN.createWithSuffix(block, "_emergency", unpoweredTextureMap, generator.modelOutput) : unlitModel;
        ResourceLocation unpoweredHorizontalModel = hasEmergencyVariant ? ModelTemplates.CUBE_COLUMN_HORIZONTAL.createWithSuffix(block, "_emergency", unpoweredTextureMap, generator.modelOutput) : unlitHorizontalModel;

        generator.delegateItemModel(block, unlitModel);

        PropertyDispatch map = PropertyDispatch.properties(NeonPillarBlock.AXIS, NeonPillarBlock.ACTIVE, NeonPillarBlock.LIT).generate((axis, powered, lit) -> {
            if (axis == Direction.Axis.Y) {
                return this.model(powered ? (lit ? litModel : unlitModel) : unpoweredModel);
            }
            Variant variant = this.model(powered ? (lit ? litHorizontalModel : unlitHorizontalModel) : unpoweredHorizontalModel).with(VariantProperties.X_ROT, VariantProperties.Rotation.R90);
            if (axis == Direction.Axis.X) {
                return variant.with(VariantProperties.Y_ROT, VariantProperties.Rotation.R90);
            }
            return variant;
        });
        generator.blockStateOutput.accept(MultiVariantGenerator.multiVariant(block).with(map));
    }

    private void registerNeonTube(BlockModelGenerators generator, Block block, boolean hasEmergencyVariant) {
        TextureMapping textureMap = new TextureMapping().put(TextureSlot.TEXTURE, TextureMapping.getBlockTexture(block, "_lit"));
        TextureMapping unlitTextureMap = new TextureMapping().put(TextureSlot.TEXTURE, TextureMapping.getBlockTexture(block, "_unlit"));

        ResourceLocation model = THICK_BAR.create(block, textureMap, generator.modelOutput);
        ResourceLocation unlitModel = THICK_BAR.createWithSuffix(block, "_unlit", unlitTextureMap, generator.modelOutput);

        TextureMapping unpoweredTextureMap = new TextureMapping().put(TextureSlot.TEXTURE, TextureMapping.getBlockTexture(block, "_emergency"));
        ResourceLocation unpoweredModel = hasEmergencyVariant ? THICK_BAR.createWithSuffix(block, "_emergency", unpoweredTextureMap, generator.modelOutput) : unlitModel;

        ResourceLocation topModel = THICK_BAR_TOP.create(block, textureMap, generator.modelOutput);
        ResourceLocation bottomModel = THICK_BAR_BOTTOM.create(block, textureMap, generator.modelOutput);

        generator.createSimpleFlatItemModel(block.asItem());

        MultiPartGenerator blockStateSupplier = MultiPartGenerator.multiPart(block);
        for (Direction.Axis axis : Direction.Axis.values()) {
            for (Boolean lit : NeonTubeBlock.LIT.getPossibleValues()) {
                for (Boolean powered : NeonTubeBlock.ACTIVE.getPossibleValues()) {
                    blockStateSupplier.with(
                            Condition.condition().term(BarBlock.AXIS, axis).term(NeonTubeBlock.ACTIVE, powered).term(NeonTubeBlock.LIT, lit),
                            this.rotateForAxis(this.model(powered ? (lit ? model : unlitModel) : unpoweredModel), axis)
                    ).with(
                            Condition.condition().term(BarBlock.AXIS, axis).term(BarBlock.TOP, true),
                            this.rotateForAxis(this.model(topModel), axis)
                    ).with(
                            Condition.condition().term(BarBlock.AXIS, axis).term(BarBlock.BOTTOM, true),
                            this.rotateForAxis(this.model(bottomModel), axis)
                    );
                }
            }
        }
        generator.blockStateOutput.accept(blockStateSupplier);
    }
}
