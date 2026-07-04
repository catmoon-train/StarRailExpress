package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class TrainTorchBlock extends BaseTorchBlock implements LightBlockInterface {
    public static SimpleParticleType flameParticle = ParticleTypes.FLAME;

    public static final MapCodec<TrainTorchBlock> CODEC = simpleCodec(TrainTorchBlock::new);

    public MapCodec<? extends TrainTorchBlock> codec() {
        return CODEC;
    }

    public TrainTorchBlock(Properties properties) {
        super(properties.lightLevel(TrainTorchBlock::lightBlockSupplier));
        this.registerDefaultState(
                this.stateDefinition.any().setValue(LIT, true).setValue(ACTIVE, true));
    }

    public static boolean isEnabled(BlockState state) {
        if (state.getOptionalValue(ACTIVE).orElse(true)) {
            if (state.getOptionalValue(LIT).orElse(true)) {
                return true;
            }
        }
        return false;
    }

    public static int lightBlockSupplier(BlockState state) {
        return isEnabled(state) ? 14 : 0;
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { LIT, ACTIVE });
    }

    protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
        return true;
    }

    public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
        if (!isEnabled(blockState))
            return;
        double d = (double) blockPos.getX() + (double) 0.5F;
        double e = (double) blockPos.getY() + 0.7;
        double f = (double) blockPos.getZ() + (double) 0.5F;
        level.addParticle(ParticleTypes.SMOKE, d, e, f, (double) 0.0F, (double) 0.0F, (double) 0.0F);
        level.addParticle(flameParticle, d, e, f, (double) 0.0F, (double) 0.0F, (double) 0.0F);
    }
}
