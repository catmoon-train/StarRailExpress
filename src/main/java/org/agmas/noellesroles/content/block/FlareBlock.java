package org.agmas.noellesroles.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 照明弹方块 - 无碰撞体积，发光，10秒后自动消失
 */
public class FlareBlock extends Block {
    public FlareBlock() {
        super(BlockBehaviour.Properties.of()
                .noOcclusion()
                .noCollission()
                .lightLevel(state -> 15)
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.GLASS)
                .randomTicks());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, 200); // 10秒 = 200 ticks
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // 粒子效果
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.FLAME, cx + random.nextGaussian() * 0.1, cy + random.nextGaussian() * 0.1, cz + random.nextGaussian() * 0.1, 0, 0.02, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, cx + random.nextGaussian() * 0.15, cy + 0.3 + random.nextGaussian() * 0.1, cz + random.nextGaussian() * 0.15, 0, 0.05, 0);
        }
    }
}
