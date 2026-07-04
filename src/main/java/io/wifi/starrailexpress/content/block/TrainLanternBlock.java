package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class TrainLanternBlock extends LanternBlock implements LightBlockInterface {

    public TrainLanternBlock(Properties properties) {
        super(properties);
    }

    protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
        return true;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(new Property[] { HANGING, WATERLOGGED, LIT, ACTIVE });
    }
}
