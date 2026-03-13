package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.command.argument.MapLoadArgumentType;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.game.MapResetManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public class SwitchMapCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("tmm:switchmap")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("scan")
                .executes(SwitchMapCommand::executeScan))
            .then(Commands.literal("load")
                .then(Commands.argument("mapName",
                    MapLoadArgumentType.string())
                    .executes(context -> executeLoad(
                        context.getSource(),
                        StringArgumentType
                            .getString(context,
                                "mapName")))))
            .then(Commands.literal("save")
                .then(Commands.argument("mapName",
                    MapLoadArgumentType.string())
                    .executes(context -> executeSave(
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
            }));
  }

  private static int executeScan(CommandContext<CommandSourceStack> context) {
    try {
      ServerLevel serverLevel = context.getSource().getLevel();
      AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverLevel);
      MapResetManager.scanArea(serverLevel, areas);
      MapResetManager.saveArea(serverLevel);
      context.getSource().sendSuccess(() -> Component.literal(
          "Scanned Successfully! Found " + GameFunctions.resetPoints.size() + " blocks should be reseted!"), true);
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
    StarGameWorldComponent gameComponent = StarGameWorldComponent.KEY.get(serverWorld);
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

  private static int executeSave(CommandSourceStack source, String mapName) {
    ServerLevel serverWorld = source.getLevel();

    // 检查游戏是否正在运行
    StarGameWorldComponent gameComponent = StarGameWorldComponent.KEY.get(serverWorld);
    if (gameComponent.isRunning()) {
      source.sendFailure(Component.translatable("commands.sre.switchmap.error.game_running"));
      return -1;
    }

    // 保存当前地图配置
    if (MapManager.saveCurrentMap(serverWorld, mapName)) {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.switchmap.save.success", mapName)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
      return 1;
    } else {
      source.sendFailure(Component.translatable("commands.sre.switchmap.error.save_failed", mapName));
      return -1;
    }
  }

  private static int executeList(CommandSourceStack source) {
    ServerLevel serverWorld = source.getLevel();
    List<String> availableMaps = MapManager.getAvailableMaps(serverWorld);

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
            () -> Component.literal(" - " + mapName)
                .withStyle(style -> style.withColor(0xFFFFFF)),
            false);
      }
    }

    return 1;
  }

  private static int executeRandom(CommandSourceStack source) {
    ServerLevel serverWorld = source.getLevel();

    // 检查游戏是否正在运行
    StarGameWorldComponent gameComponent = StarGameWorldComponent.KEY.get(serverWorld);
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

  private static int showCurrentMap(CommandSourceStack source) {
    ServerLevel serverWorld = source.getLevel();
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);

    source.sendSuccess(
        () -> Component.translatable("commands.sre.switchmap.current_map_info")
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
        () -> Component.literal("Can Jump: " + (areas.canJump ? "Yes" : "No"))
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Can Swim: " + (areas.canSwim ? "Yes" : "No"))
            .withStyle(style -> style.withColor(0x00FFFF)),
        false);
    source.sendSuccess(
        () -> Component.literal("Shouldn't Reset: " + (areas.noReset ? "Yes" : "No"))
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

    return 1;
  }
}