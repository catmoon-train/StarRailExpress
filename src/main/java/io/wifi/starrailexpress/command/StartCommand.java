package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.command.argument.GameModeArgumentType;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class StartCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:start")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("gameMode", GameModeArgumentType.gameMode())
                                .then(Commands.argument("startTimeInMinutes", IntegerArgumentType.integer(1))
                                        .executes(context -> execute(context.getSource(),
                                                GameModeArgumentType.getGameModeArgument(context, "gameMode"),
                                                IntegerArgumentType.getInteger(context, "startTimeInMinutes"))))
                                .executes(context -> {
                                    GameMode gameMode = GameModeArgumentType.getGameModeArgument(context, "gameMode");
                                    return execute(context.getSource(), gameMode, -1);
                                })));
    }

    private static int execute(CommandSourceStack source, GameMode gameMode, int minutes) {
        if (SREGameWorldComponent.KEY.get(source.getLevel()).isRunning()) {
            source.sendFailure(Component.translatable("game.start_error.game_running"));
            return -1;
        }
        if (gameMode == SREGameModes.LOOSE_ENDS) {

            GameUtils.startGame(source.getLevel(), gameMode,
                    GameConstants.getInTicks(minutes >= 0 ? minutes : gameMode.defaultStartTime, 0));
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.start", gameMode.toString(), minutes)
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
            return 1;
        } else {
            GameUtils.startGame(source.getLevel(), gameMode,
                    GameConstants.getInTicks(minutes >= 0 ? minutes : gameMode.defaultStartTime, 0));
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.start", gameMode.toString(), minutes)
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
            return 1;
        }
    }
}