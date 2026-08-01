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
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.neutral.reasoner.ReasonerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class ReasonerCompassItem extends Item {
    public ReasonerCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        if (!game.isRole(player, ModRoles.REASONER) || !game.isRunning() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.reasoner.unavailable").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        ReasonerPlayerComponent.KEY.get(player).openCompass(serverPlayer);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
