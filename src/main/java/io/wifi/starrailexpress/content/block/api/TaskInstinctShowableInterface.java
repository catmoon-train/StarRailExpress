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
import net.minecraft.world.phys.AABB;

public interface TaskInstinctShowableInterface {
    /** 仅客户端：是否渲染任务点透视。 */
    @Environment(EnvType.CLIENT)
    boolean shouldRenderTaskInstinct(Level level, BlockState state, BlockPos pos, Player player);

    /** 仅客户端：透视颜色。 */
    @Environment(EnvType.CLIENT)
    java.awt.Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player);

    /** 自定义类型需要使用 12 以上的编号。 */
    default int taskInstinctId() { return 1145; }

    /** 多格任务方块只扫描主部件；普通方块保持逐格收录。 */
    default boolean isTaskInstinctAnchor(BlockState state) { return true; }

    /** 自定义透视边界，相对于 pos；null 使用原有碰撞/视觉合并箱。 */
    @Environment(EnvType.CLIENT)
    default AABB taskInstinctBounds(Level level, BlockState state, BlockPos pos) { return null; }
}
