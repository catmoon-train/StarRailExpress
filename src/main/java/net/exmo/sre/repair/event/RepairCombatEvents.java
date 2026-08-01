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

package net.exmo.sre.repair.event;

import net.exmo.sre.repair.*;
import net.exmo.sre.repair.role.*;
import net.exmo.sre.repair.state.*;
import net.exmo.sre.repair.arena.*;
import net.exmo.sre.repair.util.*;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.agmas.noellesroles.component.ModComponents;

public final class RepairCombatEvents {
    private RepairCombatEvents() {
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (ModComponents.REPAIR_ROLES.get(serverPlayer).carrying != null) {
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.cannot_attack_carrying")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }
}
