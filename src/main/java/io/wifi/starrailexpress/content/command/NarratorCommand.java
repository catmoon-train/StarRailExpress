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
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.network.packet.CustomNarratorPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

public class NarratorCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
    dispatcher.register(
        Commands.literal("sre:narrator")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("player", EntityArgument.players()).then(
                Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                    .executes(NarratorCommand::sendNarrator)
                    .then(Commands.argument("should_interrupt", BoolArgumentType.bool())
                        .executes(NarratorCommand::sendNarrator_bool)))));
  }

  private static int sendNarrator_bool(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    Component content = ComponentArgument.getComponent(ctx, "message");
    var players = EntityArgument.getPlayers(ctx, "player");
    boolean shouldInterrupt = BoolArgumentType.getBool(ctx, "should_interrupt");
    for (var p : players) {
      sendNarratorToPlayer(p, ComponentUtils.updateForEntity(
          ctx.getSource(),
          content,
          p, 0), shouldInterrupt);
    }
    ctx.getSource().sendSuccess(() -> Component.translatable("Send custom narrator to players. Content: %s", content),
        true);
    return 1;
  }

  private static int sendNarrator(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    Component content = ComponentArgument.getComponent(ctx, "message");
    var players = EntityArgument.getPlayers(ctx, "player");
    for (var p : players) {
      sendNarratorToPlayer(p, ComponentUtils.updateForEntity(
          ctx.getSource(),
          content,
          p, 0));
    }
    ctx.getSource().sendSuccess(() -> Component.translatable("Send custom narrator to players. Content: %s", content),
        true);
    return 1;
  }

  public static void sendNarratorToPlayer(ServerPlayer player, Component content) {
    sendNarratorToPlayer(player, content, false);
  }

  public static void sendNarratorToPlayer(ServerPlayer player, Component content, boolean shouldInterrupt) {
    ServerPlayNetworking.send(player, new CustomNarratorPacket(content, shouldInterrupt));
  }
}
