package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;

public class ChangeRoleCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("changeRole")
        .requires(serverCommandSource -> serverCommandSource.hasPermission(3))
        .then(Commands.argument("player", EntityArgument.player())
            .then(Commands.literal("reset")
                .executes(ChangeRoleCommand::executeReset))
            .then(Commands.argument("role", RoleArgumentType.create())
                .executes((ctx) -> execute(ctx, true, false))
                .then(Commands.argument("record_replay", BoolArgumentType.bool())
                    .then(Commands.argument("add_stats", BoolArgumentType.bool())
                        .executes((ctx) -> execute(ctx, BoolArgumentType.getBool(ctx, "record_replay"),
                            BoolArgumentType.getBool(ctx, "add_stats"))))))));
  }

  private static int execute(CommandContext<CommandSourceStack> context, boolean record, boolean addStats)
      throws CommandSyntaxException {
    try {
      if (!Harpymodloader.isMojangVerify) {
        return 1;
      }
      ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
      SRERole newRole = RoleArgumentType.getRole(context, "role");

      // 获取游戏世界组件
      SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(targetPlayer.level());
      SREPlayerTaskComponent srePlayerTaskComponent = SREPlayerTaskComponent.KEY.get(targetPlayer);
      srePlayerTaskComponent.clear();
      srePlayerTaskComponent.sync();

      SRERole oldRole = gameWorldComponent.getRole(targetPlayer);
      if (oldRole!=null) {
        var cacheItems = new ArrayList<ItemStack>();
        targetPlayer.getInventory().items.forEach(
                itemStack -> {
                  if (oldRole.getDefaultItems().stream().anyMatch(itemStack1 -> itemStack1.getItem().equals(itemStack.getItem()))) {
                    cacheItems.add(itemStack);
                  }
                }
        );
        cacheItems.forEach(
                itemStack -> {
                  targetPlayer.getInventory().removeItem(itemStack);
                }
        );
      }
      newRole.getDefaultItems().forEach(itemStack -> targetPlayer.getInventory().add(itemStack.copy()));
      RoleUtils.changeRole(targetPlayer, newRole, record, addStats);

      // 发送反馈消息
      final MutableComponent newRoleText = Harpymodloader.getRoleName(newRole).withColor(newRole.color())
          .withStyle(style -> style.withHoverEvent(
              new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                  Component.literal(newRole.identifier().toString()))));

      if (oldRole != null) {
        final MutableComponent oldRoleText = Harpymodloader.getRoleName(oldRole).withColor(oldRole.color())
            .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.literal(oldRole.identifier().toString()))));
        context.getSource()
            .sendSuccess(() -> Component.translatable("commands.changerole.success.changed", oldRoleText,
                newRoleText, targetPlayer.getName()), true);
      } else {
        context.getSource().sendSuccess(() -> Component.translatable("commands.changerole.success.assigned",
            newRoleText, targetPlayer.getName()), true);
      }

      // 通知玩家角色已改变
      // targetPlayer.displayClientMessage(Component.translatable("commands.changerole.player.notification",
      // newRoleText), false);
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal(e.getMessage()));
    }
    return 1;
  }

  private static int executeReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    try {
      if (!Harpymodloader.isMojangVerify) {
        return 1;
      }
      ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");

      // 获取游戏世界组件
      SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(targetPlayer.level());
      SREPlayerTaskComponent srePlayerTaskComponent = SREPlayerTaskComponent.KEY.get(targetPlayer);
      srePlayerTaskComponent.clear();
      srePlayerTaskComponent.sync();

      SRERole oldRole = gameWorldComponent.getRole(targetPlayer);
      if (oldRole != null) {
        // 清除旧角色默认物品
        var cacheItems = new ArrayList<ItemStack>();
        targetPlayer.getInventory().items.forEach(
                itemStack -> {
                  if (oldRole.getDefaultItems().stream().anyMatch(itemStack1 -> itemStack1.getItem().equals(itemStack.getItem()))) {
                    cacheItems.add(itemStack);
                  }
                }
        );
        cacheItems.forEach(
                itemStack -> {
                  targetPlayer.getInventory().removeItem(itemStack);
                }
        );
        // 触发移除事件
        ((ModdedRoleRemoved) ModdedRoleRemoved.EVENT.invoker()).removeModdedRole(targetPlayer, oldRole);
      }

      // 从角色映射中移除
      gameWorldComponent.removeRole(targetPlayer);

      // 发送反馈消息
      final MutableComponent oldRoleText = oldRole != null
          ? Harpymodloader.getRoleName(oldRole).withColor(oldRole.color())
              .withStyle(style -> style.withHoverEvent(
                  new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                      Component.literal(oldRole.identifier().toString()))))
          : null;

      if (oldRole != null) {
        context.getSource()
            .sendSuccess(() -> Component.translatable("commands.changerole.reset.from", oldRoleText,
                targetPlayer.getName()), true);
      } else {
        context.getSource()
            .sendSuccess(() -> Component.translatable("commands.changerole.reset", targetPlayer.getName()), true);
      }
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal(e.getMessage()));
    }
    return 1;
  }
}