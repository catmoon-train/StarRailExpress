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

package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.SREConfig;
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
        .requires(serverCommandSource -> serverCommandSource.hasPermission(SREConfig.instance().changeModifierRequiredPermission))
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
      return 2;
    }
    // 获取玩家当前Modifier状态
    var modifiers = worldModifierComponent.getModifiers(targetPlayer.getUUID());
    // type: -1=toggle, 0=remove, 1=add
    boolean hasModifier = modifiers.contains(modifier);
    final MutableComponent feedbackText;

    // 移除：显式 remove，或 toggle 且当前拥有该修饰符
    if (type == 0 || (type == -1 && hasModifier)) {
      ModifierRemoved.EVENT.invoker().removeModifier(targetPlayer, modifier);
      worldModifierComponent.removeModifier(targetPlayer.getUUID(), modifier);
      feedbackText = Component.translatable("commands.changemodifier.player.notification.remove",
          targetPlayer.getName(), modifier.getName());
    }
    // 添加：显式 add，或 toggle 且当前没有该修饰符
    else if (type == 1 || (type == -1 && !hasModifier)) {
      // 保险：目标玩家没有角色（如游戏未开始、换局重置中）时不允许添加修饰符，
      // 否则修饰符的 tick 逻辑会在 getRole() 返回 null 时崩溃
      if (game.getRole(targetPlayer) == null) {
        context.getSource()
            .sendFailure(Component.translatable("commands.changemodifier.player.notification.failed.norole",
                targetPlayer.getName()));
        return 0;
      }
      worldModifierComponent.addModifier(targetPlayer.getUUID(), modifier);
      ModifierAssigned.EVENT.invoker().assignModifier(targetPlayer, modifier);
      feedbackText = Component.translatable("commands.changemodifier.player.notification.add",
          targetPlayer.getName(), modifier.getName());
    }
    // 状态未变化（如 add 已拥有的、remove 未拥有的）：不触发任何事件
    else {
      context.getSource().sendFailure(Component.translatable(
          "commands.changemodifier.player.notification.nothing_change",
          targetPlayer.getName(), modifier.getName()));
      return 0;
    }
    // 发送反馈消息
    context.getSource().sendSuccess(() -> feedbackText, true);

    return 1;
  }
}