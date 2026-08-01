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
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.agmas.harpymodloader.Harpymodloader;

import java.util.Collection;

public class MiniGameMoneyCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("tmm:minigame_coin")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("set").then(Commands.argument("amount", IntegerArgumentType.integer(0))
                .executes(context -> executeSet(context.getSource(),
                    ImmutableList.of(context.getSource().getEntityOrException()),
                    IntegerArgumentType.getInteger(context, "amount")))
                .then(
                    Commands.argument("targets", EntityArgument.entities())
                        .executes(context -> executeSet(context.getSource(),
                            EntityArgument.getEntities(context, "targets"),
                            IntegerArgumentType.getInteger(context, "amount"))))))
            .then(Commands.literal("add").then(Commands.argument("amount", IntegerArgumentType.integer())
                .executes(context -> executeAdd(context.getSource(),
                    ImmutableList.of(context.getSource().getEntityOrException()),
                    IntegerArgumentType.getInteger(context, "amount")))
                .then(
                    Commands.argument("targets", EntityArgument.entities())
                        .executes(context -> executeAdd(context.getSource(),
                            EntityArgument.getEntities(context, "targets"),
                            IntegerArgumentType.getInteger(context, "amount"))))))
            .then(Commands.literal("get").executes(context -> executeGet(context.getSource(),
                ImmutableList.of(context.getSource().getEntityOrException())))
                .then(Commands.argument("targets", EntityArgument.entities())
                    .executes(context -> executeGet(context.getSource(),
                        EntityArgument.getEntities(context, "targets"))))));
  }

  private static int executeSet(CommandSourceStack source, Collection<? extends Entity> targets, int amount) {
    if (!Harpymodloader.officialVerify)
      return 0;
    int total = 0;

    for (Entity target : targets) {
      SREPlayerMinigameTaskComponent.KEY.get(target).setTokens(amount);
      total += SREPlayerMinigameTaskComponent.KEY.get(target).tokens;
    }

    if (targets.size() == 1) {
      Entity target = targets.iterator().next();
      source.sendSuccess(
          () -> Component
              .translatable("commands.sre.setmoney", target.getName().getString(), amount)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    } else {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.setmoney.multiple", targets.size(), amount)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    }
    return total;
  }

  private static int executeAdd(CommandSourceStack source, Collection<? extends Entity> targets, int amount) {
    if (!Harpymodloader.officialVerify)
      return 0;
    int total = 0;
    for (Entity target : targets) {
      SREPlayerMinigameTaskComponent.KEY.get(target).addTokens(amount);
      total += SREPlayerMinigameTaskComponent.KEY.get(target).tokens;
    }

    if (targets.size() == 1) {
      Entity target = targets.iterator().next();
      int money = SREPlayerMinigameTaskComponent.KEY.get(target).tokens;

      source.sendSuccess(
          () -> Component
              .translatable("commands.sre.addmoney", target.getName().getString(), amount, money)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    } else {
      source.sendSuccess(
          () -> Component.translatable("commands.sre.addmoney.multiple", targets.size(), amount)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
    }
    return total;
  }

  private static int executeGet(CommandSourceStack source, Collection<? extends Entity> targets) {
    final int total = targets.stream().mapToInt(target -> {
      var ba = SREPlayerMinigameTaskComponent.KEY.maybeGet(target).orElse(null);
      if (ba != null) {
        return ba.tokens;
      }
      return 0;
    }).sum();
    source.sendSuccess(
        () -> Component
            .translatable("commands.sre.getmoney", total)
            .withStyle(style -> style.withColor(0x00FF00)),
        true);
    return total;
  }

}
