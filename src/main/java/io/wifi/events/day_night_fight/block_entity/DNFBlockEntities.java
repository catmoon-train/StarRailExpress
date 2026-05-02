package io.wifi.events.day_night_fight.block_entity;

import io.wifi.events.day_night_fight.block.DNFBlocks;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class DNFBlockEntities {
    public static final BlockEntityType<DNFServingPlateBlockEntity> SERVING_PLATE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            SRE.id("dnf_serving_plate"),
            BlockEntityType.Builder.of(DNFServingPlateBlockEntity::new, DNFBlocks.SERVING_PLATE).build(null));

    public static final BlockEntityType<DNFWaterDispenserBlockEntity> WATER_DISPENSER = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            SRE.id("dnf_water_dispenser"),
            BlockEntityType.Builder.of(DNFWaterDispenserBlockEntity::new, DNFBlocks.WATER_DISPENSER).build(null));

    public static final BlockEntityType<HotbarStorageBlockEntity> HOTBAR_STORAGE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            SRE.id("dnf_hotbar_storage"),
            BlockEntityType.Builder.of(HotbarStorageBlockEntity::new, DNFBlocks.HOTBAR_STORAGE).build(null));

    private DNFBlockEntities() {
    }

    public static void initialize() {
    }
}
