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

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.schedule.ScheduleManager;
import io.wifi.starrailexpress.schedule.ScheduleTask;
import io.wifi.starrailexpress.schedule.ScheduleType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerFunctionManager;

/**
 * 定时任务指令 /sre:schedule:
 * <ul>
 * <li>{@code /sre:schedule add <id> <function> <type> ...} —— 新增任务。</li>
 * <li>{@code /sre:schedule remove <id>} —— 删除任务。</li>
 * <li>{@code /sre:schedule list} —— 列出全部任务。</li>
 * <li>{@code /sre:schedule reload} —— 从本地文件重载。</li>
 * <li>{@code /sre:schedule clear} —— 清空全部任务。</li>
 * </ul>
 */
public final class ScheduleCommand {
  private ScheduleCommand() {
  }

  public static final SuggestionProvider<CommandSourceStack> SUGGEST_FUNCTION = (commandContext,
      suggestionsBuilder) -> {
    ServerFunctionManager functionManager = commandContext.getSource().getServer().getFunctions();
    SharedSuggestionProvider.suggestResource(functionManager.getTagNames(), suggestionsBuilder, "#");
    return SharedSuggestionProvider.suggestResource(functionManager.getFunctionNames(), suggestionsBuilder);
  };

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("sre:schedule")
        .requires(source -> source.hasPermission(SREConfig.instance().scheduleCommandPermission))
        .then(addNode())
        .then(Commands.literal("remove")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(ScheduleCommand::removeTask)))
        .then(Commands.literal("list")
            .executes(ScheduleCommand::listTasks))
        .then(Commands.literal("reload")
            .executes(ScheduleCommand::reloadTasks))
        .then(Commands.literal("clear")
            .executes(ScheduleCommand::clearTasks)));
  }

  private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> addNode() {
    var daily = Commands.literal("realtime_daily")
        .then(Commands.argument("hour", IntegerArgumentType.integer(0, 23))
            .then(Commands.argument("minute", IntegerArgumentType.integer(0, 59))
                .executes(ctx -> addTask(ctx, ScheduleType.REALTIME_DAILY))));
    var weekly = Commands.literal("realtime_weekly")
        .then(Commands.argument("days", StringArgumentType.string())
            .then(Commands.argument("hour", IntegerArgumentType.integer(0, 23))
                .then(Commands.argument("minute", IntegerArgumentType.integer(0, 59))
                    .executes(ctx -> addWeeklyTask(ctx)))));
    var once = Commands.literal("realtime_once")
        .then(Commands.argument("datetime", StringArgumentType.greedyString())
            .executes(ctx -> addTask(ctx, ScheduleType.REALTIME_ONCE)));
    var realtimeInterval = Commands.literal("realtime_interval")
        .then(Commands.argument("seconds", LongArgumentType.longArg(1))
            .executes(ctx -> addIntervalTask(ctx, ScheduleType.REALTIME_INTERVAL)));
    var gameTimeInterval = Commands.literal("gametime_interval")
        .then(Commands.argument("ticks", LongArgumentType.longArg(1))
            .executes(ctx -> addIntervalTask(ctx, ScheduleType.GAMETIME_INTERVAL)));
    var serverStart = Commands.literal("server_start")
        .executes(ctx -> addTask(ctx, ScheduleType.SERVER_START));
    var serverStop = Commands.literal("server_stop")
        .executes(ctx -> addTask(ctx, ScheduleType.SERVER_STOP));
    return Commands.literal("add")
        .then(Commands.argument("id", StringArgumentType.word())
            .then(Commands.argument("function", FunctionArgument.functions())
                .suggests(SUGGEST_FUNCTION)
                .then(daily)
                .then(weekly)
                .then(once)
                .then(realtimeInterval)
                .then(gameTimeInterval)
                .then(serverStart)
                .then(serverStop)));
  }

  private static int addTask(CommandContext<CommandSourceStack> ctx, ScheduleType type)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    ScheduleTask task = new ScheduleTask();
    task.id = StringArgumentType.getString(ctx, "id");
    task.type = type;
    task.function = FunctionArgument.getFunctionOrTag(ctx, "function").getFirst().toString();
    if (type == ScheduleType.REALTIME_DAILY) {
      task.hour = IntegerArgumentType.getInteger(ctx, "hour");
      task.minute = IntegerArgumentType.getInteger(ctx, "minute");
    } else if (type == ScheduleType.REALTIME_ONCE) {
      task.datetime = StringArgumentType.getString(ctx, "datetime");
    }
    return finishAdd(ctx, task);
  }

  private static int addWeeklyTask(CommandContext<CommandSourceStack> ctx)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    ScheduleTask task = new ScheduleTask();
    task.id = StringArgumentType.getString(ctx, "id");
    task.type = ScheduleType.REALTIME_WEEKLY;
    task.function = FunctionArgument.getFunctionOrTag(ctx, "function").getFirst().toString();
    task.hour = IntegerArgumentType.getInteger(ctx, "hour");
    task.minute = IntegerArgumentType.getInteger(ctx, "minute");
    // days 为逗号分隔的 1-7(1=周一..7=周日),如 "1,3"
    for (String part : StringArgumentType.getString(ctx, "days").split(",")) {
      try {
        int day = Integer.parseInt(part.trim());
        if (day < 1 || day > 7) {
          ctx.getSource().sendFailure(Component.literal("Invalid day: " + day + ", must be 1-7 (1=Monday..7=Sunday)"));
          return 0;
        }
        task.days.add(day);
      } catch (NumberFormatException e) {
        ctx.getSource().sendFailure(Component.literal("Invalid days format, expected comma separated 1-7, e.g. \"1,3\""));
        return 0;
      }
    }
    if (task.days.isEmpty()) {
      ctx.getSource().sendFailure(Component.literal("At least one day is required, e.g. \"1,3\""));
      return 0;
    }
    return finishAdd(ctx, task);
  }

  private static int addIntervalTask(CommandContext<CommandSourceStack> ctx, ScheduleType type)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    ScheduleTask task = new ScheduleTask();
    task.id = StringArgumentType.getString(ctx, "id");
    task.type = type;
    task.function = FunctionArgument.getFunctionOrTag(ctx, "function").getFirst().toString();
    if (type == ScheduleType.REALTIME_INTERVAL) {
      task.intervalSeconds = LongArgumentType.getLong(ctx, "seconds");
    } else {
      task.intervalTicks = LongArgumentType.getLong(ctx, "ticks");
    }
    return finishAdd(ctx, task);
  }

  private static int finishAdd(CommandContext<CommandSourceStack> ctx, ScheduleTask task) {
    if (!ScheduleManager.addTask(task)) {
      ctx.getSource().sendFailure(Component.literal("Failed to add schedule: duplicate id or invalid parameters."));
      return 0;
    }
    ctx.getSource().sendSuccess(() -> Component.literal("Schedule [" + task.id + "] added."), true);
    return 1;
  }

  private static int removeTask(CommandContext<CommandSourceStack> ctx) {
    String id = StringArgumentType.getString(ctx, "id");
    if (!ScheduleManager.removeTask(id)) {
      ctx.getSource().sendFailure(Component.literal("Schedule [" + id + "] not found."));
      return 0;
    }
    ctx.getSource().sendSuccess(() -> Component.literal("Schedule [" + id + "] removed."), true);
    return 1;
  }

  private static int listTasks(CommandContext<CommandSourceStack> ctx) {
    List<ScheduleTask> tasks = ScheduleManager.getTasks();
    if (tasks.isEmpty()) {
      ctx.getSource().sendSuccess(() -> Component.literal("No schedules."), false);
      return 1;
    }
    for (ScheduleTask task : tasks) {
      ctx.getSource().sendSuccess(() -> Component.literal(describe(task)), false);
    }
    return 1;
  }

  private static int reloadTasks(CommandContext<CommandSourceStack> ctx) {
    if (!ScheduleManager.reload()) {
      ctx.getSource().sendFailure(Component.literal("Failed to reload schedules from local file!"));
      return 0;
    }
    ctx.getSource().sendSuccess(() -> Component.literal("Success reload schedules from local file!"), true);
    return 1;
  }

  private static int clearTasks(CommandContext<CommandSourceStack> ctx) {
    ScheduleManager.clearTasks();
    ctx.getSource().sendSuccess(() -> Component.literal("All schedules cleared."), true);
    return 1;
  }

  private static String describe(ScheduleTask task) {
    StringBuilder sb = new StringBuilder();
    sb.append('[').append(task.id).append("] ").append(task.type).append(" -> ").append(task.function);
    switch (task.type) {
      case REALTIME_DAILY -> sb.append(" at ").append(String.format("%02d:%02d", task.hour, task.minute));
      case REALTIME_WEEKLY -> sb.append(" at ").append(String.format("%02d:%02d", task.hour, task.minute))
          .append(" days=").append(task.days);
      case REALTIME_ONCE -> sb.append(" at ").append(task.datetime);
      case REALTIME_INTERVAL -> sb.append(" every ").append(task.intervalSeconds).append("s");
      case GAMETIME_INTERVAL -> sb.append(" every ").append(task.intervalTicks).append(" ticks");
      case SERVER_START -> sb.append(" on server start");
      case SERVER_STOP -> sb.append(" on server stop");
    }
    return sb.toString();
  }
}
