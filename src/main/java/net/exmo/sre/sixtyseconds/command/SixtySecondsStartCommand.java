package net.exmo.sre.sixtyseconds.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** {@code /sre:60s start [minutes]} 启动末日60秒模式（也可 {@code /tmm:start sre:sixty_seconds}）。 */
public final class SixtySecondsStartCommand {
    private SixtySecondsStartCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("sre:60s")
                        .then(literal("start")
                                .requires(source -> source.hasPermission(SREConfig.instance().startGameRequiredPermission))
                                .then(argument("minutes", IntegerArgumentType.integer(1))
                                        .executes(context -> start(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "minutes"))))
                                .executes(context -> start(context.getSource(), -1)))
                        // 自我解脱：san 归零变怪倒计时中，玩家可牺牲以换队伍安全（对所有玩家开放）
                        .then(literal("sacrifice").executes(context -> sacrifice(context.getSource())))
                        // 抵达幸存者阵营（触发点为后续设计；此命令供 OP 触发/测试）
                        .then(literal("reach_survivors")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> reachSurvivors(context.getSource())))));
    }

    private static int reachSurvivors(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !SixtySecondsMod.isActive(player.level())) {
            return 0;
        }
        return net.exmo.sre.sixtyseconds.logic.SixtySecondsWinConditions.reachSurvivorCamp(player) ? 1 : 0;
    }

    private static int sacrifice(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can sacrifice"));
            return 0;
        }
        if (!SixtySecondsMod.isActive(player.level())) {
            return 0;
        }
        return net.exmo.sre.sixtyseconds.logic.SixtySecondsMonsterSystem.sacrifice(player) ? 1 : 0;
    }

    private static int start(CommandSourceStack source, int minutes) {
        if (SixtySecondsMod.MODE == null) {
            source.sendFailure(Component.literal("60s mode not initialized"));
            return 0;
        }
        if (SREGameWorldComponent.KEY.get(source.getLevel()).isRunning()) {
            source.sendFailure(Component.translatable("game.start_error.game_running"));
            return 0;
        }
        int resolved = minutes >= 0 ? minutes : SixtySecondsMod.MODE.defaultStartTime;
        GameUtils.startGame(source.getLevel(), SixtySecondsMod.MODE, GameConstants.getInTicks(resolved, 0));
        source.sendSuccess(() -> Component.translatable("commands.sre.start",
                SixtySecondsMod.MODE.toString(), resolved).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
