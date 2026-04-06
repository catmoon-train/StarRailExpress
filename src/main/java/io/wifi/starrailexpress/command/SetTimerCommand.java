package io.wifi.starrailexpress.command;

import org.agmas.harpymodloader.Harpymodloader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SetTimerCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("tmm:game")
            .requires(source -> source.hasPermission(2))
            .requires((t) -> Harpymodloader.isMojangVerify)
            .then(Commands.literal("time")
                .executes((context) -> {
                  return executeGetTime(context);
                })
                .then(Commands.literal("get")
                    .executes((context) -> {
                      return executeGetTime(context);
                    }))
                .then(Commands.literal("set").then(
                    Commands.argument("minutes", IntegerArgumentType.integer(0, 240))
                        .then(
                            Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                .executes(context -> setTimer(context.getSource(),
                                    IntegerArgumentType.getInteger(context, "minutes"),
                                    IntegerArgumentType.getInteger(context, "seconds"))))))));
  }

  private static int executeGetTime(CommandContext<CommandSourceStack> context) {
    var source = context.getSource();
    try {
      var gameTimeComponent = SREGameTimeComponent.KEY.get(source.getLevel());
      int leftTime = gameTimeComponent.getTime();
      float leftTimeSeconds = leftTime / 20;
      float leftTimeMinutes = leftTimeSeconds / 60;
      float leftTimeSeconds2 = leftTimeSeconds % 60;
      source.sendSuccess(() -> Component.translatable("Left Time: %s",
          String.format("%02.0f:%02.0f", leftTimeMinutes, leftTimeSeconds2))
          .withStyle(ChatFormatting.GREEN),
          false);
    } catch (Exception e) {
      e.printStackTrace();
      source.sendFailure(Component.literal("Error: ").append(e.getMessage()).withStyle(ChatFormatting.RED));
    }
    return 1;
  }

  private static int setTimer(CommandSourceStack source, int minutes, int seconds) {

    SREGameTimeComponent.KEY.get(source.getLevel()).setTime(GameConstants.getInTicks(minutes, seconds));
    source.sendSuccess(
        () -> Component.translatable("commands.sre.settimer", minutes, seconds)
            .withStyle(style -> style.withColor(0x00FF00)),
        true);
    return 1;
  }
}
