package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.modifiers.SREModifier;

public class ChangeModifierCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("changeModifier")
        .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
        .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("modifier", ModifierArgumentType.create())
                .executes((ctx) -> execute(ctx, -1))
                .then(Commands.literal("add")
                    .executes((ctx) -> execute(ctx, 1)))
                .then(Commands.literal("remove")
                    .executes((ctx) -> execute(ctx, 0)))
                .then(Commands.literal("toggle")
                    .executes((ctx) -> execute(ctx, -1))))));
  }

  private static int execute(CommandContext<CommandSourceStack> context, int type) throws CommandSyntaxException {
    if (!Harpymodloader.officialVerify) {
      return 1;
    }
    ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
    SREModifier modifier = ModifierArgumentType.getModifier(context, "modifier");
    SREGameWorldComponent game = SREGameWorldComponent.KEY.get(targetPlayer.level());
    // 获取游戏世界组件
    WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(targetPlayer.level());

    if (!game.isRunning()) {
      context.getSource()
          .sendFailure(Component.translatable("commands.changemodifier.player.notification.failed.nostart"));
      // return 2;
    }
    // 获取玩家当前Modifier状态
    var modifiers = worldModifierComponent.getModifiers(targetPlayer.getUUID());
    final MutableComponent feedbackText;

    if (modifiers.contains(modifier) && type == -1 || type == 0) {
      ModifierRemoved.EVENT.invoker().removeModifier(targetPlayer, modifier);
      worldModifierComponent.removeModifier(targetPlayer.getUUID(), modifier);
      feedbackText = Component.translatable("commands.changemodifier.player.notification.remove",
          targetPlayer.getName(), modifier.getName());
    } else if (!modifiers.contains(modifier) && type == -1 || type == 1) {
      worldModifierComponent.addModifier(targetPlayer.getUUID(), modifier);
      ModifierAssigned.EVENT.invoker().assignModifier(targetPlayer, modifier);
      feedbackText = Component.translatable("commands.changemodifier.player.notification.add",
          targetPlayer.getName(), modifier.getName());
    } else {
      ModifierAssigned.EVENT.invoker().assignModifier(targetPlayer, modifier);
      feedbackText = Component.translatable("commands.changemodifier.player.notification.nothing_change",
          targetPlayer.getName(), modifier.getName());
      context.getSource().sendFailure(feedbackText);
      return 0;
    }
    // 发送反馈消息
    context.getSource().sendSuccess(() -> feedbackText, true);

    return 1;
  }
}