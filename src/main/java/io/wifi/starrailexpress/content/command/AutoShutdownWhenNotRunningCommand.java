package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

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
    ServerLevel level = source.getLevel();
    if (!SREGameWorldComponent.KEY.get(level).isRunning()){
      level.getServer().halt(false);
    }
    source.sendSuccess(
        () -> Component.literal("Auto stop server: " + (enabled ? "On" : "Off")),
        true);
    return 1;
  }
}