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
import java.util.stream.Collectors;

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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
        .then(Commands.literal("pause")
            .executes(ScheduleCommand::pauseAllTasks)
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(ScheduleCommand::pauseTask)))
        .then(Commands.literal("resume")
            .executes(ScheduleCommand::resumeAllTasks)
            .then(Commands.argument("id", StringArgumentType.word())
                .executes(ScheduleCommand::resumeTask)))
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
          ctx.getSource().sendFailure(
              Component.translatable("commands.sre.schedule.invalid_day", day));
          return 0;
        }
        task.days.add(day);
      } catch (NumberFormatException e) {
        ctx.getSource().sendFailure(
            Component.translatable("commands.sre.schedule.invalid_days_format"));
        return 0;
      }
    }
    if (task.days.isEmpty()) {
      ctx.getSource().sendFailure(Component.translatable("commands.sre.schedule.need_days"));
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
      ctx.getSource().sendFailure(Component.translatable("commands.sre.schedule.add_failed"));
      return 0;
    }
    ctx.getSource().sendSuccess(
        () -> Component.translatable("commands.sre.schedule.added", task.id), true);
    return 1;
  }

  private static int removeTask(CommandContext<CommandSourceStack> ctx) {
    String id = StringArgumentType.getString(ctx, "id");
    if (!ScheduleManager.removeTask(id)) {
      ctx.getSource().sendFailure(Component.translatable("commands.sre.schedule.not_found", id));
      return 0;
    }
    ctx.getSource().sendSuccess(
        () -> Component.translatable("commands.sre.schedule.removed", id), true);
    return 1;
  }

  private static int pauseTask(CommandContext<CommandSourceStack> ctx) {
    String id = StringArgumentType.getString(ctx, "id");
    if (!ScheduleManager.pauseTask(id)) {
      ctx.getSource().sendFailure(Component.translatable("commands.sre.schedule.not_found", id));
      return 0;
    }
    ctx.getSource().sendSuccess(
        () -> Component.translatable("commands.sre.schedule.paused", id), true);
    return 1;
  }

  private static int pauseAllTasks(CommandContext<CommandSourceStack> ctx) {
    int count = ScheduleManager.pauseAll();
    ctx.getSource().sendSuccess(
        () -> Component.translatable("commands.sre.schedule.paused_all", count), true);
    return 1;
  }

  private static int resumeTask(CommandContext<CommandSourceStack> ctx) {
    String id = StringArgumentType.getString(ctx, "id");
    if (!ScheduleManager.resumeTask(id)) {
      ctx.getSource().sendFailure(Component.translatable("commands.sre.schedule.not_found", id));
      return 0;
    }
    ctx.getSource().sendSuccess(
        () -> Component.translatable("commands.sre.schedule.resumed", id), true);
    return 1;
  }

  private static int resumeAllTasks(CommandContext<CommandSourceStack> ctx) {
    int count = ScheduleManager.resumeAll();
    ctx.getSource().sendSuccess(
        () -> Component.translatable("commands.sre.schedule.resumed_all", count), true);
    return 1;
  }

  private static int listTasks(CommandContext<CommandSourceStack> ctx) {
    List<ScheduleTask> tasks = ScheduleManager.getTasks();
    if (tasks.isEmpty()) {
      ctx.getSource().sendSuccess(() -> Component.translatable("commands.sre.schedule.list.empty"), false);
      return 1;
    }
    ctx.getSource().sendSuccess(
        () -> Component.translatable("commands.sre.schedule.list.header", tasks.size()), false);
    for (ScheduleTask task : tasks) {
      ctx.getSource().sendSuccess(() -> describe(task), false);
    }
    return 1;
  }

  private static int reloadTasks(CommandContext<CommandSourceStack> ctx) {
    if (!ScheduleManager.reload()) {
      ctx.getSource().sendFailure(Component.translatable("commands.sre.schedule.reload_failed"));
      return 0;
    }
    ctx.getSource().sendSuccess(() -> Component.translatable("commands.sre.schedule.reloaded"), true);
    return 1;
  }

  private static int clearTasks(CommandContext<CommandSourceStack> ctx) {
    ScheduleManager.clearTasks();
    ctx.getSource().sendSuccess(() -> Component.translatable("commands.sre.schedule.cleared"), true);
    return 1;
  }

  private static MutableComponent describe(ScheduleTask task) {
    MutableComponent line = Component.translatable("commands.sre.schedule.list.entry",
        task.id,
        Component.translatable("commands.sre.schedule.type." + task.type.name().toLowerCase()),
        task.function);
    line.append(Component.literal(" ")).append(describeDetail(task));
    if (task.paused) {
      line.append(Component.translatable("commands.sre.schedule.list.paused_suffix")
          .withStyle(ChatFormatting.RED));
    }
    return line;
  }

  private static String intArrToStr(List<Integer> days) {

    if (days == null || days.isEmpty()) {
      return ""; // 或返回 "[]" 根据需求
    }
    return days.stream()
        .map(String::valueOf)
        .collect(Collectors.joining(", "));
  }

  private static MutableComponent describeDetail(ScheduleTask task) {
    return switch (task.type) {
      case REALTIME_DAILY -> Component.translatable("commands.sre.schedule.desc.daily",
          String.format("%02d", task.hour), String.format("%02d", task.minute));
      case REALTIME_WEEKLY -> Component.translatable("commands.sre.schedule.desc.weekly",
          String.format("%02d", task.hour), String.format("%02d", task.minute), intArrToStr(task.days));
      case REALTIME_ONCE -> Component.translatable("commands.sre.schedule.desc.once", task.datetime);
      case REALTIME_INTERVAL -> Component.translatable("commands.sre.schedule.desc.interval", task.intervalSeconds);
      case GAMETIME_INTERVAL -> Component.translatable("commands.sre.schedule.desc.gametime_interval",
          task.intervalTicks);
      case SERVER_START -> Component.translatable("commands.sre.schedule.desc.server_start");
      case SERVER_STOP -> Component.translatable("commands.sre.schedule.desc.server_stop");
    };
  }
}
