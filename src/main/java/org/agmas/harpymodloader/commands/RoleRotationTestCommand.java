package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.content.command.ConfigCommand;
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
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;

public class RoleRotationTestCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("tmm:game")
        .then(Commands.literal("role").then(Commands.literal("role_change_mode")
            .requires(serverCommandSource -> serverCommandSource.hasPermission(3))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("role", RoleArgumentType.create())
                    .executes((ctx) -> execute(ctx, true, false))
                    .then(Commands.argument("record_replay", BoolArgumentType.bool())
                        .then(Commands.argument("add_stats", BoolArgumentType.bool())
                            .executes((ctx) -> execute(ctx,
                                BoolArgumentType.getBool(ctx, "record_replay"),
                                BoolArgumentType.getBool(ctx, "add_stats"))))))))));
  }

  private static int execute(CommandContext<CommandSourceStack> context, boolean record, boolean addStats)
      throws CommandSyntaxException {
    try {
      if (!Harpymodloader.officialVerify) {
        return 1;
      }
      ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
      SRERole newRole = RoleArgumentType.getRole(context, "role");

      // 获取游戏世界组件
      SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(targetPlayer.level());
      if (!gameWorldComponent.getGameMode().identifier.equals(SREGameModes.ROLE_ROTATION_MODE.identifier) && !gameWorldComponent.getGameMode().identifier.equals(SREGameModes.CLASS_CHANGE_MODE.identifier)) {
        throw new Exception("This command can only be executed in 'Role Rotation Mode' or 'Class Change Mode'!");
      }
      SREPlayerTaskComponent srePlayerTaskComponent = SREPlayerTaskComponent.KEY.get(targetPlayer);
      srePlayerTaskComponent.clear();
      srePlayerTaskComponent.sync();

      SRERole oldRole = gameWorldComponent.getRole(targetPlayer);
      if (oldRole != null) {
        var cacheItems = new ArrayList<ItemStack>();
        targetPlayer.getInventory().items.forEach(
            itemStack -> {
              if (oldRole.getDefaultItems().stream()
                  .anyMatch(itemStack1 -> itemStack1.getItem().equals(itemStack.getItem()))) {
                cacheItems.add(itemStack);
              }
            });
        cacheItems.forEach(
            itemStack -> {
              targetPlayer.getInventory().removeItem(itemStack);
            });
      }
      // 自定义职业的初始物品已通过 INITIAL_ITEMS_MAP 在 changeRole → ModdedRoleAssigned
      // 事件中发放，此处跳过避免重复
      if (!"customrole".equals(newRole.identifier().getNamespace())) {
        newRole.getDefaultItems().forEach(itemStack -> targetPlayer.getInventory().add(itemStack.copy()));
      }
      RoleUtils.changeRole(targetPlayer, newRole, record, addStats);

      // 发送欢迎报幕
      RoleUtils.sendWelcomeAnnouncement(targetPlayer);

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
    } catch (Exception e) {
      throw ConfigCommand.createSimpleSyntaxException(e);
    }
    return 1;
  }
}
