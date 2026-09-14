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

import io.wifi.starrailexpress.content.block_entity.ItemDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 物品展示方块：方块本体全透明，由 {@link ItemDisplayBlockEntity} 按原版物品展示实体的规则渲染一个物品堆。
 */
public class ItemDisplayBlock extends DisplayBlockBase {

    public ItemDisplayBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemDisplayBlockEntity(pos, state);
    }

    @Override
    public String editScreenTitleKey() {
        return "gui.display_block.item_display.title";
    }
}
