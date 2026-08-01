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

package net.exmo.sre.repair.command;

import com.mojang.brigadier.Command;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.role.RepairRoleDatabase;
import net.exmo.sre.repair.network.OpenRepairRoleShopS2CPacket;

import java.util.ArrayList;

public final class RepairShopCommand {
    private RepairShopCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("cy:repairshop").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
                if (game != null && game.isRunning()) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.repair.shop_ingame")
                            .withStyle(ChatFormatting.RED), true);
                    return 0;
                }
                RepairRoleDatabase.loadInto(player);
                var component = ModComponents.REPAIR_ROLES.get(player);
                ServerPlayNetworking.send(player, new OpenRepairRoleShopS2CPacket(ItemSkinManager.getCoinNum(player),
                        new ArrayList<>(component.ownedRoles)));
                return Command.SINGLE_SUCCESS;
            }));
        });
    }
}
