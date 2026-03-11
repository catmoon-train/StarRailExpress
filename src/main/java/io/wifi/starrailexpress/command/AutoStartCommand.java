package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import io.wifi.starrailexpress.cca.AutoStartComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.SRE;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AutoStartCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:autoStart")
                        .requires(source -> source.hasPermission(2))
                        .then(
                                Commands.argument("seconds", IntegerArgumentType.integer(0, 60))
                                        .executes(context -> setAutoStart(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))
                        )
        );
    }

    private static int setAutoStart(CommandSourceStack source, int seconds) {
        return SRE.executeSupporterCommand(source,
                () -> {
                    AutoStartComponent.KEY.get(source.getLevel()).setStartTime(GameConstants.getInTicks(0, seconds));
                    if (seconds > 0) {
                        source.sendSuccess(
                            () -> Component.translatable("commands.sre.autostart.enabled", seconds)
                                .withStyle(style -> style.withColor(0x00FF00)),
                            true
                        );
                    } else {
                        source.sendSuccess(
                            () -> Component.translatable("commands.sre.autostart.disabled")
                                .withStyle(style -> style.withColor(0x00FF00)),
                            true
                        );
                    }
                }
        );
    }
}
