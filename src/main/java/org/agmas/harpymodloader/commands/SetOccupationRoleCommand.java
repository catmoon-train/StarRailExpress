package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;

public class SetOccupationRoleCommand {

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("setOccupationRole")
        .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
        .then(Commands.argument("mainRole", RoleArgumentType.create())
            .then(Commands.argument("companionRole", RoleArgumentType.create())
                .executes(SetOccupationRoleCommand::setOccupationRole)))
        .then(Commands.literal("remove")
            .then(Commands.argument("mainRole", RoleArgumentType.create())
                .then(Commands.argument("companionRole", RoleArgumentType.create())
                    .executes(SetOccupationRoleCommand::removeOccupationRole))))
        .then(Commands.literal("clear")
            .then(Commands.argument("role", RoleArgumentType.create())
                .executes(SetOccupationRoleCommand::clearOccupationRole)))
        .then(Commands.literal("list")
            .then(Commands.argument("role", RoleArgumentType.create())
                .executes(SetOccupationRoleCommand::listOccupationRoles))));
  }

  private static int setOccupationRole(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    if (!Harpymodloader.officialVerify) {
      return 1;
    }
    SRERole mainRole = RoleArgumentType.getRole(context, "mainRole");
    SRERole companionRole = RoleArgumentType.getRole(context, "companionRole");

    if (mainRole.equals(companionRole)) {
      context.getSource().sendFailure(
          Component.literal("Main role and companion role cannot be the same!"));
      return 0;
    }

    Harpymodloader.addOccupationRole(mainRole, companionRole);
    context.getSource().sendSuccess(
        () -> Component.translatable(
            "§aSet occupation role: %s -> %s",
            mainRole.identifier(),
            companionRole.identifier()),
        true);
    return 1;
  }

  private static int removeOccupationRole(CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    SRERole mainRole = RoleArgumentType.getRole(context, "mainRole");
    SRERole companionRole = RoleArgumentType.getRole(context, "companionRole");
    if (!mainRole.hasOccupationRole()) {
      context.getSource().sendFailure(
          Component.translatable("Role %s has no occupation role!", mainRole.getName()));
      return 0;
    }
    mainRole.removeOccupationRole(companionRole);
    context.getSource().sendSuccess(
        () -> Component.translatable("§aRemoved occupation role %s for: %s", mainRole.getName(),
            companionRole.getName()),
        true);
    return 1;
  }

  private static int clearOccupationRole(CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    SRERole role = RoleArgumentType.getRole(context, "role");

    if (!Harpymodloader.hasOccupationRole(role)) {
      context.getSource().sendFailure(
          Component.literal("Role " + role.identifier() + " has no occupation role!"));
      return 0;
    }

    Harpymodloader.clearOccupationRole(role);
    context.getSource().sendSuccess(
        () -> Component.translatable(
            "§aRemoved occupation role for: %s", role.identifier()),
        true);
    return 1;
  }

  private static int listOccupationRoles(CommandContext<CommandSourceStack> context)
      throws CommandSyntaxException {
    SRERole role = RoleArgumentType.getRole(context, "role");

    if (role.occupationRoles.isEmpty()) {
      context.getSource().sendSuccess(
          () -> Component.translatable("§cNo occupation roles configured for %s", role.getName()),
          false);
      return 0;
    }

    context.getSource().sendSuccess(() -> Component.translatable("§6Current occupation roles for %s:", role.getName()),
        false);
    role.occupationRoles.forEach((companionRole) -> context.getSource().sendSuccess(
        () -> Component.translatable(" - %s",
            companionRole.getName()),
        false));
    return 1;
  }
}
