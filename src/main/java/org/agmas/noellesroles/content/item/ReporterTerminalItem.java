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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 记者终端
 *
 * <p>右键打开「记者视角页面」，查看当前戴摄像头盔的玩家视角；页面布局与监控台页面一致，
 * 左右键可以切换目标。目标扫描与相机接管都在客户端完成（客户端本来就能看到其他玩家的装备），
 * 因此服务端不需要额外状态，本类只负责在服务端提前返回。
 *
 * <p>物品栏图标沿用远程监控终端的材质；手持时不渲染模型（{@code TMMItems.INVISIBLE_ITEMS}）。
 */
public class ReporterTerminalItem extends Item implements AdventureUsable {

    public ReporterTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        list.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }
        // 客户端专用逻辑：扫描戴摄像头盔的玩家并接管相机（服务端不会解析到这个客户端类）。
        return io.wifi.starrailexpress.client.ReporterTerminalClient.open(player)
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.fail(stack);
    }
}
