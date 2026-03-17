package io.wifi.starrailexpress.index.wathe_bridge;

import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import io.wifi.starrailexpress.block.entity.HornBlockEntity;
import io.wifi.starrailexpress.block_entity.*;
import io.wifi.starrailexpress.SRE;
import net.minecraft.world.level.block.entity.BlockEntityType;

public interface WatheBridgerBlocksEntities {
    BlockEntityTypeRegistrar registrar = new BlockEntityTypeRegistrar(SRE.WATHE_MOD_ID);

    BlockEntityType<SprinklerBlockEntity> SPRINKLER = registrar.create("sprinkler", BlockEntityType.Builder.of(SprinklerBlockEntity::new, WatheBridgerBlocks.GOLD_SPRINKLER, WatheBridgerBlocks.STAINLESS_STEEL_SPRINKLER));
    BlockEntityType<SmallDoorBlockEntity> SMALL_GLASS_DOOR = registrar.create("small_glass_door", BlockEntityType.Builder.of(SmallDoorBlockEntity::createGlass, WatheBridgerBlocks.SMALL_GLASS_DOOR));
    BlockEntityType<SmallDoorBlockEntity> SMALL_WOOD_DOOR = registrar.create("small_wood_door", BlockEntityType.Builder.of(SmallDoorBlockEntity::createWood, WatheBridgerBlocks.SMALL_WOOD_DOOR));
    BlockEntityType<SmallDoorBlockEntity> ANTHRACITE_STEEL_DOOR = registrar.create("anthracite_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(WatheBridgerBlocksEntities.ANTHRACITE_STEEL_DOOR, pos, state), WatheBridgerBlocks.ANTHRACITE_STEEL_DOOR));
    BlockEntityType<SmallDoorBlockEntity> KHAKI_STEEL_DOOR = registrar.create("khaki_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(WatheBridgerBlocksEntities.KHAKI_STEEL_DOOR, pos, state), WatheBridgerBlocks.KHAKI_STEEL_DOOR));
    BlockEntityType<SmallDoorBlockEntity> MAROON_STEEL_DOOR = registrar.create("maroon_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(WatheBridgerBlocksEntities.MAROON_STEEL_DOOR, pos, state), WatheBridgerBlocks.MAROON_STEEL_DOOR));
    BlockEntityType<SmallDoorBlockEntity> MUNTZ_STEEL_DOOR = registrar.create("muntz_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(WatheBridgerBlocksEntities.MUNTZ_STEEL_DOOR, pos, state), WatheBridgerBlocks.MUNTZ_STEEL_DOOR));
    BlockEntityType<SmallDoorBlockEntity> NAVY_STEEL_DOOR = registrar.create("navy_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(WatheBridgerBlocksEntities.NAVY_STEEL_DOOR, pos, state), WatheBridgerBlocks.NAVY_STEEL_DOOR));
    BlockEntityType<WheelBlockEntity> WHEEL = registrar.create("wheel", BlockEntityType.Builder.of((pos, state) -> new WheelBlockEntity(WatheBridgerBlocksEntities.WHEEL, pos, state), WatheBridgerBlocks.WHEEL));
    BlockEntityType<WheelBlockEntity> RUSTED_WHEEL = registrar.create("rusted_wheel", BlockEntityType.Builder.of((pos, state) -> new WheelBlockEntity(WatheBridgerBlocksEntities.RUSTED_WHEEL, pos, state), WatheBridgerBlocks.RUSTED_WHEEL));
    BlockEntityType<BeveragePlateBlockEntity> BEVERAGE_PLATE = registrar.create("beverage_plate", BlockEntityType.Builder.of(BeveragePlateBlockEntity::new, WatheBridgerBlocks.FOOD_PLATTER, WatheBridgerBlocks.DRINK_TRAY));
    BlockEntityType<TrimmedBedBlockEntity> TRIMMED_BED = registrar.create("trimmed_bed", BlockEntityType.Builder.of(TrimmedBedBlockEntity::create, WatheBridgerBlocks.RED_TRIMMED_BED, WatheBridgerBlocks.WHITE_TRIMMED_BED));
    BlockEntityType<HornBlockEntity> HORN = registrar.create("horn", BlockEntityType.Builder.of(HornBlockEntity::new, WatheBridgerBlocks.HORN));
    BlockEntityType<ChimneyBlockEntity> CHIMNEY = registrar.create("chimney", BlockEntityType.Builder.of(ChimneyBlockEntity::new, WatheBridgerBlocks.CHIMNEY));

    static void initialize() {
        registrar.registerEntries();
    }
}
