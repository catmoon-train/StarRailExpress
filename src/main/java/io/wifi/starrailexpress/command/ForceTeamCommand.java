package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ForceTeamCommand {
  public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("forceTeam").requires(source -> source.hasPermission(2)).then(Commands
        .argument("players", EntityArgument.players())
        .then(Commands.literal("innocent")
            .executes(context -> forceTeam(context.getSource(),
                EntityArgument.getPlayers(context, "players"), 1)))
        .then(Commands.literal("neutral")
            .executes(context -> forceTeam(context.getSource(),
                EntityArgument.getPlayers(context, "players"), 2)))
        .then(Commands.literal("neutral_for_killer")
            .executes(context -> forceTeam(context.getSource(),
                EntityArgument.getPlayers(context, "players"), 3)))
        .then(Commands.literal("killer")
            .executes(context -> forceTeam(context.getSource(),
                EntityArgument.getPlayers(context, "players"), 4)))
        .then(Commands.literal("vigilante")
            .executes(context -> forceTeam(context.getSource(),
                EntityArgument.getPlayers(context, "players"), 5)))
        .then(Commands.literal("reset")
            .executes(context -> forceTeam(context.getSource(),
                EntityArgument.getPlayers(context, "players"), -1)))));
  }

  private static int forceTeam(@NotNull CommandSourceStack source, @NotNull Collection<ServerPlayer> players,
      int roleType) {

    for (var player : players) {
      PlayerRoleWeightManager.forceTeam(player.getUUID(), roleType);
    }
    if (roleType == -1) {
      source.sendSuccess(
          () -> Component
              .translatable("Reset thier forced team!")
              .withStyle(ChatFormatting.GOLD),
          true);
      return 1;
    }
    final String[] TypeMappings = { "All", "Innocent", "Neutral", "Neutral for killers", "Killer",
        "Vigilante" };
    if (players.size() == 1) {
      ServerPlayer player = players.iterator().next();
      source.sendSuccess(
          () -> Component
              .translatable("Force %s Team: %s", player.getName().getString(),
                  TypeMappings[roleType])
              .withStyle(ChatFormatting.GOLD),
          true);
    } else {
      source.sendSuccess(
          () -> Component.translatable("Force %s players team: %s", players.size(),
              TypeMappings[roleType])
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    }
    return 1;
  }
}