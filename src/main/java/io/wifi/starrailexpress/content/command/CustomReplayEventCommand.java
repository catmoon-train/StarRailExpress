package io.wifi.starrailexpress.content.command;

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
        Commands.literal("sre:custom_replay")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("record")
                .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                    .executes(ctx -> execute(ctx, false))))
            .then(Commands.literal("record_hidden")
                .then(Commands.argument("message", ComponentArgument.textComponent(registryAccess))
                    .executes(ctx -> execute(ctx, true)))));
    dispatcher.register(
        Commands.literal("sre:show_replay")
            .requires(source -> source.hasPermission(2))
            .executes(CustomReplayEventCommand::executeShow));
  }

  private static int executeShow(CommandContext<CommandSourceStack> ctx) {
    ServerPlayer serverPlayer = ctx.getSource().getPlayer();
    var replay = SRE.REPLAY_MANAGER.generateReplay();
    serverPlayer.sendSystemMessage(replay);
    ctx.getSource().sendSuccess(() -> Component.translatable("Showing replay to %s.", serverPlayer.getName()), true);
    return 1;
  }

  private static int execute(CommandContext<CommandSourceStack> ctx, boolean hidden) {
    ServerPlayer serverPlayer = ctx.getSource().getPlayer();
    Component res = ComponentArgument.getComponent(ctx, "message");
    if (serverPlayer != null) {
      try {
        res = ComponentUtils.updateForEntity(
            (CommandSourceStack) ctx.getSource(),
            res,
            serverPlayer, 0);
      } catch (CommandSyntaxException e) {
        e.printStackTrace();
        ctx.getSource().sendFailure(Component.literal("ERROR: " + e.getMessage()));
        return 0;
      }
    } else {
    }
    Component result = SRE.REPLAY_MANAGER.recordCustomEvent(res, hidden);
    ctx.getSource().sendSuccess(() -> Component.literal("Successfully record custom event!"), true);
    ctx.getSource().sendSystemMessage(Component.literal("[ADD REPLAY] ").append(result));
    return 1;
  }
}
