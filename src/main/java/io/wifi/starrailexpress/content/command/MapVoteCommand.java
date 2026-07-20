package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.command.argument.GameModeArgumentType;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.voting.MapVotingManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.stream.Collectors;

public class MapVoteCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("tmm:votemap")
            .requires(source -> source.hasPermission(2))
            .executes(context -> startVoting(context.getSource(), 60 * 20)) // 默认60秒
            .then(Commands.argument("time",
                IntegerArgumentType.integer(10 * 20, 300 * 20)) // 时间范围10-300秒
                .executes(context -> startVoting(context.getSource(),
                    IntegerArgumentType.getInteger(context,
                        "time"))))
            .then(Commands.literal("status")
                .executes(context -> getVotingStatus(context.getSource())))
            .then(Commands.literal("pause")
                .executes(context -> pauseVoting(context.getSource())))
            .then(Commands.literal("resume")
                .executes(context -> resumeVoting(context.getSource())))
            .then(Commands.literal("stop")
                .executes(context -> stopVoting(context.getSource())))
            .then(Commands.literal("setmode")
                .then(Commands.argument("mode", GameModeArgumentType.gameMode())
                    .executes(context -> {
                      var modeId = context.getArgument("mode",
                          net.minecraft.resources.ResourceLocation.class);
                      return setPresetGameMode(context.getSource(), modeId.getPath());
                    }))));
  }

  private static int startVoting(CommandSourceStack source, int time) {
    if (AutoShutdownWhenNotRunningCommand.autoShutdownWhenGameNotRunning || GameUtils.isStartingGame) {
      source.sendFailure(Component.literal("Game is starting! You cannot open map voting screen!"));
      return 0;
    }
    SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(source.getLevel());
    if (gameWorldComponent.isRunning()) {
      source.sendFailure(Component.literal("Game has started! You cannot open map voting screen!"));
      return 0;
    }
    MapVotingManager votingManager = MapVotingManager.getInstance();

    if (votingManager.isVotingActive()) {
      source.sendFailure(Component.translatable("command.sre.votemap.already_running"));
      return 0;
    }

    if (votingManager.isVotingSystemPaused()) {
      source.sendFailure(Component.translatable("command.sre.votemap.paused"));
      return 0;
    }
    votingManager.startVoting(time);
    source.sendSuccess(() -> Component.translatable("command.sre.votemap.success"), false);

    return 1;
  }

  private static int getVotingStatus(CommandSourceStack source) {
    MapVotingManager votingManager = MapVotingManager.getInstance();

    if (votingManager.isVotingPaused()) {
      source.sendSuccess(() -> Component.literal("Map Vote Status: Paused."), false);
      return 2;
    }
    if (votingManager.isVotingActive()) {
      source.sendSuccess(() -> Component.literal("Map Vote Status: Running."), false);
      return 1;
    }
    source.sendSuccess(() -> Component.literal("Map Vote Status: Inactive."), false);
    return 0;
  }

  private static int pauseVoting(CommandSourceStack source) {
    MapVotingManager votingManager = MapVotingManager.getInstance();

    if (votingManager.isVotingSystemPaused()) {
      source.sendFailure(Component.literal("Voting system has already been paused."));
      return 0;
    }

    votingManager.setVotingSystemPaused(true);
    source.sendSuccess(() -> Component.literal("Voting system was paused successfully."), true);
    return 1;
  }

  private static int resumeVoting(CommandSourceStack source) {
    MapVotingManager votingManager = MapVotingManager.getInstance();

    if (!votingManager.isVotingSystemPaused()) {
      source.sendFailure(Component.literal("Voting system hasn't been paused."));
      return 0;
    }

    votingManager.setVotingSystemPaused(false);
    source.sendSuccess(() -> Component.literal("Voting system has been resumed successfully."), true);
    return 1;
  }

  private static int stopVoting(CommandSourceStack source) {
    MapVotingManager votingManager = MapVotingManager.getInstance();

    if (!votingManager.isVotingActive()) {
      source.sendFailure(Component.literal("No active voting!"));
      return 0;
    }

    votingManager.stopVoting();
    source.sendSuccess(() -> Component.literal("Vote Stop!!"), true);
    return 1;
  }

  private static int setPresetGameMode(CommandSourceStack source, String gameMode) {
    MapVotingManager votingManager = MapVotingManager.getInstance();

    // 验证游戏模式是否存在
    if (!votingManager.isValidGameMode(gameMode)) {
      String availableModes = SREGameModes.GAME_MODES.keySet().stream().map(ResourceLocation::getPath)
          .collect(Collectors.joining(", "));
      source.sendFailure(Component.translatable("command.sre.votemap.setmode.invalid", gameMode, availableModes));
      return 0;
    }

    votingManager.setPresetGameMode(gameMode);
    Component localizedModeName = getLocalizedGameModeName(gameMode);
    source.sendSuccess(() -> Component.translatable("command.sre.votemap.setmode.success", localizedModeName),
        true);
    return 1;
  }

  public static Component getLocalizedGameModeName(String gameModeId) {
    // 根据游戏模式ID返回本地化的名称
    return Component.translatableWithFallback("hud.sre.tip.gamemode." + gameModeId, gameModeId);
  }
}