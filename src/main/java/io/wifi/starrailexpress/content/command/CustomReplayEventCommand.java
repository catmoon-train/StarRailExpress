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
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

public class CustomReplayEventCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
    dispatcher.register(
        Commands.literal("sre:custom_replay")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                .executes(ctx -> execute(ctx, false, true))
                // hidden：该词条是否在回放中隐藏
                .then(Commands.argument("hidden", BoolArgumentType.bool())
                    .executes(ctx -> execute(ctx, BoolArgumentType.getBool(ctx, "hidden"), true))
                    // resolver：文本里的选择器是否用回放显示文本解析
                    .then(Commands.argument("resolver", BoolArgumentType.bool())
                        .executes(ctx -> execute(ctx,
                            BoolArgumentType.getBool(ctx, "hidden"),
                            BoolArgumentType.getBool(ctx, "resolver")))))));
    dispatcher.register(
        Commands.literal("sre:show_replay")
            .requires(source -> source.hasPermission(2))
            .executes(CustomReplayEventCommand::executeShow));
  }

  private static int executeShow(CommandContext<CommandSourceStack> ctx) {
    ServerPlayer serverPlayer = ctx.getSource().getPlayer();
    var replay = SRE.REPLAY_MANAGER.generateReplay();
    serverPlayer.sendSystemMessage(replay);
    ctx.getSource().sendSuccess(() -> Component.translatable("Showing replay to %s.", serverPlayer.getName()), true);
    return 1;
  }

  private static int execute(CommandContext<CommandSourceStack> ctx, boolean hidden, boolean resolver) {
    CommandSourceStack source = ctx.getSource();
    Component res = ComponentArgument.getComponent(ctx, "message");
    try {
      if (resolver) {
        res = GameReplayManager.resolveReplaySelectors(source, res);
      }
      res = ComponentUtils.updateForEntity(source, res, source.getEntity(), 0);
    } catch (CommandSyntaxException e) {
      e.printStackTrace();
      source.sendFailure(Component.literal("ERROR: " + e.getMessage()));
      return 0;
    }
    Component result = SRE.REPLAY_MANAGER.recordCustomEvent(res, hidden);
    source.sendSuccess(() -> Component.literal("Successfully record custom event!"), true);
    source.sendSystemMessage(Component.literal("[ADD REPLAY] ").append(result));
    return 1;
  }
}
