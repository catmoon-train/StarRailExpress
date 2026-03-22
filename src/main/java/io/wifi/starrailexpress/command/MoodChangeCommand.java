package io.wifi.starrailexpress.command;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class MoodChangeCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("tmm:mood")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("get").executes(context -> executeGet(context, context.getSource().getPlayer()))
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(context -> executeGet(context, EntityArgument.getPlayer(context, "target")))))
            .then(Commands.literal("set")
                .then(
                    Commands.argument("mood", FloatArgumentType.floatArg(0.0f, 1.0f))
                        .executes(context -> execute(context.getSource(),
                            ImmutableList.of(context.getSource().getEntityOrException()),
                            FloatArgumentType.getFloat(context, "mood")))
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> execute(context.getSource(),
                                    EntityArgument.getEntities(context, "targets"),
                                    FloatArgumentType.getFloat(context, "mood")))))));
  }

  private static int executeGet(CommandContext<CommandSourceStack> context, ServerPlayer player) {
    var source = context.getSource();
    if (player == null) {
      source.sendFailure(Component.literal("Not a player!"));
      return 0;
    }
    var pmc = SREPlayerMoodComponent.KEY.get(player);
    source.sendSuccess(() -> Component.translatable("Mood of %s: %s", player.getDisplayName(), pmc.getMood()), false);
    return 1;
  }

  private static int execute(CommandSourceStack source, Collection<? extends Entity> targets, float mood) {

    for (Entity target : targets) {
      SREPlayerMoodComponent moodComponent = SREPlayerMoodComponent.KEY.get(target);
      moodComponent.setMood(mood);
    }

    if (targets.size() == 1) {
      Entity target = targets.iterator().next();
      source.sendSuccess(
          () -> Component
              .translatable("commands.sre.setmood", target.getName().getString(),
                  String.format("%.2f", mood))
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    } else {
      source.sendSuccess(
          () -> Component
              .translatable("commands.sre.setmood.multiple", targets.size(), String.format("%.2f", mood))
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    }
    return 1;
  }
}