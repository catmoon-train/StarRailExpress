package org.agmas.noellesroles.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.content.command.ConfigCommand;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.VoteSession.VoteResultOption;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.game.MapResetManager;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses.ServerTaskInfo;
import io.wifi.starrailexpress.game.modes.funny.rotation.LightningDraftState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.game.roles.neutral.gambler.GamblerPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.ProblemScreenOpenC2SPacket;
import org.agmas.noellesroles.packet.ScanAllTaskPointsPayload;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.scene.SceneTaskManager;
import org.agmas.noellesroles.utils.MapScannerManager;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

public class GameUtilsCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {

    dispatcher.register(Commands.literal("cooldown")
        .requires(source -> Harpymodloader.officialVerify
            && source.hasPermission(SREConfig.instance().cooldownCommandsRequiredPermission))
        .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("item", ItemArgument.item(registryAccess))
                .then(Commands.argument("time", IntegerArgumentType.integer(0))
                    .executes((ctx) -> {
                      var source = ctx.getSource();
                      ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                      Item item = ItemArgument.getItem(ctx, "item").getItem();
                      int time = IntegerArgumentType.getInteger(ctx, "time");
                      player.getCooldowns().addCooldown(item, time);
                      source
                          .sendSuccess(
                              () -> Component.translatable("Add a cooldown of %s seconds to item %s for %s.",
                                  String.format("%.2f", time / 20d), item.getDescription(), player.getName()),
                              true);
                      return 1;
                    })))));
    dispatcher.register(
        Commands.literal("tmm:game")
            .requires(source -> Harpymodloader.officialVerify
                && source.hasPermission(SREConfig.instance().gameUtilsRequiredPermission))
            .then(Commands.literal("role")
                .requires(p -> p.hasPermission(SREConfig.instance().changeRoleRequiredPermission))
                .then(Commands.literal("silent_change")
                    .then(Commands.argument("role", RoleArgumentType.create(false)).executes((ctx) -> {
                      ServerPlayer player = ctx.getSource().getPlayerOrException();
                      SRERole role = RoleArgumentType.getRole(ctx, "role");
                      var cca = SRERoleWorldComponent.KEY.get(player.level());
                      cca.addRole(player, role);
                      ctx.getSource().sendSuccess(
                          () -> Component.translatable("Successfully silently change the role of %s to %s",
                              player.getName(), RoleUtils.getRoleOrModifierNameWithColor(role)),
                          true);
                      return 1;
                    })
                        .then(Commands.literal("no_sync").executes((ctx) -> {
                          ServerPlayer player = ctx.getSource().getPlayerOrException();
                          SRERole role = RoleArgumentType.getRole(ctx, "role");
                          var cca = SRERoleWorldComponent.KEY.get(player.level());
                          cca.addRole(player.getUUID(), role, false);
                          ctx.getSource().sendSuccess(
                              () -> Component.translatable("Successfully silently change the role of %s to %s",
                                  player.getName(), RoleUtils.getRoleOrModifierNameWithColor(role)),
                              true);
                          return 1;
                        }))))
                .then(Commands.literal("send_welcome").executes((ctx) -> {
                  ServerPlayer player = ctx.getSource().getPlayerOrException();
                  RoleUtils.sendWelcomeAnnouncement(player);

                  ctx.getSource().sendSuccess(
                      () -> Component.translatable("Successfully send welcome payload to %s",
                          player.getName()),
                      true);
                  return 1;
                }).then(Commands.argument("killer_count", IntegerArgumentType.integer()).executes((ctx) -> {
                  int killerCount = IntegerArgumentType.getInteger(ctx, "killer_count");
                  ServerPlayer player = ctx.getSource().getPlayerOrException();
                  SRERole role = SRERoleWorldComponent.KEY.get(player.level()).getRole(player);
                  if (role == null) {
                    throw ConfigCommand
                        .createSimpleSyntaxException(new Exception("Player doesn't have any roles!"));
                  }
                  // -1 时根据游戏中实际的杀手数量报幕
                  if (killerCount == -1) {
                    RoleUtils.sendWelcomeAnnouncement(player);
                  } else {
                    RoleUtils.sendWelcomeAnnouncement(player, role.identifier(), killerCount);
                  }

                  ctx.getSource().sendSuccess(
                      () -> Component.translatable("Successfully send welcome payload to %s",
                          player.getName()),
                      true);
                  return 1;
                }).then(Commands.argument("role", RoleArgumentType.create(false)).executes((ctx) -> {
                  int killerCount = IntegerArgumentType.getInteger(ctx, "killer_count");
                  ServerPlayer player = ctx.getSource().getPlayerOrException();
                  SRERole role = RoleArgumentType.getRole(ctx, "role");
                  // -1 时根据游戏中实际的杀手数量报幕
                  if (killerCount == -1) {
                    RoleUtils.sendWelcomeAnnouncement(player, role);
                  } else {
                    RoleUtils.sendWelcomeAnnouncement(player, role.identifier(), killerCount);
                  }
                  ctx.getSource().sendSuccess(
                      () -> Component.translatable("Successfully send [%s] welcome payload to %s",
                          RoleUtils.getRoleOrModifierNameWithColor(role), player.getName()),
                      true);
                  return 1;
                }))))
                .then(Commands.literal("sync_roles").executes((ctx) -> {
                  ServerLevel level = ctx.getSource().getLevel();
                  SRERoleWorldComponent.KEY.get(level).sync();

                  ctx.getSource().sendSuccess(
                      () -> Component.translatable("Successfully sync SRERoleWorldComponent to all players!"),
                      true);
                  return 1;
                }))
                .then(Commands.literal("assign_event").executes((ctx) -> {
                  ServerPlayer player = ctx.getSource().getPlayerOrException();
                  SRERole role = SRERoleWorldComponent.KEY.get(player.level()).getRole(player);
                  if (role == null) {
                    throw ConfigCommand
                        .createSimpleSyntaxException(new Exception("Player doesn't have any roles!"));
                  }
                  ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);

                  ctx.getSource().sendSuccess(
                      () -> Component.translatable("Successfully triggered role assigned events to %s (%s)",
                          player.getName(), RoleUtils.getRoleOrModifierNameWithColor(role)),
                      true);
                  return 1;
                }))
                .then(Commands.literal("remove_event").executes((ctx) -> {
                  ServerPlayer player = ctx.getSource().getPlayerOrException();
                  SRERole role = SRERoleWorldComponent.KEY.get(player.level()).getRole(player);
                  if (role == null) {
                    throw ConfigCommand
                        .createSimpleSyntaxException(new Exception("Player doesn't have any roles!"));
                  }
                  ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, role);

                  ctx.getSource().sendSuccess(
                      () -> Component.translatable("Successfully triggered role removed events to %s (%s)",
                          player.getName(), RoleUtils.getRoleOrModifierNameWithColor(role)),
                      true);
                  return 1;
                })))
            .then(Commands.literal("tests")
                .then(Commands.literal("role_rotaion_weight")
                    .then(Commands.literal("clear").executes(ctx -> {
                      var p = ctx.getSource().getPlayerOrException();
                      LightningDraftState.PLAYER_SORT_WEIGHT.remove(p.getUUID());
                      ctx.getSource()
                          .sendSuccess(
                              () -> Component.translatable(
                                  "Successfully clear player %s role rotation weight", p.getName()),
                              true);
                      return 0;
                    }))
                    .then(Commands.argument("weight", IntegerArgumentType.integer())
                        .executes((ctx) -> {
                          var p = ctx.getSource().getPlayerOrException();
                          int w = IntegerArgumentType.getInteger(ctx, "weight");
                          LightningDraftState.PLAYER_SORT_WEIGHT
                              .put(p.getUUID(),
                                  w);
                          ctx.getSource()
                              .sendSuccess(
                                  () -> Component.translatable(
                                      "Successfully change player %s role rotation weight to %s", p.getName(), w),
                                  true);
                          return 0;
                        })))
                .then(Commands.literal("vote_players").executes((ctx) -> {
                  var builder = VoteManager.builder(Component.literal("Test for vote"));
                  var source = ctx.getSource();
                  var players = source.getLevel().players();
                  for (ServerPlayer p : players) {
                    builder.addOption(VoteOption.player(p), p.getGameProfile().getName());
                  }
                  builder
                      .duration(20 * 10) // 30 秒
                      .allowReVote(true)
                      .showResults(true)
                      .maxSelect(3)
                      .callback(s -> {
                        StringBuilder topResult = new StringBuilder();
                        for (Entry<String, VoteResultOption> topResults : s.getTopResults()) {
                          if (topResult.length() != 0)
                            topResult.append(", ");
                          topResult.append(topResults.getKey());
                        }
                        for (ServerPlayer p : players) {
                          p.sendSystemMessage(
                              Component.translatable("Select Result:\nWinner: %s", topResult.toString()));
                        }
                      })
                      .start();
                  return 1;
                }))
                .then(Commands.literal("prayer").executes((ctx) -> {
                  org.agmas.noellesroles.game.roles.innocence.fool.PrayerHandler
                      .startPrayer(ctx.getSource().getPlayerOrException());
                  ctx.getSource().sendSuccess(() -> Component.literal("Successfully prayer."), false);
                  return 1;
                }))
                .then(Commands.literal("gambler_draw").executes((ctx) -> {
                  var player = ctx.getSource().getPlayerOrException();
                  if (!RoleUtils.isPlayerTheJob(player, ModRoles.GAMBLER)) {

                    ctx.getSource().sendFailure(Component.literal("Not a gambler."));
                    return 0;
                  }
                  GamblerPlayerComponent.KEY.get(player).drawNewRole();
                  ctx.getSource()
                      .sendSuccess(() -> Component.literal("Successfully draw a new role to gambler."), false);
                  return 1;
                }))
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
                      Component.literal("Sync Task Queue:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                  int idx = 0;
                  for (ServerTaskInfo inf : GameUtils.serverTaskQueue) {
                    source
                        .sendSystemMessage(Component.translatable("[%s] %s", idx, inf.getClass().getSimpleName())
                            .withStyle(ChatFormatting.AQUA));
                    idx++;
                  }
                  source.sendSystemMessage(
                      Component.literal("\nAsyn Task List:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                  idx = 0;
                  for (ServerTaskInfo inf : GameUtils.serverAsynTaskLists) {
                    source
                        .sendSystemMessage(Component.translatable("[%s] %s", idx, inf.getClass().getSimpleName())
                            .withStyle(ChatFormatting.AQUA));
                    idx++;
                  }
                  source.sendSuccess(() -> {
                    return Component.translatable("\nSync Task Queue size: %s\nAsyn Task List size: %s",
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
                    }))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes((context) -> {
                          ServerPlayer target = EntityArgument.getPlayer(context, "player");
                          return clearTasksForPlayer(context.getSource(), target);
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
                                .translatable("Cancelled task %s (tid: %s)!", task.getClass().getSimpleName(),
                                    tid)
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
                                .translatable("Cancelled task %s (tid: %s)!", task.getClass().getSimpleName(),
                                    tid)
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
                        }))))
                .then(Commands.literal("add")
                    .then(Commands.argument("taskId", StringArgumentType.word())
                        .suggests(TaskIdSuggestions::suggest)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes((context) -> {
                              String taskId = StringArgumentType.getString(context, "taskId");
                              ServerPlayer target = EntityArgument.getPlayer(context, "player");
                              return addTaskToPlayer(context.getSource(), taskId, target);
                            }))))
                .then(Commands.literal("set")
                    .then(Commands.argument("taskId", StringArgumentType.word())
                        .suggests(TaskIdSuggestions::suggest)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes((context) -> {
                              String taskId = StringArgumentType.getString(context, "taskId");
                              ServerPlayer target = EntityArgument.getPlayer(context, "player");
                              return setTaskForPlayer(context.getSource(), taskId, target);
                            }))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("taskId", StringArgumentType.word())
                        .suggests(TaskIdSuggestions::suggest)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes((context) -> {
                              String taskId = StringArgumentType.getString(context, "taskId");
                              ServerPlayer target = EntityArgument.getPlayer(context, "player");
                              return removeTaskFromPlayer(context.getSource(), taskId, target);
                            })))))
            .then(Commands.literal("win")
                .requires((p) -> p.hasPermission(SREConfig.instance().stopGameRequiredPermission))
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(WinStatusSuggestions::suggestWinStatus)
                    .executes(GameUtilsCommand::executeWinWithOnlyId))
                .then(Commands.literal("CUSTOM")
                    .then(Commands.argument("color", ModColorArgument.color())
                        .then(
                            Commands.argument("id", StringArgumentType.string())
                                .executes(GameUtilsCommand::executeCustomWinWithOnlyId))))
                .then(Commands.literal("CUSTOM_COMPONENT")
                    .then(Commands.argument("color", ModColorArgument.color())
                        .then(Commands.argument("title", ComponentArgument.textComponent(registryAccess))
                            .then(Commands
                                .argument(
                                    "subtitle", ComponentArgument.textComponent(registryAccess))
                                .executes(GameUtilsCommand::executeCustomWinWithIdAndTitle))))))
            .then(Commands.literal("reset")
                .requires(p -> p.hasPermission(SREConfig.instance().forceResetRequiredPermission))
                .then(Commands.literal("blocks")
                    .then(Commands.literal("copy").executes((context) -> {
                      // GameUtils.tryAutoTrainReset(context.getSource().getLevel());
                      var world = context.getSource().getLevel();
                      var areas = AreasWorldComponent.KEY.get(world);
                      ServerTaskInfoClasses.FullTrainResetTask task = new ServerTaskInfoClasses.FullTrainResetTask(
                          areas,
                          world, null, 0);
                      task.shouldStartGame = false;

                      GameUtils.serverTaskQueue.add(task);
                      context.getSource()
                          .sendSuccess(() -> Component.literal("Add server reset task: Normal Reset(copy)!"),
                              true);
                      return 1;
                    }))
                    .then(Commands.literal("simple").executes((context) -> {
                      var world = context.getSource().getLevel();
                      MapResetManager.loadArea(world);
                      var areas = AreasWorldComponent.KEY.get(world);
                      ServerTaskInfoClasses.OnlySomeBlockResetTask task = new ServerTaskInfoClasses.OnlySomeBlockResetTask(
                          GameUtils.resetPoints, world, null, 0, areas);
                      task.shouldStartGame = false;
                      GameUtils.serverTaskQueue.add(task);
                      context.getSource().sendSuccess(
                          () -> Component.literal("Add server reset task: Simple Reset (clean points only)!"),
                          true);
                      return 1;
                    })))
                .then(Commands.literal("entity").then(Commands.literal("clear").executes((context) -> {
                  GameUtils.resetEntities(context.getSource().getLevel());
                  context.getSource().sendSuccess(() -> Component.literal("Cleared entity!"),
                      true);
                  return 1;
                }))))
            .then(Commands.literal("scan")
                .requires(p -> p.hasPermission(SREConfig.instance().forceResetRequiredPermission))
                .executes((context) -> {
                  var source = context.getSource();
                  var level = source.getLevel();
                  var areas = AreasWorldComponent.KEY.get(level);
                  if (areas.mapName == null) {
                    context.getSource()
                        .sendFailure(Component
                            .literal(
                                "You should load map first to scan points!\nUsage: /tmm:switchmap load <MapID>")
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
                            .literal(
                                "You should load map first to scan points!\nUsage: /tmm:switchmap load <MapID>")
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
              return executeBlackout(context, 0);
            }).then(Commands.argument("duration", IntegerArgumentType.integer(0)).executes((context) -> {
              return executeBlackout(context, IntegerArgumentType.getInteger(context, "duration"));
            })).then(Commands.literal("stop").executes((context) -> {
              return executeBlackout(context, -1);
            })))
            .then(Commands.literal("monitor_broken").executes((context) -> {
              return executeMonitorBroken(context, 0);
            })
                .then(Commands.argument("duration", IntegerArgumentType.integer(0)).executes((context) -> {
                  return executeMonitorBroken(context, IntegerArgumentType.getInteger(context, "duration"));
                }))
                .then(Commands.literal("stop").executes((context) -> {
                  return executeMonitorBroken(context, -1);
                })))
            .then(Commands.literal("psycho").executes((context) -> {
              return executePsycho(context, -1);
            })
                .then(Commands.literal("stop").executes((context) -> {
                  return executePsycho(context, 0);
                })))
            .then(Commands.literal("body")
                .then(Commands.literal("teleport").executes((ctx) -> {
                  ServerPlayer player = ctx.getSource().getPlayerOrException();
                  var body = GameUtils.findPlayerBodyEntity(player);
                  if (body == null) {
                    throw ConfigCommand
                        .createSimpleSyntaxException(new Exception("Cannot find the player body in the world!"));
                  }
                  player.teleportTo(body.getX(), body.getY(), body.getZ());

                  ctx.getSource().sendSuccess(
                      () -> Component.translatable("Teleport player %s to its body", player.getName()), true);
                  return 1;
                }))
                .then(Commands.literal("kill").executes((ctx) -> {
                  ServerPlayer player = ctx.getSource().getPlayerOrException();
                  var body = GameUtils.findPlayerBodyEntity(player);
                  if (body == null) {
                    throw ConfigCommand
                        .createSimpleSyntaxException(new Exception("Cannot find the player body in the world!"));
                  }
                  body.discard();
                  ctx.getSource().sendSuccess(
                      () -> Component.translatable("Killed player body of %s", player.getName()), true);
                  return 1;
                }))
                .then(Commands.literal("as_run")
                    .fork(dispatcher.getRoot(), (commandContext) -> {
                      List<CommandSourceStack> list = Lists.newArrayList();
                      ServerPlayer player = commandContext.getSource().getPlayerOrException();
                      var body = GameUtils.findPlayerBodyEntity(player);
                      if (body == null) {
                        throw ConfigCommand
                            .createSimpleSyntaxException(
                                new Exception("Cannot find the player body in the world!"));
                      }
                      list.add(body.createCommandSourceStack());
                      return list;
                    })))
            .then(Commands.literal("revive")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                      ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                      GameUtils.revivePlayer(player, player.getX(), player.getY(), player.getZ());
                      ctx.getSource().sendSuccess(
                          () -> Component.translatable("Revived player %s to pos %s", player.getName(),
                              player.position().toString()),
                          false);
                      return 1;
                    })

                    .then(Commands.literal("to_body").executes((ctx) -> {
                      ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                      var body = GameUtils.findPlayerBodyEntity(player);
                      if (body == null) {
                        throw ConfigCommand
                            .createSimpleSyntaxException(
                                new Exception("Cannot find the player body in the world!"));
                      }
                      Vec3 pos = body.position();
                      GameUtils.revivePlayer(player, pos.x, pos.y, pos.z);
                      ctx.getSource().sendSuccess(
                          () -> Component.translatable("Revived player %s to pos %s", player.getName(),
                              player.position().toString()),
                          false);
                      return 1;
                    })
                        .then(Commands.argument("remove_body", BoolArgumentType.bool())
                            .executes(ctx -> {
                              ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                              boolean removeBody = BoolArgumentType.getBool(ctx, "remove_body");
                              var body = GameUtils.findPlayerBodyEntity(player);
                              if (body == null) {
                                throw ConfigCommand
                                    .createSimpleSyntaxException(
                                        new Exception("Cannot find the player body in the world!"));
                              }
                              Vec3 pos = body.position();
                              GameUtils.revivePlayer(player, pos.x, pos.y, pos.z);
                              if (removeBody) {
                                body.discard();
                              }
                              ctx.getSource().sendSuccess(
                                  () -> Component.translatable("Revived player %s to pos %s", player.getName(),
                                      player.position().toString()),
                                  false);
                              return 1;
                            })))
                    .then(Commands.argument("pos", Vec3Argument.vec3(true)).executes(ctx -> {
                      ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                      Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                      GameUtils.revivePlayer(player, pos.x, pos.y, pos.z);
                      ctx.getSource().sendSuccess(
                          () -> Component.translatable("Revived player %s to pos %s", player.getName(),
                              player.position().toString()),
                          false);
                      return 1;
                    }))))
            .then(Commands.literal("kill")
                .requires(p -> p.hasPermission(SREConfig.instance().gameKillRequiredPermission))
                .then(Commands.argument("victim", EntityArgument.player())
                    .then(Commands.argument("death_reason", ResourceLocationArgument.id())
                        .suggests(DeathReasonSuggestions::suggestDeathReasons)
                        .executes((context) -> {
                          ServerPlayer victim = EntityArgument.getPlayer(context, "victim");
                          ResourceLocation deathReason = ResourceLocationArgument.getId(context,
                              "death_reason");
                          return executeKillPlayer(context, victim, null, deathReason, true, false);
                        })
                        .then(Commands.argument("killer", EntityArgument.player()).executes((context) -> {
                          ServerPlayer victim = EntityArgument.getPlayer(context, "victim");
                          ServerPlayer killer = EntityArgument.getPlayer(context, "killer");
                          ResourceLocation deathReason = ResourceLocationArgument.getId(context,
                              "death_reason");
                          return executeKillPlayer(context, victim, killer, deathReason, true, false);
                        })
                            .then(Commands.argument("spawn_body", BoolArgumentType.bool()).executes((context) -> {
                              ServerPlayer victim = EntityArgument.getPlayer(context, "victim");
                              boolean spawnBody = BoolArgumentType.getBool(context, "spawn_body");
                              ServerPlayer killer = EntityArgument.getPlayer(context, "killer");
                              ResourceLocation deathReason = ResourceLocationArgument.getId(context,
                                  "death_reason");
                              return executeKillPlayer(context, victim, killer, deathReason, spawnBody, false);
                            })
                                .then(Commands.literal("force").executes((context -> {
                                  ServerPlayer victim = EntityArgument.getPlayer(context, "victim");
                                  boolean spawnBody = BoolArgumentType.getBool(context, "spawn_body");
                                  ServerPlayer killer = EntityArgument.getPlayer(context, "killer");
                                  ResourceLocation deathReason = ResourceLocationArgument.getId(context,
                                      "death_reason");
                                  return executeKillPlayer(context, victim, killer, deathReason, spawnBody, true);
                                }))))))))
            .then(Commands.literal("timestop")
                .then(Commands.argument("duration", IntegerArgumentType.integer(20, 1200))
                    .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                        .executes((context) -> {
                          int duration = IntegerArgumentType.getInteger(context, "duration");
                          Component message = ComponentArgument.getComponent(context, "message");
                          return executeTimeStop(context, duration, message);
                        })))
                .then(Commands.literal("stop")
                    .executes((context) -> {
                      return executeTimeStopStop(context);
                    }))))
            .then(Commands.literal("tag")
                .then(Commands.literal("give")
                    .then(Commands.argument("tag", StringArgumentType.word())
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes((context) -> {
                              ServerPlayer player = EntityArgument.getPlayer(context, "player");
                              String tag = StringArgumentType.getString(context, "tag");
                              player.addTag(tag);
                              context.getSource().sendSuccess(
                                  () -> Component.literal("Tag '" + tag + "' added to " + player.getName().getString()),
                                  true);
                              return 1;
                            }))))
                .then(Commands.literal("clear")
                    .then(Commands.argument("tag", StringArgumentType.word())
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes((context) -> {
                              ServerPlayer player = EntityArgument.getPlayer(context, "player");
                              String tag = StringArgumentType.getString(context, "tag");
                              player.removeTag(tag);
                              context.getSource().sendSuccess(
                                  () -> Component.literal(
                                      "Tag '" + tag + "' removed from " + player.getName().getString()),
                                  true);
                              return 1;
                            }))));

  }

  public static int executeKillPlayer(CommandContext<CommandSourceStack> context, ServerPlayer victim,
      @Nullable ServerPlayer killer, ResourceLocation deathReason, boolean spawnBody, boolean force) {
    ResourceLocation deathReasonRL = deathReason;
    final String deathReasonT = deathReasonRL.toLanguageKey();
    GameUtils.killPlayer(victim, spawnBody, killer, deathReasonRL, force);
    context.getSource()
        .sendSuccess(() -> Component.translatable("Killed player %s by %s with reason %s (Spawn body: %s)",
            victim.getName(), (killer == null ? Component.literal("System") : killer.getName()),
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

    if (time != 0) {
      if (ppc.psychoTicks > 0) {
        source.sendFailure(Component.literal("The player is already in psycho mode!").withStyle(ChatFormatting.RED));
        return 0;
      }
      ppc.startPsycho();
      context.getSource()
          .sendSuccess(() -> Component.translatable("Triggered %s Psycho!", player.getScoreboardName()), true);
    } else {
      ppc.stopPsychoAndRefreshPsychoCount(true);
      ppc.sync();
      context.getSource()
          .sendSuccess(() -> Component.translatable("Stopped %s Psycho!", player.getScoreboardName()), true);
    }
    return 1;
  }

  public static int executeMonitorBroken(CommandContext<CommandSourceStack> context, int time) {
    var wbc = SREMonitorWorldComponent.KEY.get(context.getSource().getLevel());
    if (time != -1) {
      if (time == 0) {
        wbc.triggerBroken(true, SREConfig.instance().monitorBrokenDuration * 20);
      } else {
        wbc.triggerBroken(true, time);
      }
      context.getSource()
          .sendSuccess(() -> Component.translatable("Triggered Monitor Broken!"), true);

    } else {
      context.getSource()
          .sendSuccess(() -> Component.translatable("Stopped All Monitor Broken!"), true);
      wbc.reset();

    }

    return 1;
  }

  public static int executeBlackout(CommandContext<CommandSourceStack> context, int time) {
    var wbc = SREWorldBlackoutComponent.KEY.get(context.getSource().getLevel());
    if (time != -1) {
      if (time == 0) {
        wbc.triggerBlackout(true);
      } else {
        wbc.triggerBlackout(true, time);
      }
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
          builder.suggest(t, Component.translatableWithFallback("announcement.star.win." + t.toLowerCase(), t));
        }
      });

      return builder.buildFuture();
    }
  }

  public static class DeathReasonSuggestions {
    public static Set<ResourceLocation> getAllDeathReasons() {
      Set<ResourceLocation> set = new HashSet<>();
      Field[] fields = GameConstants.DeathReasons.class.getDeclaredFields();
      for (Field field : fields) {
        if (Modifier.isStatic(field.getModifiers())
            && field.getType() == ResourceLocation.class) {
          try {
            ResourceLocation value = (ResourceLocation) field.get(null);
            set.add(value);
          } catch (IllegalAccessException e) {
            // 理论上静态字段可访问，若发生异常则忽略
            e.printStackTrace();
          }
        }
      }
      return set;
    }

    private static ResourceLocation itemId(Item item) {
      return BuiltInRegistries.ITEM.getKey(item);
    }

    public static Set<ResourceLocation> getItemDeathReasons() {
      Set<ResourceLocation> set = new HashSet<>(Set.of(
          itemId(ModItems.THROWING_KNIFE),
          itemId(ModItems.NINJA_SHURIKEN),
          ResourceLocation.fromNamespaceAndPath("starrailexpress", "gun_shot")));
      set.remove(null);
      return set;
    }

    public static Set<ResourceLocation> getAllSuggestedDeathReasons() {
      return GameConstants.DeathReasons.getAllDeathReasons();
    }

    public static CompletableFuture<Suggestions> suggestDeathReasons(CommandContext<CommandSourceStack> context,
        SuggestionsBuilder builder) {
      String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
      Set<String> suggestions = new HashSet<>();
      // 添加自定义 ID 到 Sety

      getAllSuggestedDeathReasons().stream()
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
  public static int executeTimeStop(CommandContext<CommandSourceStack> context, int duration, Component message) {
    var source = context.getSource();
    ServerPlayer executor = source.getPlayer();

    if (executor == null) {
      source.sendFailure(Component.literal("Only players can use this command!").withStyle(ChatFormatting.RED));
      return 0;
    }

    // 触发时间停止效果
    TimeStopEffect.tryTriggerStart(executor, duration, message);

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

  // ───────────── tmm:game tasks 玩家任务管理 ─────────────

  private static int addTaskToPlayer(CommandSourceStack source, String taskId, ServerPlayer target) {
    try {
      io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task taskType = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task
          .valueOf(taskId.toUpperCase());
      var comp = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.KEY.get(target);
      if (comp == null) {
        source.sendFailure(Component.literal("Player task component not found.").withStyle(ChatFormatting.RED));
        return 0;
      }
      var taskInstance = taskType.setFunction.apply(new net.minecraft.nbt.CompoundTag());
      comp.tasks.put(taskType, taskInstance);
      activateSceneTask(target, taskType);
      comp.sync();
      source.sendSuccess(() -> Component.translatable("Added task %s to player %s.", taskId,
          target.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
      return 1;
    } catch (IllegalArgumentException e) {
      source.sendFailure(Component.literal("Unknown task type: " + taskId).withStyle(ChatFormatting.RED));
      return 0;
    }
  }

  private static int setTaskForPlayer(CommandSourceStack source, String taskId, ServerPlayer target) {
    try {
      io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task taskType = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task
          .valueOf(taskId.toUpperCase());
      var comp = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.KEY.get(target);
      if (comp == null) {
        source.sendFailure(Component.literal("Player task component not found.").withStyle(ChatFormatting.RED));
        return 0;
      }
      comp.tasks.clear();
      comp.taskStreak = 0;
      comp.currentTaskAge = 0;
      comp.parallelTaskGenerated = false;
      comp.parallelTaskTypes.clear();
      SceneTaskManager.clear(target);
      var taskInstance = taskType.setFunction.apply(new net.minecraft.nbt.CompoundTag());
      comp.tasks.put(taskType, taskInstance);
      activateSceneTask(target, taskType);
      comp.sync();
      source.sendSuccess(() -> Component.translatable("Set task %s for player %s (previous tasks cleared).", taskId,
          target.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
      return 1;
    } catch (IllegalArgumentException e) {
      source.sendFailure(Component.literal("Unknown task type: " + taskId).withStyle(ChatFormatting.RED));
      return 0;
    }
  }

  private static int removeTaskFromPlayer(CommandSourceStack source, String taskId, ServerPlayer target) {
    try {
      io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task taskType = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task
          .valueOf(taskId.toUpperCase());
      var comp = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.KEY.get(target);
      if (comp == null) {
        source.sendFailure(Component.literal("Player task component not found.").withStyle(ChatFormatting.RED));
        return 0;
      }
      if (comp.tasks.containsKey(taskType)) {
        comp.tasks.remove(taskType);
        clearSceneTask(target, taskType);
        comp.sync();
        source.sendSuccess(() -> Component.translatable("Removed task %s from player %s.", taskId,
            target.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
      } else {
        source.sendFailure(Component.translatable("Player %s does not have task %s.",
            target.getDisplayName(), taskId).withStyle(ChatFormatting.YELLOW));
      }
      return 1;
    } catch (IllegalArgumentException e) {
      source.sendFailure(Component.literal("Unknown task type: " + taskId).withStyle(ChatFormatting.RED));
      return 0;
    }
  }

  /** 任务 ID Tab 补全建议（返回所有 Task 枚举名小写）。 */
  private static int clearTasksForPlayer(CommandSourceStack source, ServerPlayer target) {
    var comp = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.KEY.get(target);
    if (comp == null) {
      source.sendFailure(Component.literal("Player task component not found.").withStyle(ChatFormatting.RED));
      return 0;
    }
    comp.tasks.clear();
    comp.taskStreak = 0;
    comp.currentTaskAge = 0;
    comp.parallelTaskGenerated = false;
    comp.parallelTaskTypes.clear();
    SceneTaskManager.clear(target);
    comp.sync();

    var minigameComp = io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(target);
    if (minigameComp != null) {
      minigameComp.pendingMinigameTasks = 0;
      minigameComp.targetMinigameId = null;
      minigameComp.sync();
    }

    source.sendSuccess(() -> Component.translatable("Cleared all tasks for player %s.", target.getDisplayName())
        .withStyle(ChatFormatting.GREEN), true);
    return 1;
  }

  private static void activateSceneTask(ServerPlayer target,
      io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task taskType) {
    SceneTaskManager.Type sceneType = toSceneTaskType(taskType);
    if (sceneType != null) {
      SceneTaskManager.assign(target, sceneType);
    }
  }

  private static void clearSceneTask(ServerPlayer target,
      io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task taskType) {
    SceneTaskManager.Type sceneType = toSceneTaskType(taskType);
    if (sceneType != null) {
      SceneTaskManager.clear(target, sceneType);
    }
  }

  private static @Nullable SceneTaskManager.Type toSceneTaskType(
      io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task taskType) {
    return switch (taskType) {
      case LIGHT_STOVE -> SceneTaskManager.Type.LIGHT_STOVE;
      case CLEAN_DUST -> SceneTaskManager.Type.CLEAN_DUST;
      case TRANSPORT -> SceneTaskManager.Type.TRANSPORT;
      case PRAY -> SceneTaskManager.Type.PRAY;
      case PRUNE_BUSH -> SceneTaskManager.Type.PRUNE_BUSH;
      case HARVEST_CROP -> SceneTaskManager.Type.HARVEST_CROP;
      case BE_ALONE -> SceneTaskManager.Type.BE_ALONE;
      default -> null;
    };
  }

  private static final class TaskIdSuggestions {
    static java.util.concurrent.CompletableFuture<Suggestions> suggest(
        CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
      for (io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task t : io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task
          .values()) {
        String name = t.name().toLowerCase();
        if (name.startsWith(builder.getRemainingLowerCase())) {
          builder.suggest(name);
        }
      }
      return builder.buildFuture();
    }
  }
}
