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
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

public class SetEnabledRoleCommand {
    public static final SimpleCommandExceptionType ROLE_UNCHANGED_EXCEPTION = new SimpleCommandExceptionType(
            Component.translatable("commands.setenabledrole.unchanged"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setEnabledRole")
                .requires(serverCommandSource -> serverCommandSource
                        .hasPermission(SREConfig.instance().modifyEnableStatusRequiredPermission))
                .then(Commands.literal("enableAll").executes(SetEnabledRoleCommand::enableAll))
                .then(Commands.literal("disableAll").executes(SetEnabledRoleCommand::disableAll))
                .then(Commands.argument("role", RoleArgumentType.create())
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes((ctx) -> execute(ctx, 0))
                                .then(Commands.literal("show")
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes((ctx) -> execute(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "page"))))))));
    }

    private static int enableAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Harpymodloader.officialVerify) {
            return 1;
        }
        HarpyModLoaderConfig.HANDLER.instance().disabled.clear();
        HarpyModLoaderConfig.HANDLER.save();
        context.getSource()
                .sendSuccess(() -> Component.translatable("commands.setenabledrole.enable.success", "ALL"), true);

        return 1;
    }

    private static int disableAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Harpymodloader.officialVerify) {
            return 1;
        }
        HarpyModLoaderConfig.HANDLER.instance().disabled.clear();
        for (var role : TMMRoles.ROLES.keySet()) {
            HarpyModLoaderConfig.HANDLER.instance().disabled.add(role.toString());
        }
        HarpyModLoaderConfig.HANDLER.save();
        context.getSource()
                .sendSuccess(() -> Component.translatable("commands.setenabledrole.disable.success", "ALL"), true);

        return 1;
    }

    private static int execute(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        if (!Harpymodloader.officialVerify) {
            return 1;
        }
        SRERole role = RoleArgumentType.getRole(context, "role");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        String roleId = role.identifier().toString();
        boolean disabled = HarpyModLoaderConfig.HANDLER.instance().getDisabled().contains(roleId);
        Component roleText = Harpymodloader.getRoleName(role).withColor(role.color()).withStyle(
                style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(roleId))));

        if (disabled && enabled) {
            HarpyModLoaderConfig.HANDLER.instance().disabled.remove(roleId);
            HarpyModLoaderConfig.HANDLER.save();
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setenabledrole.enable.success", roleText), true);
        } else if (!disabled && !enabled) {
            HarpyModLoaderConfig.HANDLER.instance().disabled.add(roleId);
            HarpyModLoaderConfig.HANDLER.save();
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setenabledrole.disable.success", roleText), true);
        } else
            throw ROLE_UNCHANGED_EXCEPTION.create();

        HarpyModLoaderConfig.HANDLER.save();
        if (page > 0) {
            ListRolesCommand.showRole(context, page);
        }
        return 1;
    }
}
