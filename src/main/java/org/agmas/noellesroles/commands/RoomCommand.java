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

package org.agmas.noellesroles.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

public class RoomCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("room")
                    .requires(source -> source.hasPermission(2))
                    .executes((context) -> {
                        return execute(context);
                    }).then(Commands.argument("player", EntityArgument.player()).executes((context) -> {
                        return executeWithPlayer(context);
                    })));
        });
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        try {
            final var server = context.getSource().getServer();
            var areas = AreasWorldComponent.KEY.get(server.overworld());
            for (var p : server.getPlayerList().getPlayers()) {
                context.getSource()
                        .sendSystemMessage(
                                Component
                                        .translatable("%s: %s", p.getName(),
                                                RoomNumberToStr(
                                                        GameUtils.roomToPlayer.getOrDefault(p.getUUID(), -1)))
                                        .withStyle(ChatFormatting.AQUA));
            }
            context.getSource().sendSuccess(() -> {
                return Component.literal("Room Count: " + areas.getRoomCount()).withStyle(ChatFormatting.GOLD);
            }, false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static int executeWithPlayer(CommandContext<CommandSourceStack> context) {
        try {
            final var server = context.getSource().getServer();
            var p = EntityArgument.getPlayer(context, "player");
            var areas = AreasWorldComponent.KEY.get(server.overworld());
            context.getSource().sendSuccess(() -> {
                return Component
                        .translatable("%s: %s", p.getName(),
                                RoomNumberToStr(GameUtils.roomToPlayer.getOrDefault(p.getUUID(), -1)))
                        .append(Component.literal("\nRoom Count: " + areas.getRoomCount())
                                .withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GREEN);
            }, false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static String RoomNumberToStr(int value) {
        if (value > 0) {
            return "Room " + value;
        } else
            return "No Room";
    }
}