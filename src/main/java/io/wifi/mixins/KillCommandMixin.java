package io.wifi.mixins;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.KillCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import com.mojang.brigadier.CommandDispatcher;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

@Mixin(KillCommand.class)
public abstract class KillCommandMixin {
  @Overwrite
  public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
    commandDispatcher.register(
        ((Commands.literal("kill").requires((commandSourceStack) -> {
          return commandSourceStack.hasPermission(3);
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