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

package io.wifi.starrailexpress.customblock;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 自定义方块物品（放置用）。
 *
 * <p>
 * 与 {@code CustomItem} 一样，全模组只有这一个方块物品，具体是哪个自定义方块由物品上的
 * {@link io.wifi.starrailexpress.index.SREDataComponentTypes#CUSTOM_BLOCK_ID} 组件决定；
 * 放置时该组件由 {@link CustomBlockEntity#applyImplicitComponents} 写进方块实体。
 *
 * <p>
 * 名称与说明走物品栈上的 {@code ITEM_NAME} / {@code LORE} 组件（见
 * {@link CustomBlockLoader#applyData}），所以这里不需要覆写 {@code getName}。
 */
public class CustomBlockItem extends BlockItem {

    public CustomBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomBlockData data = CustomBlockLoader.getData(stack);
        if (data == null) {
            tooltip.add(Component.translatable("block.starrailexpress.custom_block.empty")
                    .withStyle(style -> style.withColor(0xFF5555)));
            return;
        }
        // 配置里没写 tooltip 时给一行提示，避免背包里看起来"什么都没有"
        if (data.tooltip.isEmpty()) {
            tooltip.add(Component.translatable("block.starrailexpress.custom_block.hint")
                    .withStyle(style -> style.withItalic(false).withColor(0xFF9E8B6E)));
        }
    }
}
