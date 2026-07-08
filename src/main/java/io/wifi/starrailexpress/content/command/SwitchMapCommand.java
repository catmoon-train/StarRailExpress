package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.command.argument.MapLoadArgumentType;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.game.MapResetManager;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.agmas.noellesroles.utils.MapScannerManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwitchMapCommand {
  public static LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("tmm:switchmap")
      .requires(source -> source.hasPermission(SREConfig.instance().switchMapRequiredPermission))
      .then(Commands.literal("reset_and_scan_all")
          .requires(source -> source.hasPermission(3))
          .executes(SwitchMapCommand::executeScanWithReset))
      .then(Commands.literal("scan_all")
          .requires(source -> source.hasPermission(3))
          .executes(SwitchMapCommand::executeScanWithoutReset))
      .then(Commands.literal("load")
          .then(Commands.argument("mapName",
              MapLoadArgumentType.string())
              .executes(context -> executeLoad(
                  context.getSource(),
                  StringArgumentType
                      .getString(context,
                          "mapName")))))
      .then(Commands.literal("list")
          .executes(context -> executeList(context.getSource())))
      .then(Commands.literal("random")
          .executes(context -> executeRandom(
              context.getSource())))
      .executes(context -> {
        // 没有参数时，显示当前地图信息
        return showCurrentMap(context.getSource());
      });

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        node);
  }

  private static int executeScanWithoutReset(CommandContext<CommandSourceStack> context) {
    try {
      CommandSourceStack source = context.getSource();
      ServerLevel serverWorld = source.getLevel();
      final PlayerList playerList = serverWorld.getServer().getPlayerList();
      // 检查游戏是否正在运行
      SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
      if (gameComponent.isRunning()) {
        source.sendFailure(Component.translatable("commands.sre.switchmap.error.game_running"));
        return -1;
      }
      ServerLevel serverLevel = context.getSource().getLevel();
      List<String> availableMaps = MapManager.getAvailableMaps(serverLevel, false);
      int idx = 0;
      final int total = availableMaps.size();
      final AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
      for (final String mapName : availableMaps) {
        idx++;
        final int now = idx;
        GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(20, () -> {
          if (MapManager.loadMap(serverWorld, mapName)) {
            playerList.broadcastSystemMessage(
                Component.translatable(
                    "Loading maps...\nNow: %s [%s / %s]",
                    mapName,
                    now, total)
                    .withStyle(ChatFormatting.AQUA),
                false);
            GameUtils.serverTaskQueue.addFirst(
                new ServerTaskInfoClasses.SchedulerTask(20, () -> {
                  playerList.broadcastSystemMessage(
                      Component.translatable(
                          "Scanning points...")
                          .withStyle(ChatFormatting.YELLOW),
                      false);
                  MapResetManager.scanArea(serverWorld, areas);
                  MapResetManager.saveArea(serverWorld);
                  playerList.broadcastSystemMessage(
                      Component
                          .translatable(
                              "Scanned and saved reset points for map %s ! Total %s blocks!",
                              Component.nullToEmpty(
                                  areas.mapName),
                              GameUtils.resetPoints
                                  .size())
                          .withStyle(ChatFormatting.GRAY),
                      false);
                  MapScannerManager.scanAndSaveScannerArea(
                      serverWorld, areas);
                  HashMap<Integer, Boolean> map = new HashMap<>();
                  for (Map.Entry<BlockPos, Integer> entry : GameUtils.taskBlocks
                      .entrySet()) {
                    map.putIfAbsent(entry.getValue(), true);
                  }
                  playerList.broadcastSystemMessage(
                      Component.translatable(
                          "Scanned Task points! Total %s types!",
                          map.size())
                          .withStyle(ChatFormatting.GRAY),
                      false);
                }));
          } else {
            playerList.broadcastSystemMessage(
                Component.translatable(
                    "Reseting and scaning map %s failed. [%s / %s]",
                    mapName, now,
                    total).withStyle(ChatFormatting.RED),
                false);
          }
        }));

      }
      GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(10, () -> {
        playerList.broadcastSystemMessage(
            Component.translatable("\n\nAll scan have finished!")
                .withStyle(ChatFormatting.GREEN),
            false);
      }));
      source.sendSuccess(() -> Component.literal("Successfully add scan tasks."), true);
      // AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverLevel);
      // MapResetManager.scanArea(serverLevel, areas);
      // MapResetManager.saveArea(serverLevel);
      // context.getSource().sendSuccess(() -> Component.literal(
      // "Scanned Successfully! Found " + GameUtils.resetPoints.size() + " blocks
      // should be reseted!"),
      // true);
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendSuccess(() -> Component.literal(
          "Scanned Failed! " + e.getMessage()), true);
      return 0;
    }
    return 1;
  }

  private static int executeScanWithReset(CommandContext<CommandSourceStack> context) {
    try {
      CommandSourceStack source = context.getSource();
      ServerLevel serverWorld = source.getLevel();
      final PlayerList playerList = serverWorld.getServer().getPlayerList();
      // 检查游戏是否正在运行
      SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
      if (gameComponent.isRunning()) {
        source.sendFailure(Component.translatable("commands.sre.switchmap.error.game_running"));
        return -1;
      }
      ServerLevel serverLevel = context.getSource().getLevel();
      List<String> availableMaps = MapManager.getAvailableMaps(serverLevel, false);
      int idx = 0;
      final int total = availableMaps.size();
      final AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
      for (final String mapName : availableMaps) {
        idx++;
        final int now = idx;
        GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(20, () -> {
          if (MapManager.loadMap(serverWorld, mapName)) {
            playerList.broadcastSystemMessage(
                Component.translatable(
                    "Loading maps...\nNow: %s [%s / %s]\nReseting maps...",
                    mapName,
                    now, total)
                    .withStyle(ChatFormatting.AQUA),
                false);
            GameUtils.serverTaskQueue.addFirst(
                new ServerTaskInfoClasses.SchedulerTask(20, () -> {
                  playerList.broadcastSystemMessage(
                      Component.translatable(
                          "Scanning points...")
                          .withStyle(ChatFormatting.YELLOW),
                      false);
                  MapResetManager.scanArea(serverWorld, areas);
                  MapResetManager.saveArea(serverWorld);
                  playerList.broadcastSystemMessage(
                      Component
                          .translatable(
                              "Scanned and saved reset points for map %s ! Total %s blocks!",
                              Component.nullToEmpty(
                                  areas.mapName),
                              GameUtils.resetPoints
                                  .size())
                          .withStyle(ChatFormatting.GRAY),
                      false);
                  MapScannerManager.scanAndSaveScannerArea(
                      serverWorld, areas);
                  HashMap<Integer, Boolean> map = new HashMap<>();
                  for (Map.Entry<BlockPos, Integer> entry : GameUtils.taskBlocks
                      .entrySet()) {
                    map.putIfAbsent(entry.getValue(), true);
                  }
                  playerList.broadcastSystemMessage(
                      Component.translatable(
                          "Scanned Task points! Total %s types!",
                          map.size())
                          .withStyle(ChatFormatting.GRAY),
                      false);
                }));
            GameUtils.serverTaskQueue
                .addFirst(new ServerTaskInfoClasses.FullTrainResetTask(
                    areas, serverWorld, null, 0,
                    false));
          } else {
            playerList.broadcastSystemMessage(
                Component.translatable(
                    "Reseting and scaning map %s failed. [%s / %s]",
                    mapName, now,
                    total).withStyle(ChatFormatting.RED),
                false);
          }
        }));

      }
      GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(10, () -> {
        playerList.broadcastSystemMessage(
            Component.translatable("\n\nAll scan and resets have finished!")
                .withStyle(ChatFormatting.GREEN),
            false);
      }));
      source.sendSuccess(() -> Component.literal("Successfully add scan and reset tasks."), true);
      // AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverLevel);
      // MapResetManager.scanArea(serverLevel, areas);
      // MapResetManager.saveArea(serverLevel);
      // context.getSource().sendSuccess(() -> Component.literal(
      // "Scanned Successfully! Found " + GameUtils.resetPoints.size() + " blocks
      // should be reseted!"),
      // true);
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendSuccess(() -> Component.literal(
          "Scanned Failed! " + e.getMessage()), true);
      return 0;
    }
    return 1;
  }

  private static int executeLoad(CommandSourceStack source, String mapName) {
    ServerLevel serverWorld = source.getLevel();

    // 检查游戏是否正在运行
    SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
    if (gameComponent.isRunning()) {
      source.sendFailure(Component.translatable("commands.sre.switchmap.error.game_running"));
      return -1;
    }

    // 加载地图
    if (MapManager.loadMap(serverWorld, mapName)) {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.switchmap.load.success", mapName)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
      return 1;
    } else {
      source.sendFailure(Component.translatable("commands.sre.switchmap.error.invalid_map", mapName));
      return -1;
    }
  }

  private static int executeList(CommandSourceStack source) {
    ServerLevel serverWorld = source.getLevel();
    List<String> availableMaps = MapManager.getAvailableMaps(serverWorld, true);

    if (availableMaps.isEmpty()) {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.switchmap.list.none")
              .withStyle(style -> style.withColor(0xFFFF00)),
          false);
    } else {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.switchmap.list.header")
              .withStyle(style -> style.withColor(0x00FFFF)),
          false);

      for (String mapName : availableMaps) {
        source.sendSuccess(
            () -> Component.literal(" - [" + mapName + "]")
                .withStyle(style -> style.withColor(0xFFFFFF)
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal(
                            "CLICK TO SWITCH")))
                    .withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/tmm:switchmap load '"
                            + mapName
                            + "'"))),
            false);
      }
    }

    return 1;
  }

  private static int executeRandom(CommandSourceStack source) {
    ServerLevel serverWorld = source.getLevel();

    // 检查游戏是否正在运行
    SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
    if (gameComponent.isRunning()) {
      source.sendFailure(Component.translatable("commands.sre.switchmap.error.game_running"));
      return -1;
    }

    // 随机加载地图
    if (MapManager.loadRandomMap(serverWorld)) {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.switchmap.random.success")
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
      return 1;
    } else {
      source.sendFailure(Component.translatable("commands.sre.switchmap.random.error.no_maps"));
      return -1;
    }
  }

  public static int showCurrentMap(CommandSourceStack source) {
    ServerLevel serverWorld = source.getLevel();
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);

    source.sendSuccess(
        () -> Component.translatable("commands.sre.switchmap.current_map_info")
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    // 显示当前配置信息
    source.sendSuccess(
        () -> Component
            .literal("Map ID: " + areas.mapName)
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    // 显示当前配置信息
    source.sendSuccess(
        () -> Component
            .literal("Spawn Pos: " + areas.getSpawnPos().pos.x() + ", "
                + areas.getSpawnPos().pos.y() + ", "
                + areas.getSpawnPos().pos.z())
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);

    source.sendSuccess(
        () -> Component.literal("Room Count: " + areas.getRoomCount())
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Can Jump: " + (areas.areasSettings.canJump ? "Yes" : "No"))
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Can SimpleSwim: " + (areas.areasSettings.canSimpleSwim ? "Yes" : "No"))
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Outside Noise: " + (areas.areasSettings.haveOutsideSound ? "Yes" : "No"))
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Scene Outside Sound: " + areas.areasSettings.sceneOutsideSound.name())
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Reset must copy: " + (areas.areasSettings.mustCopy ? "Yes" : "No"))
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Shouldn't Reset: " + (areas.areasSettings.noReset ? "Yes" : "No"))
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Ready Area: [" +
            String.format("%.2f", areas.getReadyArea().minX) + ", " +
            String.format("%.2f", areas.getReadyArea().minY) + ", " +
            String.format("%.2f", areas.getReadyArea().minZ) + "] to [" +
            String.format("%.2f", areas.getReadyArea().maxX) + ", " +
            String.format("%.2f", areas.getReadyArea().maxY) + ", " +
            String.format("%.2f", areas.getReadyArea().maxZ) + "]")
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);

    source.sendSuccess(
        () -> Component.literal("Template Area: [" +
            String.format("%.2f", areas.getResetTemplateArea().minX) + ", " +
            String.format("%.2f", areas.getResetTemplateArea().minY) + ", " +
            String.format("%.2f", areas.getResetTemplateArea().minZ) + "] to [" +
            String.format("%.2f", areas.getResetTemplateArea().maxX) + ", " +
            String.format("%.2f", areas.getResetTemplateArea().maxY) + ", " +
            String.format("%.2f", areas.getResetTemplateArea().maxZ) + "]")
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Play Area: [" +
            String.format("%.2f", areas.getPlayArea().minX) + ", " +
            String.format("%.2f", areas.getPlayArea().minY) + ", " +
            String.format("%.2f", areas.getPlayArea().minZ) + "] to [" +
            String.format("%.2f", areas.getPlayArea().maxX) + ", " +
            String.format("%.2f", areas.getPlayArea().maxY) + ", " +
            String.format("%.2f", areas.getPlayArea().maxZ) + "]")
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    return 1;
  }
}