package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.command.argument.GameModeArgumentType;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;

import java.util.List;
import java.util.UUID;

public class StartCommand {
  public static LiteralCommandNode<CommandSourceStack> START_COMMAND_NODE;

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    START_COMMAND_NODE = dispatcher.register(
        Commands.literal("tmm:start")
            .requires(source -> source.hasPermission(SREConfig.instance().startGameRequiredPermission))
            .then(Commands.argument("gameMode", GameModeArgumentType.gameMode())
                // tmm:start <gameMode> [startTimeInMinutes] — 常规模式，仅准备区域内的玩家加入
                .then(Commands.argument("startTimeInMinutes", IntegerArgumentType.integer(1))
                    .executes(context -> execute(context.getSource(),
                        GameModeArgumentType.getGameModeArgument(context, "gameMode"),
                        IntegerArgumentType.getInteger(context, "startTimeInMinutes"),
                        false)))
                .then(Commands.literal("force_all_players")
                    .then(Commands.argument("startTimeInMinutes", IntegerArgumentType.integer(1))
                        .executes(context -> execute(context.getSource(),
                            GameModeArgumentType.getGameModeArgument(context, "gameMode"),
                            IntegerArgumentType.getInteger(context, "startTimeInMinutes"),
                            true)))
                    .executes(context -> {
                      GameMode gameMode = GameModeArgumentType.getGameModeArgument(context, "gameMode");
                      return execute(context.getSource(), gameMode, -1, true);
                    }))
                .executes(context -> {
                  GameMode gameMode = GameModeArgumentType.getGameModeArgument(context, "gameMode");
                  return execute(context.getSource(), gameMode, -1, false);
                })));
  }

  private static int execute(CommandSourceStack source, GameMode gameMode, int minutes, boolean forceAll) {
    if (SREGameWorldComponent.KEY.get(source.getLevel()).isRunning()) {
      source.sendFailure(Component.translatable("game.start_error.game_running"));
      return -1;
    }

    if (gameMode == SREGameModes.REPAIR_ESCAPE_MODE) {
      // 节约写入玩家NBT带来的网络消耗，所以需要手动启用才会进行同步。
      // 否则禁止游戏。
      if (!SREConfig.instance().enableRepairMode) {
        source.sendFailure(Component.translatable("game.start_error.disabled_gamemode", gameMode.getName(),
            Component.translatable("text.autoconfig.starrailexpress.option.enableRepairMode"), "enableRepairMode",
            "true"));
        return -1;
      }
    }
    // 检查当前地图是否支持该游戏模式
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    if (gameMode != SREGameModes.REPAIR_ESCAPE_MODE && areas.gameModes != null && !areas.gameModes.isEmpty()) {
      String modeId = gameMode.identifier.getPath();
      boolean isSupported = areas.gameModes.contains(modeId);
      if (!isSupported) {
        source.sendFailure(Component.translatable("commands.sre.start.error.map_not_supported",
            gameMode.getName(), areas.mapName != null ? areas.mapName : "unknown"));
        return -1;
      }
    }
    int startMinutes = minutes;
    if (gameMode == SREGameModes.FOURTH_ROOM) {
      int requestedPlayers = minutes >= 0 ? minutes : 8;
      FourthRoomGameManager.setRequestedPlayerCount(source.getLevel(), requestedPlayers);
      startMinutes = gameMode.defaultStartTime;
    }

    // forceAll: 将所有参与中的玩家（未准备/opt-out 的除外）强制纳入就绪列表，
    // 使其无论身处何地都能加入游戏
    if (forceAll) {
      ParticipationComponent participation = ParticipationComponent.KEY.get(source.getLevel());
      List<ServerPlayer> allPlayers = source.getLevel().getServer().getPlayerList().getPlayers();
      List<UUID> forcedReady = allPlayers.stream()
          .filter(participation::isParticipating)
          .map(ServerPlayer::getUUID)
          .toList();
      GameUtils.setForcedReadyPlayers(forcedReady);
    }

    final int resolvedStartMinutes = startMinutes;
    if (gameMode != SREGameModes.MURDER) {
      if (!Harpymodloader.officialVerify) {
        source.sendFailure(Component.translatable("game.start_error.credit"));
        return 0;
      }
    }
    if (gameMode == SREGameModes.LOOSE_ENDS) {
      GameUtils.startGame(source.getLevel(), gameMode,
          GameConstants.getInTicks(resolvedStartMinutes >= 0 ? resolvedStartMinutes : gameMode.defaultStartTime, 0));
      source.sendSuccess(
          () -> Component.translatable("commands.sre.start", gameMode.toString(), resolvedStartMinutes)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
      return 1;
    } else {
      GameUtils.startGame(source.getLevel(), gameMode,
          GameConstants.getInTicks(resolvedStartMinutes >= 0 ? resolvedStartMinutes : gameMode.defaultStartTime, 0));
      source.sendSuccess(
          () -> Component.translatable("commands.sre.start", gameMode.toString(), resolvedStartMinutes)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
      return 1;
    }
  }
}