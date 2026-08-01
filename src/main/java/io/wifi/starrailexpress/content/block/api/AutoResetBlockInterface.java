/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
     * @return 应该返回重置后的 BlockState。如果没有更改也需要返回原state。
     */
    BlockState onResetBlockState(ServerLevel level, BlockState state, BlockPos pos);

    /**
     * 重置方块实体
     * 
     * @param level
     * @param state
     * @param pos
     * @return 返回null代表不存在方块实体或者不更改方块实体。返回类方法：
     * <pre>{@code new BlockEntityInfo(
     *   blockEntity.saveCustomOnly(
     *     level.registryAccess()),
     *   blockEntity.components());}
     * </pre>
     */
    BlockEntityInfo onResetBlockEntity(ServerLevel level, BlockState state, BlockEntity blockEntity, BlockPos pos);
}
