package org.agmas.noellesroles.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
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
                                        .translatable("%s: %s", p.getDisplayName(),
                                                RoomNumberToStr(
                                                        GameFunctions.roomToPlayer.getOrDefault(p.getUUID(), -1)))
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
                        .translatable("%s: %s", p.getDisplayName(),
                                RoomNumberToStr(GameFunctions.roomToPlayer.getOrDefault(p.getUUID(), -1)))
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