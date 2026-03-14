package org.agmas.noellesroles.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses.ServerTaskInfo;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.MapResetManager;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import pro.fazeclan.river.stupid_express.StupidExpress;

import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.ProblemScreenOpenC2SPacket;
import org.agmas.noellesroles.packet.ScanAllTaskPointsPayload;
import org.agmas.noellesroles.utils.MapScannerManager;
import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

public class GameFunctionsCommand {
  public static void register() {
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("tmm:game").requires(source -> source.hasPermission(2))
              .then(Commands.literal("gametime").executes((context) -> {
                var source = context.getSource();
                try {
                  var gameTimeComponent = SREGameTimeComponent.KEY.get(source.getLevel());
                  int leftTime = gameTimeComponent.getTime();
                  float leftTimeSeconds = leftTime / 20;
                  float leftTimeMinutes = leftTimeSeconds / 60;
                  float leftTimeSeconds2 = leftTimeSeconds % 60;
                  source.sendSuccess(() -> Component.translatable("Left Time: %s",
                      String.format("%02.0f:%02.0f", leftTimeMinutes, leftTimeSeconds2))
                      .withStyle(ChatFormatting.GREEN),
                      false);
                } catch (Exception e) {
                  e.printStackTrace();
                  source.sendFailure(Component.literal("Error: ").append(e.getMessage()).withStyle(ChatFormatting.RED));
                }
                return 1;
              }))
              .then(Commands.literal("tests")
                  .then(Commands.literal("math").executes((context) -> {
                    ServerPlayNetworking.send(context.getSource().getPlayerOrException(),
                        new ProblemScreenOpenC2SPacket(false, 3));
                    return 1;
                  }).then(Commands.literal("forced").executes((context) -> {
                    ServerPlayNetworking.send(context.getSource().getPlayerOrException(),
                        new ProblemScreenOpenC2SPacket(true, 3));
                    return 1;
                  }))))
              .then(Commands.literal("tasks")
                  .then(Commands.literal("list").executes((context) -> {
                    var source = context.getSource();
                    source.sendSystemMessage(
                        Component.literal("Sync Task Queue:\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    int idx = 0;
                    for (ServerTaskInfo inf : GameUtils.serverTaskQueue) {
                      source.sendSystemMessage(Component.translatable("[%s] %s", idx, inf.getClass().getSimpleName())
                          .withStyle(ChatFormatting.AQUA));
                      idx++;
                    }
                    source.sendSystemMessage(
                        Component.literal("Asyn Task List:\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    idx = 0;
                    for (ServerTaskInfo inf : GameUtils.serverAsynTaskLists) {
                      source.sendSystemMessage(Component.translatable("[%s] %s", idx, inf.getClass().getSimpleName())
                          .withStyle(ChatFormatting.AQUA));
                      idx++;
                    }
                    source.sendSuccess(() -> {
                      return Component.translatable("Sync Task Queue size: %s\nAsyn Task List size: %s",
                          GameUtils.serverTaskQueue.size(),
                          GameUtils.serverAsynTaskLists.size()).withStyle(ChatFormatting.GOLD);
                    }, false);
                    // GameUtils.serverTaskQueue;
                    // GameUtils.;
                    return 1;
                  }))
                  .then(Commands.literal("clear")
                      .then(Commands.literal("task_queue").executes((context) -> {
                        var source = context.getSource();
                        GameUtils.serverTaskQueue.clear();
                        source.sendSuccess(() -> {
                          return Component.literal("Cleared all task queues!");
                        }, true);
                        return 1;
                      }))
                      .then(Commands.literal("task_list").executes((context) -> {
                        var source = context.getSource();
                        GameUtils.serverAsynTaskLists.clear();
                        source.sendSuccess(() -> {
                          return Component.literal("Cleared all asyn tasks list!");
                        }, true);
                        return 1;
                      })))
                  .then(Commands.literal("cancel")
                      .then(Commands.literal("task_queue")
                          .then(Commands.argument("tid", IntegerArgumentType.integer(0)).executes((context) -> {
                            var source = context.getSource();

                            int tid = IntegerArgumentType.getInteger(context, "tid");
                            if (tid >= 0 && tid < GameUtils.serverTaskQueue.size()) {
                              var task = GameUtils.serverTaskQueue.get(tid);
                              task.cancelled = true;
                              source.sendSuccess(() -> Component
                                  .translatable("Cancelled task %s (tid: %s)!", task.getClass().getSimpleName(), tid)
                                  .withStyle(ChatFormatting.GREEN), true);
                            } else {
                              source.sendFailure(Component.literal("Invaild tid!").withStyle(ChatFormatting.RED));
                              return 0;
                            }
                            return 1;
                          }))
                          .then(Commands.literal("all").executes((context) -> {
                            var source = context.getSource();
                            GameUtils.serverTaskQueue.forEach((t) -> {
                              t.cancelled = true;
                            });
                            source.sendSuccess(() -> {
                              return Component.literal("Cleared all task queues!");
                            }, true);
                            return 1;
                          })))
                      .then(Commands.literal("task_list")
                          .then(Commands.argument("tid", IntegerArgumentType.integer(0)).executes((context) -> {
                            var source = context.getSource();

                            int tid = IntegerArgumentType.getInteger(context, "tid");
                            if (tid >= 0 && tid < GameUtils.serverAsynTaskLists.size()) {
                              var task = GameUtils.serverAsynTaskLists.get(tid);
                              task.cancelled = true;
                              source.sendSuccess(() -> Component
                                  .translatable("Cancelled task %s (tid: %s)!", task.getClass().getSimpleName(), tid)
                                  .withStyle(ChatFormatting.GREEN), true);
                            } else {
                              source.sendFailure(Component.literal("Invaild tid!").withStyle(ChatFormatting.RED));
                              return 0;
                            }
                            return 1;
                          }))
                          .then(Commands.literal("all").executes((context) -> {
                            var source = context.getSource();
                            GameUtils.serverAsynTaskLists.forEach((t) -> {
                              t.cancelled = true;
                            });
                            source.sendSuccess(() -> {
                              return Component.literal("Cleared all asyn tasks list!");
                            }, true);
                            return 1;
                          })))))
              .then(Commands.literal("win")
                  .then(Commands.argument("id", StringArgumentType.string())
                      .suggests(WinStatusSuggestions::suggestWinStatus)
                      .executes(GameFunctionsCommand::executeWinWithOnlyId))
                  .then(Commands.literal("CUSTOM")
                      .then(Commands.argument("color", ModColorArgument.color())
                          .then(
                              Commands.argument("id", StringArgumentType.string())
                                  .executes(GameFunctionsCommand::executeCustomWinWithOnlyId))))
                  .then(Commands.literal("CUSTOM_COMPONENT")
                      .then(Commands.argument("color", ModColorArgument.color())
                          .then(Commands.argument("title", ComponentArgument.textComponent(registryAccess))
                              .then(Commands
                                  .argument(
                                      "subtitle", ComponentArgument.textComponent(registryAccess))
                                  .executes(GameFunctionsCommand::executeCustomWinWithIdAndTitle))))))
              .then(Commands.literal("reset")
                  .then(Commands.literal("sync")
                      .then(Commands.literal("copy").executes((context) -> {
                        GameUtils.tryAutoTrainReset(context.getSource().getLevel());
                        context.getSource().sendSuccess(() -> Component.literal("Normal Reset(copy)!"), true);
                        return 1;
                      }))
                      .then(Commands.literal("simple").executes((context) -> {
                        GameUtils.tryResetTrainOnlySomeBlock(context.getSource().getLevel());
                        context.getSource().sendSuccess(() -> Component.literal("Simple Reset (clean points only)!"),
                            true);

                        return 1;
                      })))
                  .then(Commands.literal("asyn")
                      .then(Commands.literal("copy").executes((context) -> {
                        var world = context.getSource().getLevel();
                        var areas = AreasWorldComponent.KEY.get(world);
                        ServerTaskInfoClasses.AutoTrainResetTask task = new ServerTaskInfoClasses.AutoTrainResetTask(
                            areas,
                            world, null, 0);
                        task.shouldStartGame = false;

                        GameUtils.serverTaskQueue.add(task);
                        context.getSource()
                            .sendSuccess(() -> Component.literal("Add server reset task: Normal Reset(copy)!"), true);
                        return 1;
                      }))
                      .then(Commands.literal("simple").executes((context) -> {

                        var world = context.getSource().getLevel();
                        MapResetManager.loadArea(world);
                        ServerTaskInfoClasses.OnlySomeBlockResetTask task = new ServerTaskInfoClasses.OnlySomeBlockResetTask(
                            GameUtils.resetPoints, world, null, 0);
                        task.shouldStartGame = false;
                        GameUtils.serverTaskQueue.add(task);
                        context.getSource().sendSuccess(
                            () -> Component.literal("Add server reset task: Simple Reset (clean points only)!"),
                            true);

                        return 1;
                      }))))
              .then(Commands.literal("scan").executes((context) -> {
                var source = context.getSource();
                var level = source.getLevel();
                var areas = AreasWorldComponent.KEY.get(level);
                if (areas.mapName == null) {
                  context.getSource()
                      .sendFailure(Component
                          .literal("You should load map first to scan points!\nUsage: /tmm:switchmap load <MapID>")
                          .withStyle(ChatFormatting.RED));
                  return 0;
                }
                MapResetManager.scanArea(level, areas);
                MapResetManager.saveArea(level);
                context.getSource().sendSuccess(
                    () -> Component.translatable("Scanned and saved reset points for map %s ! Total %s blocks!",
                        Component.nullToEmpty(areas.mapName), GameUtils.resetPoints.size()),
                    true);
                MapScannerManager.scanAndSaveScannerArea(level, areas);
                HashMap<Integer, Boolean> map = new HashMap<>();
                for (Entry<BlockPos, Integer> entry : GameUtils.taskBlocks.entrySet()) {
                  map.putIfAbsent(entry.getValue(), true);
                }
                context.getSource().sendSuccess(
                    () -> Component.translatable("Scanned Task points! Total %s types!", map.size()), true);

                for (var player : context.getSource().getLevel().players()) {
                  ServerPlayNetworking.send(player, new ScanAllTaskPointsPayload(GameUtils.taskBlocks));
                }
                return 1;
              })
                  .then(Commands.literal("reset_points").executes((context) -> {
                    var source = context.getSource();
                    var level = source.getLevel();
                    var areas = AreasWorldComponent.KEY.get(level);
                    if (areas.mapName == null) {
                      context.getSource()
                          .sendFailure(Component
                              .literal("You should load map first to scan points!\nUsage: /tmm:switchmap load <MapID>")
                              .withStyle(ChatFormatting.RED));
                      return 0;
                    }
                    MapResetManager.scanArea(level, areas);
                    MapResetManager.saveArea(level);
                    context.getSource().sendSuccess(
                        () -> Component.translatable("Scanned and saved reset points for map %s ! Total %s blocks!",
                            Component.nullToEmpty(areas.mapName), GameUtils.resetPoints.size()),
                        true);
                    return 1;
                  }))
                  .then(Commands.literal("task_points").executes((context) -> {
                    var level = context.getSource().getLevel();
                    var areas = AreasWorldComponent.KEY.get(level);
                    MapScannerManager.scanAndSaveScannerArea(level, areas);
                    HashMap<Integer, Boolean> map = new HashMap<>();
                    for (Entry<BlockPos, Integer> entry : GameUtils.taskBlocks.entrySet()) {
                      map.putIfAbsent(entry.getValue(), true);
                    }
                    context.getSource().sendSuccess(
                        () -> Component.translatable("Scanned Task points! Total %s types!", map.size()), true);

                    for (var player : context.getSource().getLevel().players()) {
                      ServerPlayNetworking.send(player, new ScanAllTaskPointsPayload(GameUtils.taskBlocks));
                    }
                    return 1;
                  })))
              .then(Commands.literal("blackout").executes((context) -> {
                return executeBlackout(context, -1);
              }).then(Commands.literal("stop").executes((context) -> {
                return executeBlackout(context, 0);
              })))
              .then(Commands.literal("psycho").executes((context) -> {
                return executePsycho(context, -1);
              }).then(Commands.literal("stop").executes((context) -> {
                return executePsycho(context, 0);
              })))
              .then(Commands.literal("kill")
                  .then(Commands.argument("victim", EntityArgument.player())
                      .then(Commands.argument("death_reason", ResourceLocationArgument.id())
                          .suggests(DeathReasonSuggestions::suggestDeathReasons)
                          .executes((context) -> {
                            ServerPlayer victim = EntityArgument.getPlayer(context, "victim");
                            ResourceLocation deathReason = ResourceLocationArgument.getId(context,
                                "death_reason");
                            return executeKillPlayer(context, victim, null, deathReason, true);
                          })
                          .then(Commands.argument("killer", EntityArgument.player()).executes((context) -> {
                            ServerPlayer victim = EntityArgument.getPlayer(context, "victim");
                            ServerPlayer killer = EntityArgument.getPlayer(context, "killer");
                            ResourceLocation deathReason = ResourceLocationArgument.getId(context,
                                "death_reason");
                            return executeKillPlayer(context, victim, killer, deathReason, true);
                          })
                              .then(Commands.argument("spawn_body", BoolArgumentType.bool()).executes((context) -> {
                                ServerPlayer victim = EntityArgument.getPlayer(context, "victim");
                                boolean spawnBody = BoolArgumentType.getBool(context, "spawn_body");
                                ServerPlayer killer = EntityArgument.getPlayer(context, "killer");
                                ResourceLocation deathReason = ResourceLocationArgument.getId(context,
                                    "death_reason");
                                return executeKillPlayer(context, victim, killer, deathReason, spawnBody);
                              }))))))
              .then(Commands.literal("timestop")
                  .then(Commands.argument("duration", IntegerArgumentType.integer(20, 1200))
                      .executes((context) -> {
                        int duration = IntegerArgumentType.getInteger(context, "duration");
                        return executeTimeStop(context, duration);
                      }))
                  .then(Commands.literal("stop")
                      .executes((context) -> {
                        return executeTimeStopStop(context);
                      }))));
        });
  }

  public static int executeKillPlayer(CommandContext<CommandSourceStack> context, ServerPlayer victim,
      @Nullable ServerPlayer killer, ResourceLocation deathReason, boolean spawnBody) {
    ResourceLocation deathReasonRL = deathReason;
    final String deathReasonT = deathReasonRL.toLanguageKey();
    GameUtils.killPlayer(victim, spawnBody, killer, deathReasonRL);
    context.getSource()
        .sendSuccess(() -> Component.translatable("Killed player %s by %s with reason %s (Spawn body: %s)",
            victim.getDisplayName(), (killer == null ? Component.literal("System") : killer.getDisplayName()),
            Component.translatable("death_reason." + deathReasonT), (spawnBody ? "Yes" : "No")), true);
    return 1;
  }

  private static int executePsycho(CommandContext<CommandSourceStack> context, int time) {
    var source = context.getSource();
    var player = source.getPlayer();
    if (player == null) {
      source.sendFailure(Component.literal("This command should be run by a player!").withStyle(ChatFormatting.RED));
      return 0;
    }
    var ppc = SREPlayerPsychoComponent.KEY.get(player);
    if(ppc.psychoTicks>0){
      source.sendFailure(Component.literal("The player is already in psycho mode!").withStyle(ChatFormatting.RED));
      return 0;
    }
    if (time != 0) {
      ppc.startPsycho();
      context.getSource()
          .sendSuccess(() -> Component.translatable("Triggered %s Psycho!", player.getScoreboardName()), true);
    } else {
      ppc.stopPsychoAndRefreshPsychoCount(true);
      context.getSource()
          .sendSuccess(() -> Component.translatable("Stopped %s Psycho!", player.getScoreboardName()), true);
    }
    return 1;
  }

  public static int executeBlackout(CommandContext<CommandSourceStack> context, int time) {
    var wbc = SREWorldBlackoutComponent.KEY.get(context.getSource().getLevel());
    if (time != 0) {
      wbc.triggerBlackout();
      context.getSource()
          .sendSuccess(() -> Component.translatable("Triggered Blackout!"), true);

    } else {
      context.getSource()
          .sendSuccess(() -> Component.translatable("Stopped All Blackouts!"), true);
      wbc.reset();

    }

    return 1;
  }

  public static int executeWinWithOnlyId(CommandContext<CommandSourceStack> context) {
    String id = StringArgumentType.getString(context, "id");
    WinStatus winStatus = null;
    for (WinStatus status : WinStatusSuggestions.allWinStatus) {
      if (status.toString().toLowerCase().equals(id.toLowerCase())) {
        winStatus = status;
      }
    }
    if (winStatus == null) {
      context.getSource().sendFailure(Component.literal("Unknown WinStatus ID!").withStyle(ChatFormatting.RED));
      return 0;
    }
    var roundComponent = SREGameRoundEndComponent.KEY.get(context.getSource().getLevel());
    roundComponent.setRoundEndData(context.getSource().getLevel().players(), winStatus);
    roundComponent.sync();
    context.getSource()
        .sendSuccess(() -> Component.translatable("Stop the game with WinStatus ID [%s]", id), true);
    GameUtils.stopGame(context.getSource().getLevel());
    return 1;
  }

  public static int executeCustomWinWithOnlyId(CommandContext<CommandSourceStack> context) {
    String id = StringArgumentType.getString(context, "id");
    int color = ModColorArgument.getColor(context, "color");
    var roundComponent = SREGameRoundEndComponent.KEY.get(context.getSource().getLevel());
    roundComponent.CustomWinnerID = id;
    // roundComponent
    roundComponent.CustomWinnerSubtitle = null;
    roundComponent.CustomWinnerTitle = null;
    roundComponent.CustomWinnerColor = color;
    roundComponent.setRoundEndData(context.getSource().getLevel().players(), WinStatus.CUSTOM);

    roundComponent.sync();
    context.getSource()
        .sendSuccess(() -> Component.translatable("Stop the game with custom winner id [%s] (CUSTOM)", id), true);
    GameUtils.stopGame(context.getSource().getLevel());
    return 1;
  }

  public static int executeCustomWinWithIdAndTitle(CommandContext<CommandSourceStack> context) {
    Component title = ComponentArgument.getComponent(context, "title");
    Component subtitle = ComponentArgument.getComponent(context, "subtitle");
    String id = "custom_component";
    int color = ModColorArgument.getColor(context, "color");

    ServerPlayer serverPlayer = context.getSource().getPlayer();
    if (serverPlayer != null) {
      try {
        title = ComponentUtils.updateForEntity(
            (CommandSourceStack) context.getSource(),
            title,
            serverPlayer, 0);
        subtitle = ComponentUtils.updateForEntity(
            (CommandSourceStack) context.getSource(),
            subtitle,
            serverPlayer, 0);
      } catch (CommandSyntaxException e) {
        e.printStackTrace();
        context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        return 0;
      }
    }
    var roundComponent = SREGameRoundEndComponent.KEY.get(context.getSource().getLevel());
    roundComponent.CustomWinnerID = id;
    roundComponent.CustomWinnerColor = color;
    roundComponent.CustomWinnerSubtitle = subtitle;
    roundComponent.CustomWinnerTitle = title;
    roundComponent.setRoundEndData(context.getSource().getLevel().players(), WinStatus.CUSTOM_COMPONENT);

    roundComponent.sync();
    context.getSource().sendSuccess(
        () -> Component.translatable("Stop the game with custom winner id [%s] (CUSTOM_COMPONENT)", id), true);

    GameUtils.stopGame(context.getSource().getLevel());
    return 1;
  }

  public static class WinStatusSuggestions {
    public static List<WinStatus> allWinStatus = removeSome(
        new ArrayList<>(Arrays.asList(GameUtils.WinStatus.values())));

    public static List<WinStatus> removeSome(List<WinStatus> list) {
      list.removeIf(
          (t) -> t.equals(GameUtils.WinStatus.CUSTOM) || t.equals(GameUtils.WinStatus.CUSTOM_COMPONENT));
      return list;
    }

    public static CompletableFuture<Suggestions> suggestWinStatus(CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
      Set<String> suggestions = new HashSet<>();
      // 添加自定义 ID 到 Set

      allWinStatus.stream()
          .map(GameUtils.WinStatus::toString)
          .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
          .forEach(suggestions::add);
      // 最后批量建议
      suggestions.forEach((t) -> {
        if (t != null) {
          builder.suggest(t, Component.translatable("announcement.star.win." + t));
        }
      });

      return builder.buildFuture();
    }
  }

  public static class DeathReasonSuggestions {
    private static final List<ResourceLocation> CUSTOM_DEATH_REASONS = Arrays.asList(
        Noellesroles.id("voodoo"),
        Noellesroles.id("shot_innocent"),
        Noellesroles.id("insane_killer_death"),
        Noellesroles.id("arrow"),
        Noellesroles.id("heart_attack"),
        Noellesroles.id("conspiracy_backfire"),
        Noellesroles.id("stalker_execution"),
        Noellesroles.id("bomb_death"),
        Noellesroles.id("puppeteer_puppet"),
        Noellesroles.id("recorder_mistake"),
        Noellesroles.id("gamble_self_kill"),
        Noellesroles.id("wayfarer_error"),
        Noellesroles.id("nianshou_firecrackers"),
        SRE.id("death_afk"),
        SRE.id("disconnected"),
        SRE.id("bat_hit"),
        SRE.id("fell_out_of_train"),
        SRE.id("generic"),
        SRE.id("grenade"),
        SRE.id("gun_shot"),
        SRE.id("knife_stab"),
        SRE.id("poison"),
        SRE.id("revolver_shot"),
        SRE.id("derringer_shot"),
        StupidExpress.id("broken_heart"),
        StupidExpress.id("failed_initiation"),
        StupidExpress.id("allergist"),
        StupidExpress.id("failed_ignite"),
        StupidExpress.id("ignited"));

    public static CompletableFuture<Suggestions> suggestDeathReasons(CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
      Set<String> suggestions = new HashSet<>();
      // 添加自定义 ID 到 Set

      CUSTOM_DEATH_REASONS.stream()
          .map(ResourceLocation::toString)
          .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
          .forEach(suggestions::add);
      if (suggestions.isEmpty()) {
        // 添加物品 ID 到 Set
        BuiltInRegistries.ITEM.keySet().stream()
            .map(ResourceLocation::toString)
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
            .forEach(suggestions::add);
      }
      // 最后批量建议
      suggestions.forEach((s) -> {
        var t = ResourceLocation.tryParse(s);
        if (t != null) {
          builder.suggest(s, GameReplayUtils.getItemDisplayName(t));
        }
      });

      return builder.buildFuture();
    }
  }

  /**
   * 执行时间停止命令
   * 
   * @param context  命令上下文
   * @param duration 持续时间（tick）
   * @return 1 表示成功
   */
  public static int executeTimeStop(CommandContext<CommandSourceStack> context, int duration) {
    var source = context.getSource();
    ServerPlayer executor = source.getPlayer();

    if (executor == null) {
      source.sendFailure(Component.literal("Only players can use this command!").withStyle(ChatFormatting.RED));
      return 0;
    }

    // 触发时间停止效果
    TimeStopEffect.tryTriggerStart(executor, duration, Component.translatable("Test Time Stop!"));

    source.sendSuccess(() -> Component.translatable("Triggered time stop for %s ticks! Only you can move.", duration)
        .withStyle(ChatFormatting.GOLD), true);

    return 1;
  }

  /**
   * 停止时间停止效果
   * 
   * @param context 命令上下文
   * @return 1 表示成功
   */
  public static int executeTimeStopStop(CommandContext<CommandSourceStack> context) {
    var source = context.getSource();

    // 清除所有玩家的时间停止效果
    for (ServerPlayer player : source.getLevel().players()) {
      player.removeEffect((ModEffects.TIME_STOP));

    }

    // 清空可移动玩家列表
    TimeStopEffect.canMovePlayers.clear();
    TimeStopEffect.clientPositions.clear();

    source.sendSuccess(() -> Component.translatable("Stopped time stop! All players can now move.")
        .withStyle(ChatFormatting.GREEN), true);

    return 1;
  }
}
