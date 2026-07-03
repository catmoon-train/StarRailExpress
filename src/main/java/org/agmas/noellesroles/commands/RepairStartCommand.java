package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RepairStartCommand {
    private RepairStartCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("cy:repair")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("start")
                                .then(argument("minutes", IntegerArgumentType.integer(1))
                                        .executes(context -> start(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "minutes"))))
                                .executes(context -> start(context.getSource(), -1)))));
    }

    private static int start(net.minecraft.commands.CommandSourceStack source, int minutes) {
        if (SREGameWorldComponent.KEY.get(source.getLevel()).isRunning()) {
            source.sendFailure(Component.translatable("game.start_error.game_running"));
            return 0;
        }
        int resolved = minutes >= 0 ? minutes : SREGameModes.REPAIR_ESCAPE_MODE.defaultStartTime;
        GameUtils.startGame(source.getLevel(), SREGameModes.REPAIR_ESCAPE_MODE, GameConstants.getInTicks(resolved, 0));
        source.sendSuccess(() -> Component.translatable("commands.sre.start", SREGameModes.REPAIR_ESCAPE_MODE.toString(), resolved)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
