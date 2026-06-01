package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ParticipationCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:participate")
                .executes(ParticipationCommand::toggle)
                .then(Commands.literal("join").executes(context -> setParticipating(context, true)))
                .then(Commands.literal("leave").executes(context -> setParticipating(context, false)))
                .then(Commands.literal("status").executes(ParticipationCommand::status)));
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            return 0;
        }
        boolean participating = ParticipationComponent.KEY.get(player.level()).toggleParticipating(player);
        sendStatus(context, player, participating);
        return 1;
    }

    private static int setParticipating(CommandContext<CommandSourceStack> context, boolean participating) {
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            return 0;
        }
        ParticipationComponent.KEY.get(player.level()).setParticipating(player.getUUID(), participating);
        sendStatus(context, player, participating);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        if (player == null) {
            return 0;
        }
        boolean participating = ParticipationComponent.KEY.get(player.level()).isParticipating(player);
        sendStatus(context, player, participating);
        return 1;
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("commands.sre.participation.player_only"));
        }
        return player;
    }

    private static void sendStatus(CommandContext<CommandSourceStack> context, ServerPlayer player,
            boolean participating) {
        boolean running = SREGameWorldComponent.KEY.get(player.level()).isRunning();
        String key = participating ? "commands.sre.participation.joined" : "commands.sre.participation.left";
        context.getSource().sendSuccess(() -> Component.translatable(key), false);
        if (running) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.sre.participation.next_round"), false);
        }
    }
}
