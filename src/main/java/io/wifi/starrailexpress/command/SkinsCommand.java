package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.network.OpenSkinScreenPaylod;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SkinsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:skins")
                        .executes(context -> execute(context.getSource(), null)) // 不指定玩家，默认自己
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2)) // 需要权限等级2来查看其他玩家
                                .executes(context -> execute(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private static int execute(CommandSourceStack source, ServerPlayer player)
            throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();

        if (player == null) {
            // 未指定玩家，打开自己的皮肤管理界面
            openSkinScreen(sender);
            source.sendSuccess(() -> Component.translatable("commands.sre.showskin.self"), false);
        } else {
            // 指定玩家，打开指定玩家的皮肤管理界面

            if (player != null) {
                openSkinScreen(player);
                source.sendSuccess(() -> Component.translatable("commands.sre.showskin.other", player.getDisplayName()),
                        false);
            }

        }
        return 1;
    }

    private static void openSkinScreen(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenSkinScreenPaylod());
    }
}