package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.network.packet.CustomNarratorPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class NarratorCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("sre:narrator")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("player", EntityArgument.players()).then(
                Commands.argument("text", StringArgumentType.greedyString()).executes(NarratorCommand::sendNarrator)
                    .then(Commands.argument("should_interrupt", BoolArgumentType.bool())
                        .executes(NarratorCommand::sendNarrator_bool)))));
  }

  private static int sendNarrator_bool(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    String content = StringArgumentType.getString(ctx, "text");
    var players = EntityArgument.getPlayers(ctx, "player");
    boolean shouldInterrupt = BoolArgumentType.getBool(ctx, "should_interrupt");
    for (var p : players) {
      sendNarratorToPlayer(p, content, shouldInterrupt);
    }
    ctx.getSource().sendSuccess(() -> Component.literal("Send custom narrator to players. Content: " + content), true);
    return 1;
  }

  private static int sendNarrator(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    String content = StringArgumentType.getString(ctx, "text");
    var players = EntityArgument.getPlayers(ctx, "player");
    for (var p : players) {
      sendNarratorToPlayer(p, content);
    }
    ctx.getSource().sendSuccess(() -> Component.literal("Send custom narrator to players. Content: " + content), true);
    return 1;
  }

  public static void sendNarratorToPlayer(ServerPlayer player, String content) {
    sendNarratorToPlayer(player, content, false);
  }

  public static void sendNarratorToPlayer(ServerPlayer player, String content, boolean shouldInterrupt) {
    ServerPlayNetworking.send(player, new CustomNarratorPacket(content, shouldInterrupt));
  }
}
