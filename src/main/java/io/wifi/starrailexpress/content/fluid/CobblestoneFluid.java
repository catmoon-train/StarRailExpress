package io.wifi.starrailexpress.content.fluid;

import io.wifi.starrailexpress.index.SREDecorationBlocks;
import io.wifi.starrailexpress.index.SREFluids;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;

/** Lava-like custom fluid used by the cobblestone liquid bucket. */
public abstract class CobblestoneFluid extends LavaFluid {
    @Override
    public Fluid getFlowing() {
        return SREFluids.FLOWING_COBBLESTONE;
    }

    @Override
    public Fluid getSource() {
        return SREFluids.COBBLESTONE;
    }

    @Override
    public Item getBucket() {
        return SREDecorationBlocks.COBBLESTONE_BUCKET;
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return SREDecorationBlocks.COBBLESTONE_LIQUID.defaultBlockState()
                .setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    public static class Flowing extends CobblestoneFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends CobblestoneFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
