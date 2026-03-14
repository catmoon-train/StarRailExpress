package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.data.ServerMapConfig;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.ShowSelectedMapUIPayload;
import io.wifi.starrailexpress.voting.MapVotingManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

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
                                                "time")))));
    }

    private static int startVoting(CommandSourceStack source, int time) {
        if (GameUtils.isStartingGame) {
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

        votingManager.startVoting(time);
        String mapconfigs = ShowSelectedMapUIPayload
                .convertServerMapConfigToString(ServerMapConfig.getInstance(source.getServer()));

        source.getServer().getPlayerList().getPlayers().forEach(
                serverPlayer -> {
                    ServerPlayNetworking.send(serverPlayer,
                            new ShowSelectedMapUIPayload(mapconfigs));
                });
        source.sendSuccess(() -> Component.translatable("command.sre.votemap.success"), false);

        return 1;
    }
}