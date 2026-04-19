package io.wifi.starrailexpress.content.block.api;

import io.wifi.starrailexpress.game.GameUtils.BlockEntityInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface AutoResetBlockInterface {
    /**
     * 重置方块
     * 
     * @param level
     * @param state
     * @param pos
     * @return 应该返回重置后的 BlockState
     */
    BlockState onResetBlockState(ServerLevel level, BlockState state, BlockPos pos);

    /**
     * 重置方块实体
     * 
     * @param level
     * @param state
     * @param pos
     * @return 返回null代表不存在方块实体或者不更改方块实体。返回类方法：
     *         {@code new BlockEntityInfo(blockEntity.saveCustomOnly(level.registryAccess()),blockEntity.components());}
     */
    BlockEntityInfo onResetBlockEntity(ServerLevel level, BlockState state, BlockEntity blockEntity, BlockPos pos);
}
