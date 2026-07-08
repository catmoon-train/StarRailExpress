package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block.*;
import org.agmas.noellesroles.content.block_entity.*;
import net.exmo.sre.repair.content.block.*;
import net.exmo.sre.repair.content.block_entity.*;

import static io.wifi.starrailexpress.index.TMMBlocks.DARK_STEEL;

public interface ModBlocks {
    public static ResourceKey<CreativeModeTab> BLOCK_CREATIVE_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Noellesroles.id("block"));
    public static final BlockRegistrar blockRegistrar = new BlockRegistrar(Noellesroles.MOD_ID);
    public static final BlockEntityTypeRegistrar blockEntityRegistrar = new BlockEntityTypeRegistrar(
            Noellesroles.MOD_ID);

    Block VENDING_MACHINES_BLOCK = registerBlockMultiTab("vending_machines",
            new VendingMachinesBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion()),
            BLOCK_CREATIVE_GROUP, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    BlockEntityType<VendingMachinesBlockEntity> VENDING_MACHINES_BLOCK_ENTITY = blockEntityRegistrar.create(
            "vending_machines",
            BlockEntityType.Builder.of(VendingMachinesBlockEntity::new,
                    ModBlocks.VENDING_MACHINES_BLOCK));
    Block LOTTERY_MACHINE_BLOCK = registerBlockMultiTab("lottery_machine",
            new LotteryMachineBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion()),
            BLOCK_CREATIVE_GROUP, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    BlockEntityType<LotteryMachineBlockEntity> LOTTERY_MACHINE_BLOCK_ENTITY = blockEntityRegistrar.create(
            "lottery_machine",
            BlockEntityType.Builder.of(LotteryMachineBlockEntity::new,
                    ModBlocks.LOTTERY_MACHINE_BLOCK));
    // 创建轮盘赌桌方块
    Block DEVIL_ROULETTE_TABLE = registerBlockMultiTab("devil_roulette_table",
            new DevilRouletteTable(),
            BLOCK_CREATIVE_GROUP, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    Block REPAIR_STATION = registerBlock("repair_station",
            new RepairStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).lightLevel(state -> 3)));
    Block HUNTER_CAGE = registerBlock("hunter_cage",
            new HunterCageBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion().strength(4.0F)));
    Block REPAIR_EXIT_GATE = registerBlock("repair_exit_gate",
            new RepairExitGateBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion().strength(5.0F)));
    Block REPAIR_SUPPLY_CRATE = registerBlock("repair_supply_crate",
            new RepairSupplyCrateBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F)));
    Block REPAIR_PALLET = registerBlock("repair_pallet",
            new RepairPalletBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(1.2F).noOcclusion()));
    Block HUNTER_SNARE = registerBlock("hunter_snare",
            new HunterSnareBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(0.6F).noOcclusion()));
    Block FLARE_BLOCK = registerBlock("flare_block",
            new FlareBlock());
    Block HOTBAR_STORAGE = registerBlockMultiTab("repair_hotbar_storage",
            new HotbarStorageBlock(Block.Properties.ofFullCopy(Blocks.CHEST)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)),
            BLOCK_CREATIVE_GROUP, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    Block SUPPLY_CRATE_BLOCK = registerBlockMultiTab("supply_crate",
            new SupplyCrateBlock(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion()),
            BLOCK_CREATIVE_GROUP, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    BlockEntityType<SupplyCrateBlockEntity> SUPPLY_CRATE_BLOCK_ENTITY = blockEntityRegistrar.create(
            "supply_crate",
            BlockEntityType.Builder.of(SupplyCrateBlockEntity::new,
                    ModBlocks.SUPPLY_CRATE_BLOCK));

    // 创建轮盘赌桌方块实体类型
    BlockEntityType<DevilRouletteTableEntity> DEVIL_ROULETTE_TABLE_ENTITY = blockEntityRegistrar.create(
            "devil_roulette_table",
            BlockEntityType.Builder.of(DevilRouletteTableEntity::new,
                    new Block[] { ModBlocks.DEVIL_ROULETTE_TABLE }));
    BlockEntityType<RepairStationBlockEntity> REPAIR_STATION_BLOCK_ENTITY = blockEntityRegistrar.create(
            "repair_station",
            BlockEntityType.Builder.of(RepairStationBlockEntity::new, ModBlocks.REPAIR_STATION));
    BlockEntityType<HunterCageBlockEntity> HUNTER_CAGE_BLOCK_ENTITY = blockEntityRegistrar.create(
            "hunter_cage",
            BlockEntityType.Builder.of(HunterCageBlockEntity::new, ModBlocks.HUNTER_CAGE));
    public static final BlockEntityType<HotbarStorageBlockEntity> HOTBAR_STORAGE_BLOCK_ENTITY_BLOCK_ENTITY_TYPE = Registry
            .register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    Noellesroles.id("repair_hotbar_storage"),
                    BlockEntityType.Builder.of(HotbarStorageBlockEntity::new, HOTBAR_STORAGE)
                            .build(null));

    // Kill blocks (OP utilities)
    @SuppressWarnings("unchecked")
    Block KILL_BLOCK = blockRegistrar.createWithItem("kill_block",
            new KillBlock(BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).noOcclusion()),
            CreativeModeTabs.OP_BLOCKS, ModSceneBlocks.SCENE_CREATIVE_GROUP);
    @SuppressWarnings("unchecked")
    Block KILL_BLOCK_PANEL = blockRegistrar.createWithItem("kill_block_panel",
            new KillBlockPanel(BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).noOcclusion()),
            CreativeModeTabs.OP_BLOCKS, ModSceneBlocks.SCENE_CREATIVE_GROUP);

    static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BLOCK_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.noellesroles.block")).icon(() -> {
                    return new ItemStack(VENDING_MACHINES_BLOCK.asItem());
                })
                .build());
        blockRegistrar.registerEntries();
        blockEntityRegistrar.registerEntries();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings, BLOCK_CREATIVE_GROUP);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlockMultiTab(String id, T block,
            ResourceKey<CreativeModeTab> tab, ResourceKey<CreativeModeTab>... extraTabs) {
        if (extraTabs.length == 0) {
            return blockRegistrar.createWithItem(id, block, tab);
        }
        ResourceKey<CreativeModeTab>[] allTabs = new ResourceKey[extraTabs.length + 1];
        allTabs[0] = tab;
        System.arraycopy(extraTabs, 0, allTabs, 1, extraTabs.length);
        return blockRegistrar.createWithItem(id, block, allTabs);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerOpBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, CreativeModeTabs.OP_BLOCKS);
    }
}
