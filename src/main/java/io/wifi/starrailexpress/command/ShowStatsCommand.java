package io.wifi.starrailexpress.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.network.ShowStatsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.UUID;

public class ShowStatsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:showStats")

                        .executes(context -> execute(context.getSource(), null)) // 不指定玩家，默认自己
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> execute(context.getSource(), GameProfileArgument.getGameProfiles(context, "player")))
                        )
        );
    }

    private static int execute(CommandSourceStack source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();

        if (profiles == null || profiles.isEmpty()) {
            // 未指定玩家，打开自己的统计界面
            openStatsScreen(sender, sender.getUUID());
            source.sendSuccess(() -> Component.translatable("commands.sre.showstats.self"), false);
        } else {
            // 指定玩家，打开指定玩家的统计界面
            for (GameProfile profile : profiles) {
                UUID targetUuid = profile.getId();
                ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayer(targetUuid);
                if (targetPlayer != null) {
                    openStatsScreen(targetPlayer, targetUuid);
                    source.sendSuccess(() -> Component.translatable("commands.sre.showstats.other", profile.getName()), false);
                } else {
                    source.sendFailure(Component.translatable("commands.sre.showstats.player_not_found", profile.getName()));
                }
            }
        }
        return 1;
    }

    private static void openStatsScreen(ServerPlayer player, UUID targetPlayerUuid) {
        SREPlayerStatsComponent.KEY.get( player).syncNow();
        ServerPlayNetworking.send(player, new ShowStatsPayload(targetPlayerUuid));
    }
}