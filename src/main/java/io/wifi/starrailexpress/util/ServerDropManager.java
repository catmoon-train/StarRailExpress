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
import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.rules.DropRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.LoanContractItem;
import org.agmas.noellesroles.init.ModItems;

public class ServerDropManager {
    public static boolean onDrop(ServerPlayer player, boolean dropAll) {
        ItemStack itemStack = player.getMainHandItem();
        if (!player.isCreative() && itemStack.is(ModItems.LOAN_CONTRACT)
                && !LoanContractItem.canBeDroppedBy(itemStack, player.getUUID())) {
            return false;
        }
        if (SRE.isLobby) {
            return true;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return true;
        }
        InteractionResult result = RoleMethodDispatcher.callOnDropItem(player, itemStack);
        if (result == InteractionResult.CONSUME || result == InteractionResult.FAIL
                || result == InteractionResult.CONSUME_PARTIAL) {
            return false;
        } else if (result == InteractionResult.SUCCESS || result == InteractionResult.SUCCESS_NO_ITEM_USED) {
            return true;
        }
        if (itemStack.getItem() instanceof DropAndClearItem)
            return true;

        // 自定义列车物品的「是否可丢弃 / 仅特定职业可丢弃」是硬判定：
        // 不能再参与下面的 canDropItem / canDrop 的「或」逻辑，否则任何一个注册的
        // canDrop 谓词（例如林家「手持非钥匙即可丢」）都会把自定义物品的限制整体放行。
        if (CustomItemRuntime.isCustomItem(itemStack)) {
            return CustomItemRuntime.canDropCustomItem(player);
        }

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
