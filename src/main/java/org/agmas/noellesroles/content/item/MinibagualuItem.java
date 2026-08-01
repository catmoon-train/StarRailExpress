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

import org.agmas.noellesroles.utils.RoleUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MinibagualuItem extends Item {
    final int COOLDOWN_TICKS = 30 * 20;

    public MinibagualuItem(Properties properties) {
        super(properties);
    }

    public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity,
            InteractionHand interactionHand) {
        if (!interactionHand.equals(InteractionHand.MAIN_HAND))
            return InteractionResult.PASS;
        if (player.getCooldowns().isOnCooldown(this))
            return InteractionResult.FAIL;
        if (!(livingEntity instanceof Player target)) {
            return InteractionResult.PASS;
        }

        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        var role = RoleUtils.getPlayerRole(target);
        Component roleType = RoleUtils.getRoleTypeName(role);
        player.displayClientMessage(Component.translatable("message.noellesroles.mini_bagualu.info", roleType)
                .withStyle(ChatFormatting.GOLD), true);
        if (!player.isCreative()) {
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            itemStack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        return InteractionResult.SUCCESS;
    }
}
