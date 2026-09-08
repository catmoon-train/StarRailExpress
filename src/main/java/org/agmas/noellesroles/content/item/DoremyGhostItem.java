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

import org.agmas.noellesroles.role_data.killer.DoremyRoleData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DoremyGhostItem extends Item {

    private static final int COOLDOWN_TICKS = 20 * 90;

    public DoremyGhostItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        var item = player.getItemInHand(interactionHand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(item);
        }
        if (DoremyRoleData.isDreaming(player)) {
            player.displayClientMessage(
                    Component.translatable("item.noellesroles.doremy_ghost.abnormal").withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(item);
        }
        return super.use(level, player, interactionHand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        var item = super.finishUsingItem(itemStack, level, livingEntity);
        if (livingEntity instanceof ServerPlayer player) {
            {
                if (!player.isCreative())
                    player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                DoremyRoleData.tryDream(player, 5 * 20);
                player.displayClientMessage(Component.translatable("message.item.noellesroles.doremy_ghost.use"), true);
            }
        }
        return item;
    }
}
