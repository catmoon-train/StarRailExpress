package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.api.Role;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;

public class SetOccupationRoleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setOccupationRole")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.argument("mainRole", RoleArgumentType.skipVanilla())
                        .then(Commands.argument("companionRole", RoleArgumentType.skipVanilla())
                                .executes(SetOccupationRoleCommand::setOccupationRole)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("role", RoleArgumentType.skipVanilla())
                                .executes(SetOccupationRoleCommand::removeOccupationRole)))
                .then(Commands.literal("clear")
                        .executes(SetOccupationRoleCommand::clearOccupationRoles))
                .then(Commands.literal("list")
                        .executes(SetOccupationRoleCommand::listOccupationRoles)));
    }

    private static int setOccupationRole(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if(!Harpymodloader.isMojangVerify) {
            return 1;
        }
        Role mainRole = RoleArgumentType.getRole(context, "mainRole");
        Role companionRole = RoleArgumentType.getRole(context, "companionRole");

        if (mainRole.equals(companionRole)) {
            context.getSource().sendFailure(Component.literal("Main role and companion role cannot be the same!"));
            return 0;
        }

        Harpymodloader.setOccupationRole(mainRole, companionRole);
        context.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "§aSet occupation role: %s -> %s",
                        mainRole.identifier(),
                        companionRole.identifier())),
                true);
        return 1;
    }

    private static int removeOccupationRole(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Role role = RoleArgumentType.getRole(context, "role");

        if (!Harpymodloader.hasOccupationRole(role)) {
            context.getSource().sendFailure(Component.literal("Role " + role.identifier() + " has no occupation role!"));
            return 0;
        }

        Harpymodloader.removeOccupationRole(role);
        context.getSource().sendSuccess(
                () -> Component.literal(String.format("§aRemoved occupation role for: %s", role.identifier())),
                true);
        return 1;
    }

    private static int clearOccupationRoles(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = Harpymodloader.Occupations_Roles.size();
        Harpymodloader.clearOccupationRoles();
        context.getSource().sendSuccess(
                () -> Component.literal(String.format("§aCleared all occupation roles (%d removed)", count)),
                true);
        return 1;
    }

    private static int listOccupationRoles(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (Harpymodloader.Occupations_Roles.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("§cNo occupation roles configured"), false);
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("§6Current occupation roles:"), false);
        Harpymodloader.Occupations_Roles.forEach((mainRole, companionRole) ->
                context.getSource().sendSuccess(
                        () -> Component.literal(String.format("  §a%s §f-> §a%s",
                                mainRole.identifier(), companionRole.identifier())),
                        false));
        return 1;
    }
}
