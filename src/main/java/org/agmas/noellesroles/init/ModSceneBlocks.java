package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block.scene.BreakingBridgeBlock;
import org.agmas.noellesroles.content.block.scene.CellarBlock;
import org.agmas.noellesroles.content.block.scene.CoffinBlock;
import org.agmas.noellesroles.content.block.scene.DrippingStalactiteBlock;
import org.agmas.noellesroles.content.block.scene.FlamethrowerBlock;
import org.agmas.noellesroles.content.block.scene.FogZoneBlock;
import org.agmas.noellesroles.content.block.scene.HurricaneDeviceBlock;
import org.agmas.noellesroles.content.block.scene.ManholeBlock;
import org.agmas.noellesroles.content.block.scene.PoisonZoneBlock;
import org.agmas.noellesroles.content.block.scene.BushBlock;
import org.agmas.noellesroles.content.block.scene.CropBlock;
import org.agmas.noellesroles.content.block.scene.DustBlock;
import org.agmas.noellesroles.content.block.scene.IncineratorBlock;
import org.agmas.noellesroles.content.block.scene.MovingPlatformBlock;
import org.agmas.noellesroles.content.block.scene.StatueBlock;
import org.agmas.noellesroles.content.block.scene.StoveBlock;
import org.agmas.noellesroles.content.block.scene.TransportPointBlock;
import org.agmas.noellesroles.content.block_entity.scene.CropBlockEntity;
import org.agmas.noellesroles.content.block.scene.ReactorBlock;
import org.agmas.noellesroles.content.block.scene.RollingStoneTriggerPlate;
import org.agmas.noellesroles.content.block.scene.WaterValveBlock;
import org.agmas.noellesroles.content.block.scene.WaterPumpBlock;
import org.agmas.noellesroles.content.block.scene.SceneGateBlock;
import org.agmas.noellesroles.content.block.scene.TrainTargetBlock;
import org.agmas.noellesroles.content.block_entity.scene.CoffinBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.FlamethrowerBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.HurricaneDeviceBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.IncineratorBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.MovingPlatformBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.ReactorBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.RollingStoneTriggerPlateEntity;
import org.agmas.noellesroles.content.block_entity.scene.WaterValveBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.WaterPumpBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.FogZoneBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.ManholeBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.PoisonZoneBlockEntity;
import org.agmas.noellesroles.content.block_entity.scene.SceneGateBlockEntity;

/**
 * 场景方块注册与" SRE 场景方块"创造标签栏。
 */
public interface ModSceneBlocks {

    ResourceKey<CreativeModeTab> SCENE_CREATIVE_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Noellesroles.id("scene"));

    /** SRE 任务点方块创造标签栏。 */
    ResourceKey<CreativeModeTab> QUEST_CREATIVE_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Noellesroles.id("quest"));

    BlockRegistrar blockRegistrar = new BlockRegistrar(Noellesroles.MOD_ID);
    BlockEntityTypeRegistrar blockEntityRegistrar = new BlockEntityTypeRegistrar(Noellesroles.MOD_ID);

    // ───────────────────────── 批次A：陷阱类 ─────────────────────────

    Block POISON_ZONE = registerBlock("poison_zone",
            new PoisonZoneBlock(Properties.ofFullCopy(Blocks.SLIME_BLOCK).noOcclusion().strength(-1.0F, 3600000.0F)));
    Block BREAKING_BRIDGE = registerBlock("breaking_bridge",
            new BreakingBridgeBlock(Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    Block DRIPPING_STALACTITE = registerBlock("dripping_stalactite",
            new DrippingStalactiteBlock(Properties.ofFullCopy(Blocks.DRIPSTONE_BLOCK).randomTicks()));

    BlockEntityType<PoisonZoneBlockEntity> POISON_ZONE_ENTITY = blockEntityRegistrar.create("poison_zone",
            BlockEntityType.Builder.of(PoisonZoneBlockEntity::new, POISON_ZONE));

    // ───────────────────────── 批次B：区域/通道类 ─────────────────────────

    Block FOG_ZONE = registerBlock("fog_zone",
            new FogZoneBlock(Properties.ofFullCopy(Blocks.WHITE_STAINED_GLASS).strength(-1.0F, 3600000.0F)));
    Block MANHOLE = registerBlock("manhole",
            new ManholeBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
    Block CELLAR = registerBlock("cellar",
            new CellarBlock(Properties.ofFullCopy(Blocks.SPRUCE_PLANKS)));

    BlockEntityType<FogZoneBlockEntity> FOG_ZONE_ENTITY = blockEntityRegistrar.create("fog_zone",
            BlockEntityType.Builder.of(FogZoneBlockEntity::new, FOG_ZONE));
    BlockEntityType<ManholeBlockEntity> MANHOLE_ENTITY = blockEntityRegistrar.create("manhole",
            BlockEntityType.Builder.of(ManholeBlockEntity::new, MANHOLE));

    // ───────────────────────── 批次C：破坏任务联动 ─────────────────────────

    Block SCENE_GATE = registerBlock("scene_gate",
            new SceneGateBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(-1.0F, 3600000.0F)));
    Block FLAMETHROWER = registerBlock("flamethrower",
            new FlamethrowerBlock(Properties.ofFullCopy(Blocks.MAGMA_BLOCK).lightLevel(s -> 7)));

    BlockEntityType<SceneGateBlockEntity> SCENE_GATE_ENTITY = blockEntityRegistrar.create("scene_gate",
            BlockEntityType.Builder.of(SceneGateBlockEntity::new, SCENE_GATE));
    Block REACTOR = registerQuestBlock("reactor",
            new ReactorBlock(Properties.ofFullCopy(Blocks.NETHERITE_BLOCK)
                    .lightLevel(state -> state.getValue(ReactorBlock.ACTIVE) ? 12 : 0)));
    Block WATER_VALVE = registerQuestBlock("water_valve",
            new WaterValveBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(WaterValveBlock.ACTIVE) ? 8 : 0)));

    BlockEntityType<FlamethrowerBlockEntity> FLAMETHROWER_ENTITY = blockEntityRegistrar.create("flamethrower",
            BlockEntityType.Builder.of(FlamethrowerBlockEntity::new, FLAMETHROWER));
    Block ROLLING_STONE_TRIGGER = registerBlock("rolling_stone_trigger",
            new RollingStoneTriggerPlate(Properties.ofFullCopy(Blocks.STONE).noOcclusion()));

    BlockEntityType<ReactorBlockEntity> REACTOR_ENTITY = blockEntityRegistrar.create("reactor",
            BlockEntityType.Builder.of(ReactorBlockEntity::new, REACTOR));
    BlockEntityType<WaterValveBlockEntity> WATER_VALVE_ENTITY = blockEntityRegistrar.create("water_valve",
            BlockEntityType.Builder.of(WaterValveBlockEntity::new, WATER_VALVE));
    BlockEntityType<RollingStoneTriggerPlateEntity> ROLLING_STONE_TRIGGER_ENTITY = blockEntityRegistrar.create(
            "rolling_stone_trigger",
            BlockEntityType.Builder.of(RollingStoneTriggerPlateEntity::new, ROLLING_STONE_TRIGGER));

    // ───────────────────────── 批次D：特殊机关 ─────────────────────────

    Block TRAIN_TARGET = registerBlock("train_target",
            new TrainTargetBlock(Properties.ofFullCopy(Blocks.TARGET)));
    Block INCINERATOR = registerBlock("incinerator",
            new IncineratorBlock(Properties.ofFullCopy(Blocks.FURNACE).lightLevel(s -> 13)));
    Block MOVING_PLATFORM = registerBlock("moving_platform",
            new MovingPlatformBlock(Properties.ofFullCopy(Blocks.SMOOTH_STONE)));
    Block HURRICANE_DEVICE = registerBlock("hurricane_device",
            new HurricaneDeviceBlock(Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion()));
    Block COFFIN = registerBlock("coffin",
            new CoffinBlock(Properties.ofFullCopy(Blocks.DARK_OAK_PLANKS).noOcclusion()));
    Block WATER_PUMP = registerBlock("water_pump",
            new WaterPumpBlock(Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    BlockEntityType<IncineratorBlockEntity> INCINERATOR_ENTITY = blockEntityRegistrar.create("incinerator",
            BlockEntityType.Builder.of(IncineratorBlockEntity::new, INCINERATOR));
    BlockEntityType<MovingPlatformBlockEntity> MOVING_PLATFORM_ENTITY = blockEntityRegistrar.create("moving_platform",
            BlockEntityType.Builder.of(MovingPlatformBlockEntity::new, MOVING_PLATFORM));
    BlockEntityType<HurricaneDeviceBlockEntity> HURRICANE_DEVICE_ENTITY = blockEntityRegistrar.create("hurricane_device",
            BlockEntityType.Builder.of(HurricaneDeviceBlockEntity::new, HURRICANE_DEVICE));
    BlockEntityType<CoffinBlockEntity> COFFIN_ENTITY = blockEntityRegistrar.create("coffin",
            BlockEntityType.Builder.of(CoffinBlockEntity::new, COFFIN));
    BlockEntityType<WaterPumpBlockEntity> WATER_PUMP_ENTITY = blockEntityRegistrar.create("water_pump",
            BlockEntityType.Builder.of(WaterPumpBlockEntity::new, WATER_PUMP));

    // ───────────────────────── 场景任务点方块 → SRE 任务点方块 ─────────────────────────

    Block STOVE = registerQuestBlock("scene_stove",
            new StoveBlock(Properties.ofFullCopy(Blocks.FURNACE)
                    .lightLevel(state -> state.getValue(StoveBlock.LIT) ? 13 : 0)));
    Block DUST = registerQuestBlock("scene_dust",
            new DustBlock(Properties.ofFullCopy(Blocks.GRAVEL).noOcclusion()));
    Block TRANSPORT_POINT = registerQuestBlock("transport_point",
            new TransportPointBlock(Properties.ofFullCopy(Blocks.BARREL)));
    Block STATUE = registerQuestBlock("scene_statue",
            new StatueBlock(Properties.ofFullCopy(Blocks.CHISELED_QUARTZ_BLOCK)));
    Block BUSH = registerQuestBlock("scene_bush",
            new BushBlock(Properties.ofFullCopy(Blocks.OAK_LEAVES).noOcclusion()));
    Block CROP = registerQuestBlock("scene_crop",
            new CropBlock(Properties.ofFullCopy(Blocks.HAY_BLOCK)));

    BlockEntityType<CropBlockEntity> CROP_ENTITY = blockEntityRegistrar.create("scene_crop",
            BlockEntityType.Builder.of(CropBlockEntity::new, CROP));

    // ───────────────────────── 注册 ─────────────────────────

    @SuppressWarnings("unchecked")
    static <T extends Block> T registerBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, SCENE_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    static <T extends Block> T registerBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings, SCENE_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    static <T extends Block> T registerQuestBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, QUEST_CREATIVE_GROUP);
    }

    static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SCENE_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.noellesroles.scene"))
                .icon(() -> new ItemStack(DRIPPING_STALACTITE.asItem()))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, QUEST_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.noellesroles.quest"))
                .icon(() -> new ItemStack(REACTOR.asItem()))
                .build());

        // 将原版黑色混凝土加入 SRE 任务点方块分类
        ItemGroupEvents.modifyEntriesEvent(QUEST_CREATIVE_GROUP).register(entries -> {
            entries.accept(Items.BLACK_CONCRETE, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            entries.accept(Items.NOTE_BLOCK, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            entries.accept(Items.LECTERN, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        });

        blockRegistrar.registerEntries();
        blockEntityRegistrar.registerEntries();
    }
}
