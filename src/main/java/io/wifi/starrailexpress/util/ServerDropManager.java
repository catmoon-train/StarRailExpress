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

package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DropAndClearItem;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.rules.DropRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public class ServerDropManager {
    public static boolean onDrop(ServerPlayer player, boolean dropAll) {
        if (SRE.isLobby) {
            return true;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return true;
        }
        ItemStack itemStack = player.getMainHandItem();
        InteractionResult result = RoleMethodDispatcher.callOnDropItem(player, itemStack);
        if (result == InteractionResult.CONSUME || result == InteractionResult.FAIL
                || result == InteractionResult.CONSUME_PARTIAL) {
            return false;
        } else if (result == InteractionResult.SUCCESS || result == InteractionResult.SUCCESS_NO_ITEM_USED) {
            return true;
        }
        if (itemStack.getItem() instanceof DropAndClearItem)
            return true;

        if (DropRules.canDropItem
                .contains(BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString())
                || DropRules.canDrop.stream().anyMatch((p) -> {
                    return p.test(player);
                })) {
            return true;
        }
        return false;
    }
}
