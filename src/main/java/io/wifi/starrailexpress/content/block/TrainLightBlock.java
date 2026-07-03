package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import io.wifi.starrailexpress.index.SREBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.ToIntFunction;

public class TrainLightBlock extends LightBlock implements LightBlockInterface {
    public static final ToIntFunction<BlockState> LIGHT_EMISSION;
    public static final MapCodec<LightBlock> CODEC = simpleCodec(LightBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public MapCodec<LightBlock> codec() {
        return CODEC;
    }

    protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos,
            CollisionContext collisionContext) {
        return collisionContext.isHoldingItem(SREBlocks.TRAIN_LIGHT.asItem())
                || collisionContext.isHoldingItem(Items.DEBUG_STICK) ? Shapes.block() : Shapes.empty();
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos,
            boolean notify) {
        if (!world.isClientSide) {
            boolean isPowered = world.hasNeighborSignal(pos);
            if (isPowered != state.getValue(POWERED)) {
                state = state.setValue(POWERED, isPowered);
                if (isPowered != state.getValue(LIT)) {
                    state = state.setValue(LIT, isPowered);
                }
                world.setBlock(pos, state, 2);
            }
        }
    }

    @Override
    public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
        if (level.isClientSide && SRE.canSeeBarrier()) {
            if ((blockState.getValue(LIT))) {
                double d = (double) blockPos.getX() + (double) 0.5F + (randomSource.nextDouble() - (double) 0.5F) * 0.2;
                double e = (double) blockPos.getY() + 0.7 + (randomSource.nextDouble() - (double) 0.5F) * 0.2;
                double f = (double) blockPos.getZ() + (double) 0.5F + (randomSource.nextDouble() - (double) 0.5F) * 0.2;
                level.addParticle(DustParticleOptions.REDSTONE, d, e, f, (double) 0.0F, (double) 0.0F, (double) 0.0F);
            }
            BlockParticleOption particleEffect = new BlockParticleOption(ParticleTypes.BLOCK_MARKER, blockState);
            level.addAlwaysVisibleParticle(particleEffect, blockPos.getX() + 0.5, blockPos.getY() + 0.5,
                    blockPos.getZ() + 0.5,
                    (double) 0.0F,
                    (double) 0.0F, (double) 0.0F);

        }
    }

    public TrainLightBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 15).setValue(WATERLOGGED, false)
                .setValue(LIT, true).setValue(ACTIVE, true).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { LEVEL, WATERLOGGED, LIT, ACTIVE, POWERED });
    }

    static {
        LIGHT_EMISSION = (state) -> state.getValue(LIT) && state.getValue(ACTIVE) ? state.getValue(LEVEL) : 0;
    }
}
