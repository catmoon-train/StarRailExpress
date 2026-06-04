package io.wifi.starrailexpress.content.block.api;

import io.wifi.starrailexpress.index.TMMProperties;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public interface LightBlockInterface {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty ACTIVE = TMMProperties.ACTIVE;
}
