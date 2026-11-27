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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.command.argument.GameModeArgumentType;
import io.wifi.starrailexpress.content.vote.RolePoolVote;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.voting.MapVotingManager;
import io.wifi.starrailexpress.roster.pool.RolePool;
import io.wifi.starrailexpress.roster.pool.RolePoolRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.stream.Collectors;

public class MapVoteCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("tmm:votemap")
            .requires(source -> source.hasPermission(2))
            .executes(context -> startVoting(context.getSource(), 20 * 20)) // 默认20秒
            // 只开职业池投票，不接地图投票（管理员单独定池子用）
            .then(Commands.literal("pool")
                .executes(context -> startPoolVote(context.getSource(), 20 * 20)))
            // 跳过职业池阶段，直接开地图投票
            .then(Commands.literal("nopool")
                .executes(context -> startVotingDirect(context.getSource(), 20 * 20))
                .then(Commands.argument("time",
                    IntegerArgumentType.integer(2 * 20, 300 * 20))
                    .executes(context -> startVotingDirect(context.getSource(),
                        IntegerArgumentType.getInteger(context, "time")))))
            .then(Commands.argument("time",
                IntegerArgumentType.integer(2 * 20, 300 * 20)) // 时间范围10-300秒
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

    dispatcher.register(
        Commands.literal("tmm:rolepool")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("list")
                .executes(context -> listPools(context.getSource())))
            .then(Commands.literal("apply")
                .then(Commands.argument("pool", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider
                        .suggest(RolePoolRegistry.all().stream().map(RolePool::id).toList(), builder))
                    .executes(context -> applyPool(context.getSource(),
                        StringArgumentType.getString(context, "pool")))))
            .then(Commands.literal("vote")
                .executes(context -> startPoolVote(context.getSource(), 20 * 20))));
  }

  /** 职业池投票阶段（结束后自动接地图投票）。 */
  private static int startPoolVote(CommandSourceStack source, int time) {
    if (RolePoolVote.isRunning()) {
      source.sendFailure(Component.translatable("command.sre.rolepool.vote_running"));
      return 0;
    }
    if (RolePoolVote.start(time, null)) {
      source.sendSuccess(() -> Component.translatable("command.sre.rolepool.vote_started"), true);
      return 1;
    }
    source.sendFailure(Component.translatable("command.sre.rolepool.vote_failed"));
    return 0;
  }

  /** 直接开地图投票（不经过职业池投票）。 */
  private static int startVotingDirect(CommandSourceStack source, int time) {
    if (AutoShutdownWhenNotRunningCommand.autoShutdownWhenGameNotRunning || GameUtils.isStartingGame) {
      source.sendFailure(Component.literal("Game is starting! You cannot open map voting screen!"));
      return 0;
    }
    SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(source.getLevel());
    if (gameWorldComponent.isRunning()) {
      source.sendFailure(Component.literal("Game has started! You cannot open map voting screen!"));
      return 0;
    }
    MapVotingManager.getInstance().startVotingDirect(time);
    source.sendSuccess(() -> Component.translatable("command.sre.votemap.success"), false);
    return 1;
  }

  /** 列出全部职业池。 */
  private static int listPools(CommandSourceStack source) {
    List<RolePool> pools = RolePoolRegistry.all();
    if (pools.isEmpty()) {
      source.sendFailure(Component.translatable("command.sre.rolepool.empty"));
      return 0;
    }
    for (RolePool pool : pools) {
      int count = RolePoolRegistry.rolesOf(pool).size();
      source.sendSuccess(() -> Component.translatable("command.sre.rolepool.list_entry",
          pool.id(), pool.displayName(), count), false);
    }
    return pools.size();
  }

  /** 直接把某个池子写进职业轮换名单（不投票）。 */
  private static int applyPool(CommandSourceStack source, String poolId) {
    RolePool pool = RolePoolRegistry.byId(poolId);
    if (pool == null) {
      source.sendFailure(Component.translatable("command.sre.rolepool.unknown", poolId));
      return 0;
    }
    int count = RolePoolRegistry.applyToRoster(List.of(pool));
    source.sendSuccess(() -> Component.translatable("command.sre.rolepool.applied",
        pool.displayName(), count), true);
    return 1;
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

    if (RolePoolVote.isRunning()) {
      source.sendFailure(Component.translatable("command.sre.rolepool.vote_running"));
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