package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;
import org.jetbrains.annotations.NotNull;

public class StopCommand {
    public static LiteralCommandNode<CommandSourceStack> STOP_COMMAND_NODE;

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        STOP_COMMAND_NODE = dispatcher.register(Commands.literal("tmm:stop")
                .requires(source -> Harpymodloader.officialVerify
                        && source.hasPermission(SREConfig.instance().stopGameRequiredPermission))
                .then(Commands.literal("force").executes(context -> {
                    GameUtils.finalizeGame(context.getSource().getLevel());
                    return 1;
                }))
                .executes(context -> {
                    GameUtils.stopGame(context.getSource().getLevel());
                    context.getSource().sendSuccess(
                            () -> Component.translatable("commands.sre.stop")
                                    .withStyle(style -> style.withColor(0x00FF00)),
                            true);
                    return 1;
                }));
    }
}