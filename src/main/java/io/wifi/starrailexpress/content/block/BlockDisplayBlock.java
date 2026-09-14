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

package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.BlockDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 方块展示方块：方块本体全透明，由 {@link BlockDisplayBlockEntity} 按原版方块展示实体的规则渲染一个方块模型。
 * 渲染见 {@code client.render.block_entity.BlockDisplayBlockEntityRenderer}。
 */
public class BlockDisplayBlock extends DisplayBlockBase {

    public BlockDisplayBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockDisplayBlockEntity(pos, state);
    }

    @Override
    public String editScreenTitleKey() {
        return "gui.display_block.block_display.title";
    }
}
