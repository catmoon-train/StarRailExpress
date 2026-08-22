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

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role_data.killer.DreamRoleData;

/**
 * 巨幕面具（Dream 商店 350 金币）。
 *
 * <p>正常流程下<b>不会进入背包</b>：商店购买即触发
 * {@link DreamRoleData#activateMaskBerserk}（Psycho/疯魔逻辑，Dream 角色
 * 不发球棒），冷却挂在本物品上。保留 use() 仅供创造模式测试。
 */
public class DreamMaskItem extends Item {
    public DreamMaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }
        if (player instanceof ServerPlayer sp && DreamRoleData.activateMaskBerserk(sp)) {
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }
}
