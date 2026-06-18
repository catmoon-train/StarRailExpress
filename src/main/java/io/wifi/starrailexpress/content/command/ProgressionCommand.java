package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.progression.ProgressionDataManager;
import io.wifi.starrailexpress.progression.ProgressionState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ProgressionCommand {
    private ProgressionCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:pass")
                .then(Commands.literal("activate")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(context -> activate(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "type"))))));
    }

    private static int activate(ServerPlayer player, String rawType) {
        ProgressionState.FactionCardType type = ProgressionState.FactionCardType.fromString(rawType);
        if (!ProgressionDataManager.activateFactionCard(player, type)) {
            player.displayClientMessage(Component.translatable("message.sre.pass.faction.assign_failed"), true);
            return 0;
        }
        return 1;
    }
}
