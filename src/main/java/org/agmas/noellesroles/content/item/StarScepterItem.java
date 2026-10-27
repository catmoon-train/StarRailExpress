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
import org.agmas.noellesroles.role_data.innocence.StarGathererRoleData;

/**
 * 星之权杖：摘星者开局自带的物品。
 * <p>
 * 右键 → 锁定视线方向 35 格内最近的玩家 1.2 秒，2 秒后星星从高空砸下来（死因「星星」）。
 * 冷却 / 归星目标都由 {@link StarGathererRoleData} 记账。
 */
public class StarScepterItem extends Item {

    public StarScepterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        StarGathererRoleData.useScepter(sp);
        return InteractionResultHolder.success(stack);
    }
}
