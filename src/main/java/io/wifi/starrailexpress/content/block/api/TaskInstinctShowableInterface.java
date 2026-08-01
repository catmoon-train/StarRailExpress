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

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface TaskInstinctShowableInterface {
    @Environment(EnvType.CLIENT)

    /**
     * 仅客户端：是否渲染
     * 
     * @return
     */
    boolean shouldRenderTaskInstinct(Level level, BlockState state, BlockPos pos, Player player);

    @Environment(EnvType.CLIENT)

    /**
     * 仅客户端：渲染颜色
     * 
     * @return
     */
    java.awt.Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player);

    /**
     * 需要12+。可不改
     * 
     * @return
     */
    public default int taskInstinctId() {
        return 1145;
    }
}
