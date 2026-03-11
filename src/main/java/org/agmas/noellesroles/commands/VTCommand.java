package org.agmas.noellesroles.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.PlayerVolumeComponent;

public class VTCommand {
  public static void register() {
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
      dispatcher.register(Commands.literal("vt_mode").executes(VTCommand::executeWithOutPlayer)
          .then(Commands.argument("player", EntityArgument.player())
              .requires(source -> source.hasPermission(2))
              .executes(VTCommand::executeWithPlayer)
              .then(Commands.argument("status", BoolArgumentType.bool())
                  .executes(VTCommand::executeWithPlayerAndStatus))));
    });
  }

  private static int executeWithPlayer(CommandContext<CommandSourceStack> context) {
    try {
      ServerPlayer player = EntityArgument.getPlayer(context, "player");
      return execute(context, player);
    } catch (CommandSyntaxException e) {
      context.getSource().sendFailure(Component.literal("ERROR: " + e.getMessage()));
      e.printStackTrace();
    }
    return 0;
  }

  private static int executeWithPlayerAndStatus(CommandContext<CommandSourceStack> context) {
    try {
      ServerPlayer player = EntityArgument.getPlayer(context, "player");
      Boolean enabled = BoolArgumentType.getBool(context, "status");
      if (player == null)
        return 0;
      try {
        PlayerVolumeComponent component = PlayerVolumeComponent.KEY.get(player);
        component.vtMode = enabled;
        if (component.vtMode) {
          Component MSG = Component
              .translatable("message.noellesroles.vt_mode.enabled", player.getDisplayName())
              .withStyle(ChatFormatting.YELLOW);
          player.sendSystemMessage(MSG);
          context.getSource().sendSuccess(() -> MSG, true);
        } else {
          Component MSG = Component
              .translatable("message.noellesroles.vt_mode.disabled", player.getDisplayName())
              .withStyle(ChatFormatting.GREEN);
          player.sendSystemMessage(MSG);
          context.getSource().sendSuccess(() -> MSG, true);
        }

        return Command.SINGLE_SUCCESS;
      } catch (Exception e) {
        return 0;
      }
    } catch (CommandSyntaxException e) {
      context.getSource().sendFailure(Component.literal("ERROR: " + e.getMessage()));
      e.printStackTrace();
    }
    return 0;
  }

  private static int executeWithOutPlayer(CommandContext<CommandSourceStack> context) {
    ServerPlayer player = context.getSource().getPlayer();
    return execute(context, player);
  }

  private static int execute(CommandContext<CommandSourceStack> context, ServerPlayer player) {
    if (player == null)
      return 0;
    try {
      PlayerVolumeComponent component = PlayerVolumeComponent.KEY.get(player);
      component.vtMode = !component.vtMode;
      if (component.vtMode) {
        Component MSG = Component.translatable("message.noellesroles.vt_mode.enabled", player.getDisplayName())
            .withStyle(ChatFormatting.YELLOW);
        player.sendSystemMessage(MSG);
        context.getSource().sendSuccess(() -> MSG, false);
      } else {
        Component MSG = Component.translatable("message.noellesroles.vt_mode.disabled", player.getDisplayName())
            .withStyle(ChatFormatting.GREEN);
        player.sendSystemMessage(MSG);
        context.getSource().sendSuccess(() -> MSG, false);
      }

      return Command.SINGLE_SUCCESS;
    } catch (Exception e) {
      return 0;
    }
  }
}