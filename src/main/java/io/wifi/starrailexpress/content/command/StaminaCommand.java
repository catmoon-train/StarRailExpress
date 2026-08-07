/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.content.command;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import io.wifi.starrailexpress.network.packet.StaminaS2CPacket;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;

public class StaminaCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("sre:stamina")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("add").then(Commands
                .argument("amount", IntegerArgumentType.integer())
                .executes(context -> executeAdd(context.getSource(),
                    ImmutableList.of(context.getSource()
                        .getEntityOrException()),
                    IntegerArgumentType.getInteger(context,
                        "amount")))
                .then(
                    Commands.argument("targets",
                        EntityArgument.players())
                        .executes(context -> executeAdd(
                            context.getSource(),
                            EntityArgument.getPlayers(
                                context,
                                "targets"),
                            IntegerArgumentType
                                .getInteger(context,
                                    "amount"))))))
            .then(Commands.literal("set")
                .then(Commands.literal("max")
                    .executes(context -> executeSet(context.getSource(),
                        ImmutableList.of(context.getSource()
                            .getPlayerOrException()),
                        -1))
                    .then(
                        Commands.argument("targets",
                            EntityArgument.players())
                            .executes(context -> executeSet(
                                context.getSource(),
                                EntityArgument.getPlayers(
                                    context,
                                    "targets"),
                                -1))))
                .then(Commands.argument("amount", IntegerArgumentType.integer())
                    .executes(context -> executeSet(context.getSource(),
                        ImmutableList.of(context.getSource()
                            .getPlayerOrException()),
                        IntegerArgumentType.getInteger(context,
                            "amount")))
                    .then(
                        Commands.argument("targets",
                            EntityArgument.players())
                            .executes(context -> executeSet(
                                context.getSource(),
                                EntityArgument.getPlayers(
                                    context,
                                    "targets"),
                                IntegerArgumentType
                                    .getInteger(context,
                                        "amount"))))))
            .then(Commands.literal("get").executes(context -> executeGet(
                context.getSource(),
                ImmutableList.of(context.getSource()
                    .getEntityOrException())))
                .then(Commands.argument("targets",
                    EntityArgument.players())
                    .executes(context -> executeGet(
                        context.getSource(),
                        EntityArgument.getPlayers(
                            context,
                            "targets"))))));
  }

  public static boolean setStamina(Player target, float amount) {
    return setStamina(target, amount, true);
  }

  public static boolean setStamina(Player target, float amount, boolean sync) {
    if (target instanceof PlayerStaminaGetter stam) {
      stam.starrailexpress$setStamina((float) amount);

      if (sync) {
        if (!target.level().isClientSide && target instanceof ServerPlayer sp) {
          ServerPlayNetworking.send(sp, new StaminaS2CPacket(amount));
        }
      }
      return true;
    }
    return false;
  }

  private static int executeSet(CommandSourceStack source, Collection<? extends Player> targets, int amount) {
    int finalAmount = Math.max(amount, -1);
    int total = 0;

    for (Player target : targets) {
      if (target instanceof PlayerStaminaGetter) {
        setStamina(target, (float) finalAmount);
        total += finalAmount;
      }
    }

    if (targets.size() == 1) {
      Entity target = targets.iterator().next();
      source.sendSuccess(
          () -> Component
              .translatable("commands.sre.setstamina",
                  target.getName().getString(), amount)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    } else {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.setstamina.multiple", targets.size(),
              amount)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    }
    return total;
  }

  private static int executeAdd(CommandSourceStack source, Collection<? extends Entity> targets, int amount) {
    int total = 0;
    for (Entity target : targets) {
      if (target instanceof PlayerStaminaGetter stam) {
        float current = stam.starrailexpress$getStamina();
        float newVal = Math.max(0, current + amount);
        stam.starrailexpress$setStamina(newVal);
        total += (int) newVal;
      }
    }

    if (targets.size() == 1) {
      Entity target = targets.iterator().next();
      float stamina = ((PlayerStaminaGetter) target).starrailexpress$getStamina();
      source.sendSuccess(
          () -> Component
              .translatable("commands.sre.addstamina",
                  target.getName().getString(), amount,
                  (int) stamina)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    } else {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.addstamina.multiple", targets.size(),
              amount)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    }
    return total;
  }

  private static int executeGet(CommandSourceStack source, Collection<? extends Entity> targets) {
    final int total = targets.stream().mapToInt(target -> {
      if (target instanceof PlayerStaminaGetter stam) {
        return (int) stam.starrailexpress$getStamina();
      }
      return 0;
    }).sum();
    source.sendSuccess(
        () -> Component
            .translatable("commands.sre.getstamina", total)
            .withStyle(style -> style.withColor(0x00FF00)),
        true);
    return total;
  }
}
