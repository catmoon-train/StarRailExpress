package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class NonOPKickCommand {
  private static final SimpleCommandExceptionType ERROR_KICKING_OWNER = new SimpleCommandExceptionType(
      Component.translatable("commands.kick.owner.failed"));
  private static final SimpleCommandExceptionType ERROR_SINGLEPLAYER = new SimpleCommandExceptionType(
      Component.translatable("commands.kick.singleplayer.failed"));

  public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher,
      CommandBuildContext commandBuildContext) {
    commandDispatcher.register((Commands.literal("sre:kick")
        .requires(commandSourceStack -> commandSourceStack.hasPermission(2)))
        .then((Commands.argument("targets", EntityArgument.players())
            .executes((commandContext) -> kickPlayers(commandContext.getSource(),
                EntityArgument.getPlayers(commandContext, "targets"),
                Component.translatable("multiplayer.disconnect.kicked"))))
            .then(Commands.argument("reason", ComponentArgument.textComponent(commandBuildContext))
                .executes((commandContext) -> kickPlayers(commandContext.getSource(),
                    EntityArgument.getPlayers(commandContext, "targets"),
                    ComponentArgument.getComponent(commandContext, "reason"))))));
  }

  private static int kickPlayers(CommandSourceStack commandSourceStack, Collection<ServerPlayer> collection,
      Component component) throws CommandSyntaxException {
    if (!commandSourceStack.getServer().isPublished()) {
      throw ERROR_SINGLEPLAYER.create();
    } else {
      int i = 0;

      for (ServerPlayer serverPlayer : collection) {
        if (serverPlayer.hasPermissions(2))
          continue;
        if (!commandSourceStack.getServer().isSingleplayerOwner(serverPlayer.getGameProfile())) {
          serverPlayer.connection.disconnect(component);
          commandSourceStack.sendSuccess(() -> Component.translatable("commands.kick.success",
              new Object[] { serverPlayer.getName(), component }), true);
          ++i;
        }
      }

      if (i == 0) {
        throw ERROR_KICKING_OWNER.create();
      } else {
        return i;
      }
    }
  }
}
