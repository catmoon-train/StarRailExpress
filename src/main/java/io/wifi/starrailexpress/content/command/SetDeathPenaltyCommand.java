package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.noellesroles.component.DeathPenaltyComponent;

public class SetDeathPenaltyCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("tmm:game")
            .requires(source -> Harpymodloader.officialVerify && source.hasPermission(2))
            .then(Commands.literal("penalty")
                .requires(source -> source.isPlayer())
                .then(Commands.literal("stop").executes((ctx) -> {
                  CommandSourceStack source = ctx.getSource();
                  if (source.getPlayer() == null) {

                    source.sendFailure(Component.literal("This command can only be executed by player."));
                    return 0;
                  }
                  ServerPlayer player = source.getPlayer();
                  DeathPenaltyComponent.KEY.get(player).init();
                  source.sendSuccess(
                      () -> Component.translatable("Stop Penalty for %s", player.getName()), true);
                  return 1;
                }))
                .then(Commands.literal("start")
                    .then(Commands.argument("time", IntegerArgumentType.integer(-1))
                        .then(Commands.argument("after_detection", BoolArgumentType.bool())
                            .then(Commands.literal("normal").executes((ctx) -> {
                              CommandSourceStack source = ctx.getSource();
                              if (source.getPlayer() == null) {
                                source.sendFailure(Component.literal("This command can only be executed by player."));
                                return 0;
                              }
                              if (!source.getPlayer().isSpectator()) {
                                source.sendFailure(
                                    Component.literal("This command can only be executed by a spectator player."));
                                return 0;
                              }
                              int time = IntegerArgumentType.getInteger(ctx, "time");
                              boolean morePenalty = BoolArgumentType.getBool(ctx, "after_detection");
                              ServerPlayer player = source.getPlayer();
                              DeathPenaltyComponent.KEY.get(player).setPenalty(time, morePenalty);
                              source.sendSuccess(
                                  () -> Component.translatable("Start Penalty for %s for %ss", player.getName(),
                                      String.format("%.2f", ((double) time) / 20.)),
                                  true);
                              return 1;
                            }))
                            .then(Commands.literal("entity")
                                .then(Commands.argument("entity", EntityArgument.entity()).executes((ctx) -> {
                                  CommandSourceStack source = ctx.getSource();
                                  if (source.getPlayer() == null) {
                                    source
                                        .sendFailure(Component.literal("This command can only be executed by player."));
                                    return 0;
                                  }
                                  if (!source.getPlayer().isSpectator()) {
                                    source.sendFailure(
                                        Component.literal("This command can only be executed by a spectator player."));
                                    return 0;
                                  }
                                  int time = IntegerArgumentType.getInteger(ctx, "time");
                                  Entity entity = EntityArgument.getEntity(ctx, "entity");
                                  boolean morePenalty = BoolArgumentType.getBool(ctx, "after_detection");
                                  ServerPlayer player = source.getPlayer();
                                  DeathPenaltyComponent.KEY.get(player).setPenaltyWithCameraLimit(time, entity,
                                      morePenalty);
                                  source.sendSuccess(
                                      () -> Component.translatable("Start Penalty for %s for %ss on %s",
                                          player.getName(),
                                          String.format("%.2f", ((double) time) / 20.), entity.getName()),
                                      true);
                                  return 1;
                                })))
                            .then(Commands.literal("pos")
                                .then(Commands.argument("pos", Vec3Argument.vec3(true)).executes((ctx) -> {
                                  CommandSourceStack source = ctx.getSource();
                                  if (source.getPlayer() == null) {
                                    source
                                        .sendFailure(Component.literal("This command can only be executed by player."));
                                    return 0;
                                  }
                                  if (!source.getPlayer().isSpectator()) {
                                    source.sendFailure(
                                        Component.literal("This command can only be executed by a spectator player."));
                                    return 0;
                                  }
                                  int time = IntegerArgumentType.getInteger(ctx, "time");
                                  Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                                  boolean morePenalty = BoolArgumentType.getBool(ctx, "after_detection");
                                  ServerPlayer player = source.getPlayer();
                                  DeathPenaltyComponent.KEY.get(player).setPenaltyWithPositionLimit(time, pos,
                                      morePenalty);
                                  source.sendSuccess(
                                      () -> Component.translatable("Start Penalty for %s for %ss at %s",
                                          player.getName(),
                                          String.format("%.2f", ((double) time) / 20.), pos.toString()),
                                      true);
                                  return 1;
                                }))))))));
  }
}
