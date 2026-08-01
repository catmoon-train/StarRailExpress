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

package net.exmo.sre.repair.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.exmo.sre.repair.util.RepairGameplayEffects;
import net.exmo.sre.repair.state.RepairModeState;

import java.util.List;

public class SmokePelletItem extends Item {
    public SmokePelletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (!RepairModeState.canUseSurvivorUtility(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            RepairGameplayEffects.burst(serverLevel, player.getX(), player.getY() + 0.8D, player.getZ(), 1);
            RepairGameplayEffects.disorientHunters(serverLevel, player.getX(), player.getY(), player.getZ(), 5.5D, 90);
            for (net.minecraft.server.level.ServerPlayer hunter : serverLevel.players()) {
                if (RepairModeState.isHunter(hunter) && hunter.distanceToSqr(player) <= 6.5D * 6.5D) {
                    RepairModeState.blockHunterCarry(hunter, 20 * 8);
                    hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.carry_jammed")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.getCooldowns().addCooldown(this, 20 * 18);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.smoke_pellet.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
