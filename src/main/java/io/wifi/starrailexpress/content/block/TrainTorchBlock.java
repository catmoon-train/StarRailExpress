package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class TrainTorchBlock extends TorchBlock implements LightBlockInterface {
    public SimpleParticleType flameParticle;
    public static final MapCodec<TrainTorchBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance
            .group(PARTICLE_OPTIONS_FIELD.forGetter((torchBlock) -> torchBlock.flameParticle), propertiesCodec())
            .apply(instance, TrainTorchBlock::new));;

    public MapCodec<? extends TrainTorchBlock> codec() {
        return CODEC;
    }

    public TrainTorchBlock(SimpleParticleType simpleParticleType, Properties properties) {
        super(simpleParticleType, properties);
        properties.lightLevel(TrainTorchBlock::lightBlockSupplier);
        this.flameParticle = simpleParticleType;
        this.registerDefaultState(
                this.stateDefinition.any().setValue(LIT, true).setValue(ACTIVE, true));
    }

    public static int lightBlockSupplier(BlockState state) {
        if (state.getOptionalValue(ACTIVE).orElse(true)) {
            if (state.getOptionalValue(LIT).orElse(true)) {
                return 14;
            }
        }
        return 0;
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { LIT, ACTIVE });
    }
}
