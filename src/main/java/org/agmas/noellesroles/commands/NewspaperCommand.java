package org.agmas.noellesroles.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.Optional;

import io.wifi.starrailexpress.util.SRENetworkMessageUtils;

public class NewspaperCommand {
  @SuppressWarnings("rawtypes")
  public static void register() {
    CommandRegistrationCallback.EVENT.register((commandDispatcher, registryAccess, environment) -> {
      commandDispatcher.register((Commands.literal("newspaper")
          .requires((commandSourceStack) -> {
            return commandSourceStack.hasPermission(2);
          })).then(Commands.argument("targets", EntityArgument.players())
              .then(Commands.argument("title", ComponentArgument.textComponent(registryAccess))
                  .then(Commands.argument("author", ComponentArgument.textComponent(registryAccess))
                      .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                          .executes((commandContext) -> {
                            int i = 0;
                            for (Iterator var2 = EntityArgument.getPlayers(commandContext, "targets")
                                .iterator(); var2.hasNext(); ++i) {
                              ServerPlayer serverPlayer = (ServerPlayer) var2.next();
                              Component message = ComponentUtils.updateForEntity(
                                  commandContext.getSource(),
                                  ComponentArgument.getComponent(commandContext, "message"), serverPlayer, 0);

                              Component title = ComponentUtils.updateForEntity(
                                  commandContext.getSource(),
                                  ComponentArgument.getComponent(commandContext, "title"), serverPlayer, 0);

                              Component author = ComponentUtils.updateForEntity(
                                  commandContext.getSource(),
                                  ComponentArgument.getComponent(commandContext, "author"), serverPlayer, 0);
                              SRENetworkMessageUtils.sendNewspaper(serverPlayer, message, Optional.of(title),
                                  Optional.of(author));
                            }
                            return i;
                          }))))));
    });
  }
}