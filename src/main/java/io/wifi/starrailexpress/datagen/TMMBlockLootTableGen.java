package io.wifi.starrailexpress.datagen;

import io.wifi.starrailexpress.block.OrnamentBlock;
import io.wifi.starrailexpress.block.PanelBlock;
import io.wifi.starrailexpress.block.property.OrnamentShape;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.BlockFamily;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class TMMBlockLootTableGen extends FabricBlockLootTableProvider {

    public TMMBlockLootTableGen(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generate() {
        // Vents
        this.addSelfDrop(TMMBlocks.STAINLESS_STEEL_VENT_SHAFT);
        this.addSelfDrop(TMMBlocks.STAINLESS_STEEL_VENT_HATCH);
        this.addSelfDrop(TMMBlocks.DARK_STEEL_VENT_SHAFT);
        this.addSelfDrop(TMMBlocks.DARK_STEEL_VENT_HATCH);
        this.addSelfDrop(TMMBlocks.TARNISHED_GOLD_VENT_SHAFT);
        this.addSelfDrop(TMMBlocks.TARNISHED_GOLD_VENT_HATCH);
        // Metallic Blocks
        this.addFamily(TMMBlocks.Family.TARNISHED_GOLD);
        this.addSelfDrop(TMMBlocks.TARNISHED_GOLD_PILLAR);
        this.addFamily(TMMBlocks.Family.GOLD);
        this.addSelfDrop(TMMBlocks.GOLD_PILLAR);
        this.addFamily(TMMBlocks.Family.PRISTINE_GOLD);
        this.addSelfDrop(TMMBlocks.PRISTINE_GOLD_PILLAR);
        this.addFamily(TMMBlocks.Family.WHITE_HULL);
        this.addSelfDrop(TMMBlocks.CULLING_WHITE_HULL);
        this.addFamily(TMMBlocks.Family.BLACK_HULL);
        this.addSelfDrop(TMMBlocks.CULLING_BLACK_HULL);
        this.addFamily(TMMBlocks.Family.BLACK_HULL_SHEET);
        this.addSelfDrop(TMMBlocks.METAL_SHEET);
        this.addSelfDrop(TMMBlocks.METAL_SHEET_STAIRS);
        this.addSelfDrop(TMMBlocks.METAL_SHEET_SLAB);
        this.addSelfDrop(TMMBlocks.METAL_SHEET_WALL);
        this.addSelfDrop(TMMBlocks.METAL_SHEET_DOOR, this::createDoorTable);
        this.addSelfDrop(TMMBlocks.METAL_SHEET_WALKWAY);
        this.addSelfDrop(TMMBlocks.STAINLESS_STEEL_LADDER);
        this.addFamily(TMMBlocks.Family.STAINLESS_STEEL);
        this.addSelfDrop(TMMBlocks.STAINLESS_STEEL_WALKWAY);
        this.addSelfDrop(TMMBlocks.STAINLESS_STEEL_BRANCH);
        this.addSelfDrop(TMMBlocks.STAINLESS_STEEL_PILLAR);
        // Glass
        this.addSelfDrop(TMMBlocks.GOLDEN_GLASS_PANEL);
        this.addSelfDrop(TMMBlocks.CULLING_GLASS);
        this.addSelfDrop(TMMBlocks.RHOMBUS_GLASS);
        this.addSelfDrop(TMMBlocks.HULL_GLASS);
        this.addSelfDrop(TMMBlocks.PRIVACY_GLASS_PANEL);
        // Stones
        this.addFamily(TMMBlocks.Family.MARBLE);
        this.addSelfDrop(TMMBlocks.MARBLE_MOSAIC);
        this.addFamily(TMMBlocks.Family.MARBLE_TILE);
        this.addFamily(TMMBlocks.Family.DARK_MARBLE);
        // Woods
        this.addFamily(TMMBlocks.Family.MAHOGANY);
        this.addFamily(TMMBlocks.Family.MAHOGANY_HERRINGBONE);
        this.addFamily(TMMBlocks.Family.SMOOTH_MAHOGANY);
        this.addSelfDrop(TMMBlocks.MAHOGANY_PANEL, this::panelDrops);
        this.addSelfDrop(TMMBlocks.MAHOGANY_CABINET, this::createNameableBlockEntityTable);
        this.addSelfDrop(TMMBlocks.MAHOGANY_BOOKSHELF);
        this.addFamily(TMMBlocks.Family.BUBINGA);
        this.addFamily(TMMBlocks.Family.BUBINGA_HERRINGBONE);
        this.addFamily(TMMBlocks.Family.SMOOTH_BUBINGA);
        this.addSelfDrop(TMMBlocks.BUBINGA_PANEL, this::panelDrops);
        this.addSelfDrop(TMMBlocks.BUBINGA_CABINET, this::createNameableBlockEntityTable);
        this.addSelfDrop(TMMBlocks.BUBINGA_BOOKSHELF);
        this.addFamily(TMMBlocks.Family.EBONY);
        this.addFamily(TMMBlocks.Family.EBONY_HERRINGBONE);
        this.addFamily(TMMBlocks.Family.SMOOTH_EBONY);
        this.addSelfDrop(TMMBlocks.EBONY_PANEL, this::panelDrops);
        this.addSelfDrop(TMMBlocks.EBONY_CABINET, this::createNameableBlockEntityTable);
        this.addSelfDrop(TMMBlocks.TRIMMED_EBONY_STAIRS);
        this.addSelfDrop(TMMBlocks.EBONY_BOOKSHELF);
        this.addSelfDrop(TMMBlocks.OAK_BRANCH);
        this.addSelfDrop(TMMBlocks.SPRUCE_BRANCH);
        this.addSelfDrop(TMMBlocks.BIRCH_BRANCH);
        this.addSelfDrop(TMMBlocks.JUNGLE_BRANCH);
        this.addSelfDrop(TMMBlocks.ACACIA_BRANCH);
        this.addSelfDrop(TMMBlocks.DARK_OAK_BRANCH);
        this.addSelfDrop(TMMBlocks.MANGROVE_BRANCH);
        this.addSelfDrop(TMMBlocks.CHERRY_BRANCH);
        this.addSelfDrop(TMMBlocks.BAMBOO_POLE);
        this.addSelfDrop(TMMBlocks.CRIMSON_STIPE);
        this.addSelfDrop(TMMBlocks.WARPED_STIPE);
        this.addSelfDrop(TMMBlocks.STRIPPED_OAK_BRANCH);
        this.addSelfDrop(TMMBlocks.STRIPPED_SPRUCE_BRANCH);
        this.addSelfDrop(TMMBlocks.STRIPPED_BIRCH_BRANCH);
        this.addSelfDrop(TMMBlocks.STRIPPED_JUNGLE_BRANCH);
        this.addSelfDrop(TMMBlocks.STRIPPED_ACACIA_BRANCH);
        this.addSelfDrop(TMMBlocks.STRIPPED_DARK_OAK_BRANCH);
        this.addSelfDrop(TMMBlocks.STRIPPED_MANGROVE_BRANCH);
        this.addSelfDrop(TMMBlocks.STRIPPED_CHERRY_BRANCH);
        this.addSelfDrop(TMMBlocks.STRIPPED_BAMBOO_POLE);
        this.addSelfDrop(TMMBlocks.STRIPPED_CRIMSON_STIPE);
        this.addSelfDrop(TMMBlocks.STRIPPED_WARPED_STIPE);
        this.addSelfDrop(TMMBlocks.PANEL_STRIPES);
        this.addSelfDrop(TMMBlocks.RAIL_BEAM);
        this.addSelfDrop(TMMBlocks.TRIMMED_RAILING_POST, block -> this.createSingleItemTable(TMMBlocks.TRIMMED_RAILING));
        this.addSelfDrop(TMMBlocks.DIAGONAL_TRIMMED_RAILING, block -> this.createSingleItemTable(TMMBlocks.TRIMMED_RAILING));
        this.addSelfDrop(TMMBlocks.TRIMMED_RAILING);
        // Furniture / Decor
        this.addSelfDrop(TMMBlocks.CARGO_BOX, this::createNameableBlockEntityTable);
        this.addSelfDrop(TMMBlocks.WHITE_LOUNGE_COUCH);
        this.addSelfDrop(TMMBlocks.WHITE_OTTOMAN);
        this.addSelfDrop(TMMBlocks.WHITE_TRIMMED_BED, block -> this.createSinglePropConditionTable(block, BedBlock.PART, BedPart.HEAD));
        this.addSelfDrop(TMMBlocks.RED_TRIMMED_BED, block -> this.createSinglePropConditionTable(block, BedBlock.PART, BedPart.HEAD));
        this.addSelfDrop(TMMBlocks.BLUE_LOUNGE_COUCH);
        this.addSelfDrop(TMMBlocks.LIGHT_TOILET);
        this.addSelfDrop(TMMBlocks.DARK_TOILET);
        this.addSelfDrop(TMMBlocks.GREEN_LOUNGE_COUCH);
        this.addSelfDrop(TMMBlocks.RED_LEATHER_COUCH);
        this.addSelfDrop(TMMBlocks.BROWN_LEATHER_COUCH);
        this.addSelfDrop(TMMBlocks.BEIGE_LEATHER_COUCH);
        this.addSelfDrop(TMMBlocks.COFFEE_TABLE);
        this.addSelfDrop(TMMBlocks.BAR_TABLE);
        this.addSelfDrop(TMMBlocks.BAR_STOOL);
        this.addSelfDrop(TMMBlocks.GOLD_BAR);
        this.addSelfDrop(TMMBlocks.GOLD_LEDGE);
        this.addSelfDrop(TMMBlocks.STAINLESS_STEEL_BAR);
        this.addSelfDrop(TMMBlocks.TRIMMED_LANTERN);
        this.addSelfDrop(TMMBlocks.WALL_LAMP);
        this.addSelfDrop(TMMBlocks.NEON_PILLAR);
        this.addSelfDrop(TMMBlocks.NEON_TUBE);
        this.addSelfDrop(TMMBlocks.STAINLESS_STEEL_SPRINKLER);
        this.addSelfDrop(TMMBlocks.GOLD_SPRINKLER);
        this.addSelfDrop(TMMBlocks.SMALL_BUTTON);
        this.addSelfDrop(TMMBlocks.ELEVATOR_BUTTON);
        this.addSelfDrop(TMMBlocks.GOLD_ORNAMENT, this::ornamentDrops);
        this.addNothingDrop(TMMBlocks.SMALL_GLASS_DOOR);
        this.addNothingDrop(TMMBlocks.SMALL_WOOD_DOOR);
        this.addNothingDrop(TMMBlocks.ANTHRACITE_STEEL_DOOR);
        this.addNothingDrop(TMMBlocks.KHAKI_STEEL_DOOR);
        this.addNothingDrop(TMMBlocks.MAROON_STEEL_DOOR);
        this.addNothingDrop(TMMBlocks.MUNTZ_STEEL_DOOR);
        this.addNothingDrop(TMMBlocks.NAVY_STEEL_DOOR);
        this.addNothingDrop(TMMBlocks.WHEEL);
        this.addNothingDrop(TMMBlocks.RUSTED_WHEEL);
    }

    private void addFamily(BlockFamily family) {
        this.addFamily(family, this::addSelfDrop);
    }

    private void addFamily(BlockFamily family, Consumer<Block> consumer) {
        family.getVariants().values().forEach(consumer);
        consumer.accept(family.getBaseBlock());
    }

    private void addSelfDrop(Block block) {
        this.addSelfDrop(block, this::createSingleItemTable);
    }

    private void addSelfDrop(Block block, Function<Block, LootTable.Builder> function) {
        if (block.defaultDestroyTime() == -1.0f) {
            // Register drops as nothing if block is unbreakable
            this.add(block, noDrop());
        } else {
            this.add(block, function);
        }
    }

    private void addNothingDrop(Block block) {
        this.add(block, noDrop());
    }

    private ConstantValue count(float value) {
        return ConstantValue.exactly(value);
    }

    private LootTable.Builder panelDrops(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool().add(
                this.applyExplosionCondition(block, LootItem.lootTableItem(block))
                        .apply(
                                Direction.values(),
                                direction -> SetItemCountFunction.setCount(this.count(1), true)
                                        .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).setProperties(
                                                StatePropertiesPredicate.Builder.properties().hasProperty(PanelBlock.getFaceProperty(direction), true)
                                        ))
                        ).apply(SetItemCountFunction.setCount(this.count(-1f), true))
        ));
    }

    private LootTable.Builder ornamentDrops(Block block) {
        return LootTable.lootTable().withPool(LootPool.lootPool().add(
                this.applyExplosionCondition(block, LootItem.lootTableItem(block))
                        .apply(
                                OrnamentShape.values(),
                                shape -> SetItemCountFunction.setCount(this.count(shape.getCount()), false)
                                        .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).setProperties(
                                                StatePropertiesPredicate.Builder.properties().hasProperty(OrnamentBlock.SHAPE, shape)
                                        ))
                        )
        ));
    }
}
