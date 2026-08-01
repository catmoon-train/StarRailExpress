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

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;

public class WrittenNoteItem extends Item {

    public WrittenNoteItem(Properties properties) {
        super(properties);
    }
    public static Runnable openScreenCallback = null;
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (level.isClientSide) {
            // 检查是否为记录员角色
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
            if (gameWorld.isRole(player, ModRoles.RECORDER)) {
                if (openScreenCallback != null) {
                    openScreenCallback.run();
                }
                return InteractionResultHolder.success(player.getItemInHand(usedHand));
            }
        }

        return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }
}