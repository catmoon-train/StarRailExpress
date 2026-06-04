package io.wifi.starrailexpress.content.block;

import java.util.function.ToIntFunction;

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
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

public class TrainLightBlock extends LightBlock implements LightBlockInterface {
    public static final ToIntFunction<BlockState> LIGHT_EMISSION;
    public static final MapCodec<LightBlock> CODEC = simpleCodec(LightBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public MapCodec<LightBlock> codec() {
        return CODEC;
    }

   protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
      return collisionContext.isHoldingItem(TMMBlocks.TRAIN_LIGHT.asItem()) ? Shapes.block() : Shapes.empty();
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
