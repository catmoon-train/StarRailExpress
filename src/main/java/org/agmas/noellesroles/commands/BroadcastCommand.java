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

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;

public class BroadcastCommand {
  @SuppressWarnings("rawtypes")
  public static void register() {
    CommandRegistrationCallback.EVENT.register((commandDispatcher, registryAccess, environment) -> {
      commandDispatcher.register((Commands.literal("broadcast")
          .requires((commandSourceStack) -> {
            return commandSourceStack.hasPermission(2);
          })).then(Commands.argument("targets", EntityArgument.players())
              .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                  .executes((commandContext) -> {
                    int i = 0;

                    for (Iterator var2 = EntityArgument.getPlayers(commandContext, "targets")
                        .iterator(); var2.hasNext(); ++i) {
                      ServerPlayer serverPlayer = (ServerPlayer) var2.next();
                      BroadcastMessage(serverPlayer, ComponentUtils.updateForEntity(
                          (CommandSourceStack) commandContext.getSource(),
                          ComponentArgument.getComponent(commandContext, "message"),
                          serverPlayer, 0));
                    }
                    return i;
                  }))));
    });
  }

  public static void BroadcastMessage(ServerPlayer serverPlayer, Component message) {
    org.agmas.noellesroles.packet.BroadcastMessageS2CPacket packet = new org.agmas.noellesroles.packet.BroadcastMessageS2CPacket(
        message);
    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
        .send(serverPlayer, packet);
  }
}