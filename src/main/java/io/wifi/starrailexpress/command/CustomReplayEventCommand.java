package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.SRE;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

public class CustomReplayEventCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
    dispatcher.register(
        Commands.literal("tmm:custom_replay")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("record")
                .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                    .executes(CustomReplayEventCommand::execute))));
  }

  private static int execute(CommandContext<CommandSourceStack> commandContext) {
    ServerPlayer serverPlayer = commandContext.getSource().getPlayer();
    Component res = ComponentArgument.getComponent(commandContext, "message");
    if (serverPlayer != null) {
      try {
        res = ComponentUtils.updateForEntity(
            (CommandSourceStack) commandContext.getSource(),
            res,
            serverPlayer, 0);
      } catch (CommandSyntaxException e) {
        e.printStackTrace();
        commandContext.getSource().sendFailure(Component.literal("ERROR: " + e.getMessage()));
        return 0;
      }
    } else {
    }
    Component result = SRE.REPLAY_MANAGER.recordCustomEvent(res);
    commandContext.getSource().sendSuccess(() -> Component.literal("Successfully record custom event!"), true);
    commandContext.getSource().sendSystemMessage(Component.literal("[ADD REPLAY] ").append(result));
    return 1;
  }
}