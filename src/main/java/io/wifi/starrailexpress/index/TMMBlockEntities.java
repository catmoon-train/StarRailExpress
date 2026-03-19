package io.wifi.starrailexpress.index;

import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import io.wifi.starrailexpress.block.entity.HornBlockEntity;
import io.wifi.starrailexpress.block_entity.*;
import io.wifi.starrailexpress.SRE;
import net.minecraft.world.level.block.entity.BlockEntityType;

public interface TMMBlockEntities {
    BlockEntityTypeRegistrar registrar = new BlockEntityTypeRegistrar(SRE.TMM_MOD_ID);

    BlockEntityType<SprinklerBlockEntity> SPRINKLER = registrar.create("sprinkler", BlockEntityType.Builder.of(SprinklerBlockEntity::new, TMMBlocks.GOLD_SPRINKLER, TMMBlocks.STAINLESS_STEEL_SPRINKLER));
    BlockEntityType<SmallDoorBlockEntity> SMALL_GLASS_DOOR = registrar.create("small_glass_door", BlockEntityType.Builder.of(SmallDoorBlockEntity::createGlass, TMMBlocks.SMALL_GLASS_DOOR));
    BlockEntityType<SmallDoorBlockEntity> SMALL_WOOD_DOOR = registrar.create("small_wood_door", BlockEntityType.Builder.of(SmallDoorBlockEntity::createWood, TMMBlocks.SMALL_WOOD_DOOR));
    BlockEntityType<SmallDoorBlockEntity> ANTHRACITE_STEEL_DOOR = registrar.create("anthracite_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(TMMBlockEntities.ANTHRACITE_STEEL_DOOR, pos, state), TMMBlocks.ANTHRACITE_STEEL_DOOR));
    BlockEntityType<SmallDoorBlockEntity> KHAKI_STEEL_DOOR = registrar.create("khaki_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(TMMBlockEntities.KHAKI_STEEL_DOOR, pos, state), TMMBlocks.KHAKI_STEEL_DOOR));
    BlockEntityType<SmallDoorBlockEntity> MAROON_STEEL_DOOR = registrar.create("maroon_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(TMMBlockEntities.MAROON_STEEL_DOOR, pos, state), TMMBlocks.MAROON_STEEL_DOOR));
    BlockEntityType<SmallDoorBlockEntity> MUNTZ_STEEL_DOOR = registrar.create("muntz_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(TMMBlockEntities.MUNTZ_STEEL_DOOR, pos, state), TMMBlocks.MUNTZ_STEEL_DOOR));
    BlockEntityType<SmallDoorBlockEntity> NAVY_STEEL_DOOR = registrar.create("navy_steel_door", BlockEntityType.Builder.of((pos, state) -> new SmallDoorBlockEntity(TMMBlockEntities.NAVY_STEEL_DOOR, pos, state), TMMBlocks.NAVY_STEEL_DOOR));
    BlockEntityType<WheelBlockEntity> WHEEL = registrar.create("wheel", BlockEntityType.Builder.of((pos, state) -> new WheelBlockEntity(TMMBlockEntities.WHEEL, pos, state), TMMBlocks.WHEEL));
    BlockEntityType<WheelBlockEntity> RUSTED_WHEEL = registrar.create("rusted_wheel", BlockEntityType.Builder.of((pos, state) -> new WheelBlockEntity(TMMBlockEntities.RUSTED_WHEEL, pos, state), TMMBlocks.RUSTED_WHEEL));
    BlockEntityType<BeveragePlateBlockEntity> BEVERAGE_PLATE = registrar.create("beverage_plate", BlockEntityType.Builder.of(BeveragePlateBlockEntity::new, TMMBlocks.FOOD_PLATTER, TMMBlocks.DRINK_TRAY));
    BlockEntityType<TrimmedBedBlockEntity> TRIMMED_BED = registrar.create("trimmed_bed", BlockEntityType.Builder.of(TrimmedBedBlockEntity::create, TMMBlocks.RED_TRIMMED_BED, TMMBlocks.WHITE_TRIMMED_BED));
    BlockEntityType<HornBlockEntity> HORN = registrar.create("horn", BlockEntityType.Builder.of(HornBlockEntity::new, TMMBlocks.HORN));
    BlockEntityType<ChimneyBlockEntity> CHIMNEY = registrar.create("chimney", BlockEntityType.Builder.of(ChimneyBlockEntity::new, TMMBlocks.CHIMNEY));
    BlockEntityType<CameraBlockEntity> CAMERA = registrar.create("camera", BlockEntityType.Builder.of(CameraBlockEntity::new, TMMBlocks.CAMERA));
    BlockEntityType<SecurityMonitorBlockEntity> SECURITY_MONITOR = registrar.create("security_monitor", BlockEntityType.Builder.of(SecurityMonitorBlockEntity::new, TMMBlocks.SECURITY_MONITOR));
    BlockEntityType<ToiletBlockEntity> TOILET = registrar.create("toilet", BlockEntityType.Builder.of(ToiletBlockEntity::create, TMMBlocks.LIGHT_TOILET, TMMBlocks.DARK_TOILET));

    static void initialize() {
        registrar.registerEntries();
    }
}
