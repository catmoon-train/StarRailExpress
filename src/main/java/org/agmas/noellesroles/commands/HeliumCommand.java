package org.agmas.noellesroles.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.voice.HeliumBuzzPlayerComponent;

public class HeliumCommand {
  public static void register() {
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
        dispatcher.register(Commands.literal("sre:helium").requires(source -> source.hasPermission(2))
          .then(Commands.argument("target", EntityArgument.player())
            .executes(HeliumCommand::executeWithTarget)
            .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
              .executes(ctx -> executeWithTargetAndSeconds(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))));
    });
  }

  private static int executeWithTarget(CommandContext<CommandSourceStack> context) {
    try {
      ServerPlayer target = EntityArgument.getPlayer(context, "target");
      return execute(context, target, 240); // 默认 240 秒（4 分钟）
    } catch (CommandSyntaxException e) {
      context.getSource().sendFailure(Component.literal("ERROR: " + e.getMessage()));
      e.printStackTrace();
    }
    return 0;
  }

  private static int executeWithTargetAndSeconds(CommandContext<CommandSourceStack> context, int seconds) {
    try {
      ServerPlayer target = EntityArgument.getPlayer(context, "target");
      return execute(context, target, seconds);
    } catch (CommandSyntaxException e) {
      context.getSource().sendFailure(Component.literal("ERROR: " + e.getMessage()));
      e.printStackTrace();
    }
    return 0;
  }

  private static int execute(CommandContext<CommandSourceStack> context, ServerPlayer target, int seconds) {
    if (target == null)
      return 0;
    try {
      HeliumBuzzPlayerComponent comp = HeliumBuzzPlayerComponent.KEY.get(target);
      comp.apply(seconds * 20, 1);  // ticks = seconds * 20, amplifier = 1
      Component MSG = Component.translatable("message.noellesroles.helium.applied", target.getDisplayName(), seconds)
          .withStyle(ChatFormatting.YELLOW);
      context.getSource().sendSuccess(() -> MSG, false);
      return Command.SINGLE_SUCCESS;
    } catch (Exception e) {
      context.getSource().sendFailure(Component.literal("ERROR: " + e.getMessage()));
      return 0;
    }
  }
}
