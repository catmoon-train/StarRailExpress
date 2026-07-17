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

    // 末日60秒模式：避难所门 + 物资箱（同时入方块页与 60s 统一页）
    Block SIXTY_SECONDS_SHELTER_DOOR = blockRegistrar.createWithItem("sixty_seconds_shelter_door",
            new net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion().strength(3.0F)),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_SUPPLY_BOX = blockRegistrar.createWithItem("sixty_seconds_supply_box",
            new net.exmo.sre.sixtyseconds.content.block.SupplyBoxBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F)),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 上锁的物资箱（撬箱起子）/ 高级物资箱（更好掉落）/ 上锁的高级物资箱（钳子）
    Block SIXTY_SECONDS_SUPPLY_BOX_LOCKED = blockRegistrar.createWithItem("sixty_seconds_supply_box_locked",
            new net.exmo.sre.sixtyseconds.content.block.SupplyBoxBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F), true, false),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_SUPPLY_BOX_ADVANCED = blockRegistrar.createWithItem("sixty_seconds_supply_box_advanced",
            new net.exmo.sre.sixtyseconds.content.block.SupplyBoxBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F), false, true),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_SUPPLY_BOX_ADVANCED_LOCKED = blockRegistrar.createWithItem(
            "sixty_seconds_supply_box_advanced_locked",
            new net.exmo.sre.sixtyseconds.content.block.SupplyBoxBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F), true, true),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    BlockEntityType<net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity> SIXTY_SECONDS_SUPPLY_BOX_ENTITY =
            blockEntityRegistrar.create("sixty_seconds_supply_box",
                    BlockEntityType.Builder.of(
                            net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity::new,
                            ModBlocks.SIXTY_SECONDS_SUPPLY_BOX,
                            ModBlocks.SIXTY_SECONDS_SUPPLY_BOX_LOCKED,
                            ModBlocks.SIXTY_SECONDS_SUPPLY_BOX_ADVANCED,
                            ModBlocks.SIXTY_SECONDS_SUPPLY_BOX_ADVANCED_LOCKED));
    // 末日60秒模式：随机物资箱（克隆物资箱一切，但每次刷新随机取一个 loot 类别）
    Block SIXTY_SECONDS_RANDOM_SUPPLY_BOX = blockRegistrar.createWithItem("sixty_seconds_random_supply_box",
            new net.exmo.sre.sixtyseconds.content.block.RandomSupplyBoxBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F)),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    BlockEntityType<net.exmo.sre.sixtyseconds.content.block_entity.RandomSupplyBoxBlockEntity> SIXTY_SECONDS_RANDOM_SUPPLY_BOX_ENTITY =
            blockEntityRegistrar.create("sixty_seconds_random_supply_box",
                    BlockEntityType.Builder.of(
                            net.exmo.sre.sixtyseconds.content.block_entity.RandomSupplyBoxBlockEntity::new,
                            ModBlocks.SIXTY_SECONDS_RANDOM_SUPPLY_BOX));
    // 末日60秒模式：幸存者营地（走上/右键触发通关）
    Block SIXTY_SECONDS_SURVIVOR_CAMP = blockRegistrar.createWithItem("sixty_seconds_survivor_camp",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsSurvivorCampBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).lightLevel(s -> 12).noOcclusion()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：避难所控制面板（右键打开本队仪表盘：门耐久/电力/科技/成员）
    Block SIXTY_SECONDS_SHELTER_PANEL = blockRegistrar.createWithItem("sixty_seconds_shelter_panel",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsShelterPanelBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F)),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：研究台（右键打开科技树，用废料解锁配方）
    Block SIXTY_SECONDS_RESEARCH_TABLE = blockRegistrar.createWithItem("sixty_seconds_research_table",
            new Block(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F)),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：拆解台（右键打开拆解界面，把可合成物品按 -60% 拆回基础资源；
    // 冒险模式仅可放白色混凝土标记上方，扳手可拆回）
    Block SIXTY_SECONDS_DISMANTLER = blockRegistrar.createWithItem("sixty_seconds_dismantler",
            new Block(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F)),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：专用合成台（与原版家具合成站等价的可携带版本，右键开对应配方 GUI；
    // 冒险模式仅可放白色混凝土标记上方，扳手可拆回）
    Block SIXTY_SECONDS_WORKBENCH = blockRegistrar.createWithItem("sixty_seconds_workbench",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).strength(2.5F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.WORKBENCH),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_STOVE = blockRegistrar.createWithItem("sixty_seconds_stove",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.SMOKER).strength(2.5F).lightLevel(s -> 6),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.STOVE),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_PURIFIER = blockRegistrar.createWithItem("sixty_seconds_purifier",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.BATHTUB),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 裁缝台：护甲/背包等装备类配方（原版对应家具：织布机）
    Block SIXTY_SECONDS_TAILOR_TABLE = blockRegistrar.createWithItem("sixty_seconds_tailor_table",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.LOOM).strength(2.5F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.TAILOR),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 军械台：近战武器/枪械/弹药/投掷物配方（原版对应家具：锻造台/砂轮/铁砧）
    Block SIXTY_SECONDS_ARSENAL_TABLE = blockRegistrar.createWithItem("sixty_seconds_arsenal_table",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.ARSENAL),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：功能方块（冒险模式可放置，仅限白色混凝土标记上方；扳手可拆）
    Block SIXTY_SECONDS_GENERATOR = blockRegistrar.createWithItem("sixty_seconds_generator",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsGeneratorBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(3.0F).lightLevel(
                            s -> s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                                    ? 7 : 0)),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_LAMP = blockRegistrar.createWithItem("sixty_seconds_lamp",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsLampBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(0.5F).lightLevel(
                            s -> s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                                    ? 14 : 0)),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_BARRICADE = blockRegistrar.createWithItem("sixty_seconds_barricade",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsBarricadeBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(4.0F),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.BARRICADE_HP),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_HEAVY_BARRICADE = blockRegistrar.createWithItem("sixty_seconds_heavy_barricade",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsBarricadeBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF).strength(6.0F),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.BARRICADE_HEAVY_HP),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_SPIKE_TRAP = blockRegistrar.createWithItem("sixty_seconds_spike_trap",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsSpikeTrapBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(1.5F).noOcclusion()),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 铁丝网：廉价版尖刺陷阱（伤害低、同样减速；电线+钉子合成，见 SixtySecondsRecipes barbed_wire）
    Block SIXTY_SECONDS_BARBED_WIRE = blockRegistrar.createWithItem("sixty_seconds_barbed_wire",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsSpikeTrapBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(1.0F).noOcclusion(),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.BARBED_WIRE_DAMAGE),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 哨戒炮：通电时自动射击范围内的怪与敌队玩家（防御科技合成；见 SixtySecondsPveSystem）
    Block SIXTY_SECONDS_TURRET = blockRegistrar.createWithItem("sixty_seconds_turret",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsTurretBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(3.0F).noOcclusion()),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 工事强化：钢筋强化路障（更高耐久层级）+ 探照灯（供电时亮度 15）
    Block SIXTY_SECONDS_REINFORCED_BARRICADE = blockRegistrar.createWithItem("sixty_seconds_reinforced_barricade",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsBarricadeBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(8.0F),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.BARRICADE_REINFORCED_HP),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_FLOODLIGHT = blockRegistrar.createWithItem("sixty_seconds_floodlight",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsLampBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(0.5F).lightLevel(
                            s -> s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                                    ? 15 : 0)),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：简易淋浴器（每人每天一次，消耗小瓶水，污染 -50%）
    Block SIXTY_SECONDS_SHOWER = blockRegistrar.createWithItem("sixty_seconds_shower",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsShowerBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.0F)),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 末日60秒模式：培育箱（种子→蔬菜的耕地系统；肥料加速，成熟右键收获）
    Block SIXTY_SECONDS_PLANTER = blockRegistrar.createWithItem("sixty_seconds_planter",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsPlanterBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).randomTicks()),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 末日60秒模式：集水器三档（被动产污染水，右键收取；见 SixtySecondsWaterCollectorBlock）
    Block SIXTY_SECONDS_RAIN_BARREL = blockRegistrar.createWithItem("sixty_seconds_rain_barrel",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsWaterCollectorBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).randomTicks(),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.COLLECTOR_BASIC_CAPACITY,
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.COLLECTOR_BASIC_INTERVAL),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_RAIN_COLLECTOR = blockRegistrar.createWithItem("sixty_seconds_rain_collector",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsWaterCollectorBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).randomTicks(),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.COLLECTOR_ROOF_CAPACITY,
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.COLLECTOR_ROOF_INTERVAL),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_CONDENSER = blockRegistrar.createWithItem("sixty_seconds_condenser",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsWaterCollectorBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F).randomTicks(),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.COLLECTOR_CONDENSER_CAPACITY,
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.COLLECTOR_CONDENSER_INTERVAL),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ══ 末日60秒模式：科技树重构批次——新合成台 ═══════════════════════════
    Block SIXTY_SECONDS_STERILE_TABLE = blockRegistrar.createWithItem("sixty_seconds_sterile_table",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.STERILE),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_ADV_WORKBENCH = blockRegistrar.createWithItem("sixty_seconds_adv_workbench",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).strength(2.5F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.ADV_WORKBENCH),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_SMELTER = blockRegistrar.createWithItem("sixty_seconds_smelter",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BLAST_FURNACE).strength(3.0F).lightLevel(s -> 8),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.SMELTER),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_BREWERY = blockRegistrar.createWithItem("sixty_seconds_brewery",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.BREWING),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_ARMOR_FORGE = blockRegistrar.createWithItem("sixty_seconds_armor_forge",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(3.0F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.ARMOR_FORGE),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_WEAPON_FORGE = blockRegistrar.createWithItem("sixty_seconds_weapon_forge",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(3.0F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.WEAPON_FORGE),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_LATHE = blockRegistrar.createWithItem("sixty_seconds_lathe",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(3.0F),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.LATHE),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_MELTING_POT = blockRegistrar.createWithItem("sixty_seconds_melting_pot",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).strength(2.5F).lightLevel(s -> 6),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.MELTING_POT),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_ALTAR = blockRegistrar.createWithItem("sixty_seconds_altar",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsStationBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(3.0F).lightLevel(s -> 5),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes.Station.ALTAR),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 高级培育箱（常规作物 2 阶段速生）+ 菌丝箱（只种蘑菇）───────────────────
    Block SIXTY_SECONDS_ADVANCED_PLANTER = blockRegistrar.createWithItem("sixty_seconds_advanced_planter",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsPlanterBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).randomTicks(),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsCrops.Tier.ADVANCED),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_MUSHROOM_BOX = blockRegistrar.createWithItem("sixty_seconds_mushroom_box",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsPlanterBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).randomTicks(),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsCrops.Tier.MUSHROOM),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_GARDENER_PLANTER = blockRegistrar.createWithItem("sixty_seconds_gardener_planter",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsPlanterBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.5F).randomTicks(),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsCrops.Tier.GARDENER),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    BlockEntityType<net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsPlanterBlockEntity> SIXTY_SECONDS_PLANTER_ENTITY =
            blockEntityRegistrar.create("sixty_seconds_planter",
                    BlockEntityType.Builder.of(
                            net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsPlanterBlockEntity::new,
                            ModBlocks.SIXTY_SECONDS_PLANTER,
                            ModBlocks.SIXTY_SECONDS_ADVANCED_PLANTER,
                            ModBlocks.SIXTY_SECONDS_MUSHROOM_BOX,
                            ModBlocks.SIXTY_SECONDS_GARDENER_PLANTER));

    // ── 保险库（对外队上锁的真实容器）+ 基地箱子（普通容器）─────────────────
    Block SIXTY_SECONDS_VAULT_SMALL = blockRegistrar.createWithItem("sixty_seconds_vault_small",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsVaultBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(4.0F), 2, true),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_VAULT_MEDIUM = blockRegistrar.createWithItem("sixty_seconds_vault_medium",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsVaultBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(4.5F), 3, true),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_VAULT_LARGE = blockRegistrar.createWithItem("sixty_seconds_vault_large",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsVaultBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(5.0F), 6, true),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_BASE_CHEST_SMALL = blockRegistrar.createWithItem("sixty_seconds_base_chest_small",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsVaultBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.0F), 3, false),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_BASE_CHEST_LARGE = blockRegistrar.createWithItem("sixty_seconds_base_chest_large",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsVaultBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.0F), 6, false),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    BlockEntityType<net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsVaultBlockEntity> SIXTY_SECONDS_VAULT_ENTITY =
            blockEntityRegistrar.create("sixty_seconds_vault",
                    BlockEntityType.Builder.of(
                            net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsVaultBlockEntity::new,
                            ModBlocks.SIXTY_SECONDS_VAULT_SMALL,
                            ModBlocks.SIXTY_SECONDS_VAULT_MEDIUM,
                            ModBlocks.SIXTY_SECONDS_VAULT_LARGE,
                            ModBlocks.SIXTY_SECONDS_BASE_CHEST_SMALL,
                            ModBlocks.SIXTY_SECONDS_BASE_CHEST_LARGE));

    // 哨戒炮 BE：只为客户端渲染器（炮头旋转）提供状态缓存，无数据落盘
    BlockEntityType<net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsTurretBlockEntity> SIXTY_SECONDS_TURRET_ENTITY =
            blockEntityRegistrar.create("sixty_seconds_turret",
                    BlockEntityType.Builder.of(
                            net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsTurretBlockEntity::new,
                            ModBlocks.SIXTY_SECONDS_TURRET));

    // ── 邮箱（60秒模式：9格容器，每日自动投递报纸）────────────────────────
    Block SIXTY_SECONDS_MAILBOX = blockRegistrar.createWithItem("sixty_seconds_mailbox",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsMailboxBlock(),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    BlockEntityType<net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity> SIXTY_SECONDS_MAILBOX_ENTITY =
            blockEntityRegistrar.create("sixty_seconds_mailbox",
                    BlockEntityType.Builder.of(
                            net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsMailboxBlockEntity::new,
                            ModBlocks.SIXTY_SECONDS_MAILBOX));

    // ── 基地设施：报警器/玩偶/次声波音响 + 基地门（扩容钥匙开启，搭图用）──────
    Block SIXTY_SECONDS_BASE_ALARM = blockRegistrar.createWithItem("sixty_seconds_base_alarm",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseUtilityBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.0F),
                    net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseUtilityBlock.Kind.ALARM),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_DOLL = blockRegistrar.createWithItem("sixty_seconds_doll",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseUtilityBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).strength(0.8F),
                    net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseUtilityBlock.Kind.DOLL),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_SUBWOOFER = blockRegistrar.createWithItem("sixty_seconds_subwoofer",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseUtilityBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.5F),
                    net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseUtilityBlock.Kind.SUBWOOFER),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_BASE_DOOR_1 = blockRegistrar.createWithItem("sixty_seconds_base_door_1",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseDoorBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion().strength(50.0F).noLootTable(), 1),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_BASE_DOOR_2 = blockRegistrar.createWithItem("sixty_seconds_base_door_2",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseDoorBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion().strength(50.0F).noLootTable(), 2),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_BASE_DOOR_3 = blockRegistrar.createWithItem("sixty_seconds_base_door_3",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseDoorBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).noOcclusion().strength(50.0F).noLootTable(), 3),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

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

    // ── 电力设施：蓄电池 / 发电增幅板 ──────────────────────────────────
    Block SIXTY_SECONDS_POWER_BATTERY = blockRegistrar.createWithItem("sixty_seconds_power_battery",
            new Block(BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(3.0F)),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    Block SIXTY_SECONDS_POWER_AMPLIFIER = blockRegistrar.createWithItem("sixty_seconds_power_amplifier",
            new net.exmo.sre.sixtyseconds.content.block.SixtySecondsPowerAmplifierBlock(
                    BlockBehaviour.Properties.ofFullCopy(DARK_STEEL).strength(2.0F).noOcclusion()),
            b -> new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPlaceableBlockItem(
                    b, new Item.Properties()),
            BLOCK_CREATIVE_GROUP, net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

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
