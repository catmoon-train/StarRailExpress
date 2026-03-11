package io.wifi.starrailexpress.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.LevelStem;

public class TMMDatagen implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator dataGenerator) {
        // this is so that the dimension options can actually generate
        DynamicRegistries.register(Registries.LEVEL_STEM, LevelStem.CODEC);

        FabricDataGenerator.Pack pack = dataGenerator.createPack();
        pack.addProvider(TMMModelGen::new);
        pack.addProvider(TMMBlockTagGen::new);
        pack.addProvider(TMMItemTagGen::new);
        pack.addProvider(TMMLangGen::new);
        pack.addProvider(TMMBlockLootTableGen::new);
    }
}
