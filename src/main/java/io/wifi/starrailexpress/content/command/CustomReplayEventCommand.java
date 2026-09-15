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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.SRE;
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
            .then(Commands.literal("record")
                .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                    .executes(ctx -> execute(ctx, false))))
            .then(Commands.literal("record_hidden")
                .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                    .executes(ctx -> execute(ctx, true))))
            // easy：纯文本 + 玩家名参数，按顺序填入文本里的 <player>
            .then(Commands.literal("easy")
                .then(Commands.argument("text", StringArgumentType.string())
                    .executes(ctx -> executeEasy(ctx, ""))
                    .then(Commands.argument("names", StringArgumentType.greedyString())
                        .executes(ctx -> executeEasy(ctx, StringArgumentType.getString(ctx, "names")))))));
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

  private static int execute(CommandContext<CommandSourceStack> ctx, boolean hidden) {
    ServerPlayer serverPlayer = ctx.getSource().getPlayer();
    Component res = ComponentArgument.getComponent(ctx, "message");
    if (serverPlayer != null) {
      try {
        res = ComponentUtils.updateForEntity(
            (CommandSourceStack) ctx.getSource(),
            res,
            serverPlayer, 0);
      } catch (CommandSyntaxException e) {
        e.printStackTrace();
        ctx.getSource().sendFailure(Component.literal("ERROR: " + e.getMessage()));
        return 0;
      }
    } else {
    }
    Component result = SRE.REPLAY_MANAGER.recordCustomEvent(res, hidden);
    ctx.getSource().sendSuccess(() -> Component.literal("Successfully record custom event!"), true);
    ctx.getSource().sendSystemMessage(Component.literal("[ADD REPLAY] ").append(result));
    return 1;
  }

  /** 「easy」分支填名字用的占位符。 */
  private static final String PLAYER_PLACEHOLDER = "<player>";

  /**
   * 记录一条自定义回放文本（简易写法：纯文本 + 玩家名，按顺序填入 {@code <player>}）。
   *
   * <p>
   * 例：{@code /sre:custom_replay easy "<player>受到了<player>的攻击" Alex1 Alex2}
   * → 回放中新增词条「Alex1受到了Alex2的攻击」。
   *
   * <p>
   * 名字按顺序消耗，多出来的名字会被忽略；名字不够时，剩下的 {@code <player>} 原样保留。
   * 玩家名不做校验（按输入的文字原样填入）。
   */
  private static int executeEasy(CommandContext<CommandSourceStack> ctx, String names) {
    String text = StringArgumentType.getString(ctx, "text");
    String[] tokens = names == null || names.isBlank() ? new String[0] : names.trim().split("\\s+");
    StringBuilder builder = new StringBuilder();
    int tokenIndex = 0;
    int cursor = 0;
    int at;
    while ((at = text.indexOf(PLAYER_PLACEHOLDER, cursor)) >= 0) {
      builder.append(text, cursor, at);
      builder.append(tokenIndex < tokens.length ? tokens[tokenIndex] : PLAYER_PLACEHOLDER);
      tokenIndex++;
      cursor = at + PLAYER_PLACEHOLDER.length();
    }
    builder.append(text, cursor, text.length());
    Component message = Component.literal(builder.toString());
    SRE.REPLAY_MANAGER.recordCustomEvent(message);
    ctx.getSource().sendSuccess(() -> Component.literal("[ADD REPLAY] ").append(message), true);
    return 1;
  }
}
