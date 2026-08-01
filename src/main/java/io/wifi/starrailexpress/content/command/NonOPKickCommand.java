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

package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.wifi.starrailexpress.SREConfig;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class NonOPKickCommand {
  private static final SimpleCommandExceptionType ERROR_KICKING_OWNER = new SimpleCommandExceptionType(
      Component.translatable("commands.kick.owner.failed"));
  private static final SimpleCommandExceptionType ERROR_SINGLEPLAYER = new SimpleCommandExceptionType(
      Component.translatable("commands.kick.singleplayer.failed"));

  public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher,
      CommandBuildContext commandBuildContext) {
    commandDispatcher.register((Commands.literal("sre:kick")
        .requires(
            commandSourceStack -> commandSourceStack.hasPermission(SREConfig.instance().gameKickRequiredPermission)))
        .then((Commands.argument("targets", EntityArgument.players())
            .executes((commandContext) -> kickPlayers(commandContext.getSource(),
                EntityArgument.getPlayers(commandContext, "targets"),
                Component.translatable("multiplayer.disconnect.kicked"))))
            .then(Commands.argument("reason", ComponentArgument.textComponent(commandBuildContext))
                .executes((commandContext) -> kickPlayers(commandContext.getSource(),
                    EntityArgument.getPlayers(commandContext, "targets"),
                    ComponentArgument.getComponent(commandContext, "reason"))))));
  }

  private static int kickPlayers(CommandSourceStack ctx, Collection<ServerPlayer> collection,
      Component component) throws CommandSyntaxException {
    if (!ctx.getServer().isPublished()) {
      throw ERROR_SINGLEPLAYER.create();
    } else {
      int playerPermission = 2;
      final var server = ctx.getServer();
      if (ctx.isPlayer()) {

        var runner = ctx.getPlayer();
        if (runner != null) {
          playerPermission = server.getProfilePermissions(runner.getGameProfile());
        }
      } else {
        playerPermission = 4;
      }
      int i = 0;
      for (ServerPlayer serverPlayer : collection) {
        if (serverPlayer.hasPermissions(playerPermission))
          continue;
        if (!ctx.getServer().isSingleplayerOwner(serverPlayer.getGameProfile())) {
          serverPlayer.connection.disconnect(component);
          ctx.sendSuccess(() -> Component.translatable("commands.kick.success",
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
