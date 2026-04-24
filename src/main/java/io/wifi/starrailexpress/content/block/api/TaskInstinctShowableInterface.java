package io.wifi.starrailexpress.content.block.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public interface TaskInstinctShowableInterface {
    @Environment(EnvType.CLIENT)

    /**
     * 仅客户端：是否渲染
     * 
     * @return
     */
    boolean shouldRender(BlockState state, BlockPos pos);

    @Environment(EnvType.CLIENT)

    /**
     * 仅客户端：渲染颜色
     * 
     * @return
     */
    int renderColor(BlockState state, BlockPos pos);

    /**
     * 需要12+。可不改
     * 
     * @return
     */
    public default int instinct_id(){
        return 12;
    }
}
