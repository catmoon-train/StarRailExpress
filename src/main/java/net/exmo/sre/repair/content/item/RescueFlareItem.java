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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.state.RepairModeState;

import java.util.List;

public class RescueFlareItem extends Item {
    public RescueFlareItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer medic) || !(target instanceof ServerPlayer downed)) {
            return InteractionResult.PASS;
        }
        var downedComponent = ModComponents.REPAIR_ROLES.get(downed);
        if (!RepairModeState.canUseSurvivorUtility(medic) || !downedComponent.downed
                || downedComponent.trialStand.present()) {
            return InteractionResult.PASS;
        }
        RepairModeState.revivePlayer(medic, downed);
        if ("medic".equals(ModComponents.REPAIR_ROLES.get(medic).activeRole)) {
            RepairModeState.startSkillCooldown(medic, 20 * 45, "medic_first_aid");
        }
        if (!medic.getAbilities().instabuild) {
            stack.shrink(1);
        }
        medic.getCooldowns().addCooldown(this, 20 * 10);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.rescue_flare.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
