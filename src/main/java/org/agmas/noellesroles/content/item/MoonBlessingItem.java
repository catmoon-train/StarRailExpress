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
import org.agmas.noellesroles.role_data.killer.PoolerRoleData;

/**
 * 月之祝福：祈月者开局自带的物品。
 * <p>
 * 右键使用 → {@link PoolerRoleData#useMoonBlessing(ServerPlayer)}：
 * 海豚的恩惠 + 速度 II + 无碰撞 15 秒，冷却 40 秒。
 * 冷却 / 是否可用都由 {@link PoolerRoleData} 记账（商店的「减少技能冷却」也能作用到它）。
 */
public class MoonBlessingItem extends Item {

    public MoonBlessingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        boolean used = PoolerRoleData.useMoonBlessing(sp);
        if (used) {
            sp.getCooldowns().addCooldown(this, PoolerRoleData.MOON_BLESSING_COOLDOWN_TICKS);
        }
        return InteractionResultHolder.success(stack);
    }
}
