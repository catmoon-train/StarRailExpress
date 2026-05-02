package io.wifi.events.day_night_fight.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class WhiteBlock extends Block {

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public WhiteBlock(Properties properties) {
        super(properties);
    }
}
