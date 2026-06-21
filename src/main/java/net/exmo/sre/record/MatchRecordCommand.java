package net.exmo.sre.record;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 全局战绩查询命令：
 * <ul>
 *     <li>{@code /sre:records} —— 打开战绩列表 GUI</li>
 *     <li>{@code /sre:records <id>} —— 直接打开某一场回放</li>
 * </ul>
 */
public final class MatchRecordCommand {

    private MatchRecordCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:records")
                .executes(context -> openList(context.getSource()))
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(context -> openReplay(context.getSource(),
                                StringArgumentType.getString(context, "id")))));
    }

    private static int openList(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!MatchRecordStore.isAvailable()) {
            source.sendFailure(Component.translatable("commands.sre.records.unavailable"));
            return 0;
        }
        MatchRecordService.openListFor(player, 0);
        source.sendSuccess(() -> Component.translatable("commands.sre.records.opening_list"), false);
        return 1;
    }

    private static int openReplay(CommandSourceStack source, String matchId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!MatchRecordStore.isAvailable()) {
            source.sendFailure(Component.translatable("commands.sre.records.unavailable"));
            return 0;
        }
        MatchRecordService.openReplayFor(player, matchId);
        source.sendSuccess(() -> Component.translatable("commands.sre.records.opening_replay", matchId), false);
        return 1;
    }
}
