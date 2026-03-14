package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class StopCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:stop")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("force").executes(context -> {
                            GameUtils.finalizeGame(context.getSource().getLevel());
                            return 1;
                        }
                ))
                .executes(context -> {
                    GameUtils.stopGame(context.getSource().getLevel());
                    context.getSource().sendSuccess(
                            () -> Component.translatable("commands.sre.stop")
                                    .withStyle(style -> style.withColor(0x00FF00)),
                            true
                    );
                    return 1;
                })
        );
    }
}