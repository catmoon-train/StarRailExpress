package io.wifi.starrailexpress.index;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.fluid.CobblestoneFluid;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

public final class SREFluids {
    public static final FlowingFluid FLOWING_COBBLESTONE = register("flowing_cobblestone",
            new CobblestoneFluid.Flowing());
    public static final FlowingFluid COBBLESTONE = register("cobblestone",
            new CobblestoneFluid.Source());

    private SREFluids() {
    }

    public static void initialize() {
        // Forces the fluid registry entries to be initialized before their block and bucket are created.
    }

    private static <T extends Fluid> T register(String id, T fluid) {
        return Registry.register(BuiltInRegistries.FLUID, SRE.id(id), fluid);
    }
}
