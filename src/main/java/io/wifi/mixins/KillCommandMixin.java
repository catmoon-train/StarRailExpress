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

package io.wifi.mixins;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;

import io.wifi.starrailexpress.SREConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collection;

@Mixin(KillCommand.class)
public abstract class KillCommandMixin {
  @Overwrite
  public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
    commandDispatcher.register(
        ((Commands.literal("kill").requires((commandSourceStack) -> {
          if (commandSourceStack.isPlayer())
            return commandSourceStack.hasPermission(SREConfig.instance().gameKillRequiredPermission);
          else
            return commandSourceStack.hasPermission(2);
        }))
            .executes((commandContext) -> sre$kill(commandContext.getSource(),
                ImmutableList.of((commandContext.getSource()).getEntityOrException()))))
            .then(Commands.argument("targets", EntityArgument.entities())
                .executes((commandContext) -> sre$kill(commandContext.getSource(),
                    EntityArgument.getEntities(commandContext, "targets")))));
  }

  @Unique
  private static int sre$kill(CommandSourceStack commandSourceStack, Collection<? extends Entity> collection) {
    for (Entity entity : collection) {
      entity.kill();
    }

    if (collection.size() == 1) {
      commandSourceStack.sendSuccess(() -> Component.translatable("commands.kill.success.single",
          new Object[] { ((Entity) collection.iterator().next()).getDisplayName() }), true);
    } else {
      commandSourceStack.sendSuccess(
          () -> Component.translatable("commands.kill.success.multiple", new Object[] { collection.size() }),
          true);
    }

    return collection.size();
  }
}