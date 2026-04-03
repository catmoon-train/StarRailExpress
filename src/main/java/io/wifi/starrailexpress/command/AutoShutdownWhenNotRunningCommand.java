package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AutoShutdownWhenNotRunningCommand {
    public static boolean autoShutdownWhenGameNotRunning = false;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("stop_when_over")
                        .requires(t -> t.hasPermission(4))
                        .then(
                                Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> execute(context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")))));
    }

    private static int execute(CommandSourceStack source, boolean enabled) {
        autoShutdownWhenGameNotRunning = enabled;
        source.sendSuccess(
                () -> Component.literal("Auto stop server: " + (enabled ? "On" : "Off")),
                true);
        return 1;
    }
}