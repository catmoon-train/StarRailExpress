package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public class TrainCarvedPumpkinBlock extends CarvedPumpkinBlock implements LightBlockInterface {

    public TrainCarvedPumpkinBlock(Properties properties) {
        super(properties);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(new Property[] { LIT, ACTIVE });
    }
}