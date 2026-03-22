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

public class SetCompanionRoleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setCompanionRole")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.argument("primaryRole", RoleArgumentType.skipVanilla())
                        .then(Commands.argument("companionRole", RoleArgumentType.skipVanilla())
                                .executes(SetCompanionRoleCommand::execute)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("primaryRole", RoleArgumentType.skipVanilla())
                                .executes(SetCompanionRoleCommand::removeCompanion))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Harpymodloader.isMojangVerify) {
            return 1;
        }
        SRERole primaryRole = RoleArgumentType.getRole(context, "primaryRole");
        SRERole companionRole = RoleArgumentType.getRole(context, "companionRole");

        Component primaryRoleText = Harpymodloader.getRoleName(primaryRole).withColor(primaryRole.color());
        Component companionRoleText = Harpymodloader.getRoleName(companionRole).withColor(companionRole.color());

        context.getSource().sendSuccess(
                () -> Component.translatable("commands.sre.setcompanionrole.success",
                        primaryRoleText, companionRoleText),
                true);

        return 1;
    }

    private static int removeCompanion(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        SRERole primaryRole = RoleArgumentType.getRole(context, "primaryRole");

        // 从配置中移除
        if (Harpymodloader.Occupations_Roles.remove(primaryRole) != null) {
            Component primaryRoleText = Harpymodloader.getRoleName(primaryRole).withColor(primaryRole.color());

            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.sre.removecompanionrole.success", primaryRoleText),
                    true);
        } else {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.sre.removecompanionrole.notfound",
                            Harpymodloader.getRoleName(primaryRole).withColor(primaryRole.color())),
                    false);
        }

        return 1;
    }
}