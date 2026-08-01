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

import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.Optional;

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