package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;

import io.wifi.starrailexpress.cca.ScoreboardRoleSelectorComponent;
import io.wifi.starrailexpress.SRE;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ForceRoleCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:forceRole").requires(source -> source.hasPermission(2))
                .then(Commands.literal("killer").then(Commands.argument("players", EntityArgument.players())
                        .executes(context -> forceKiller(context.getSource(), EntityArgument.getPlayers(context, "players")))
                )).then(Commands.literal("vigilante").then(Commands.argument("players", EntityArgument.players())
                        .executes(context -> forceVigilante(context.getSource(), EntityArgument.getPlayers(context, "players")))
                ))
        );
    }

    private static int forceKiller(@NotNull CommandSourceStack source, @NotNull Collection<ServerPlayer> players) {
        return SRE.executeSupporterCommand(source,
                () -> {
                    ScoreboardRoleSelectorComponent component = ScoreboardRoleSelectorComponent.KEY.get(source.getServer().getScoreboard());
                    component.forcedKillers.clear();
                    for (var player : players) component.forcedKillers.add(player.getUUID());
                    
                    if (players.size() == 1) {
                        ServerPlayer player = players.iterator().next();
                        source.sendSuccess(
                            () -> Component.translatable("commands.sre.forcerole.killer", player.getName().getString())
                                .withStyle(style -> style.withColor(0x00FF00)),
                            true
                        );
                    } else {
                        source.sendSuccess(
                            () -> Component.translatable("commands.sre.forcerole.killer.multiple", players.size())
                                .withStyle(style -> style.withColor(0x00FF00)),
                            true
                        );
                    }
                }
        );
    }

    private static int forceVigilante(@NotNull CommandSourceStack source, @NotNull Collection<ServerPlayer> players) {
        return SRE.executeSupporterCommand(source,
                () -> {
                    ScoreboardRoleSelectorComponent component = ScoreboardRoleSelectorComponent.KEY.get(source.getServer().getScoreboard());
                    component.forcedVigilantes.clear();
                    for (var player : players) component.forcedVigilantes.add(player.getUUID());
                    
                    if (players.size() == 1) {
                        ServerPlayer player = players.iterator().next();
                        source.sendSuccess(
                            () -> Component.translatable("commands.sre.forcerole.vigilante", player.getName().getString())
                                .withStyle(style -> style.withColor(0x00FF00)),
                            true
                        );
                    } else {
                        source.sendSuccess(
                            () -> Component.translatable("commands.sre.forcerole.vigilante.multiple", players.size())
                                .withStyle(style -> style.withColor(0x00FF00)),
                            true
                        );
                    }
                }
        );
    }
}