package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.wifi.starrailexpress.api.Role;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

public class SetEnabledRoleCommand {
    public static final SimpleCommandExceptionType ROLE_UNCHANGED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.setenabledrole.unchanged"));


    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setEnabledRole")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.argument("role", RoleArgumentType.skipVanilla())
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(SetEnabledRoleCommand::execute))
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if(!Harpymodloader.isMojangVerify) {
            return 1;
        }
        Role role = RoleArgumentType.getRole(context, "role");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        HarpyModLoaderConfig.HANDLER.save();
        String roleId = role.identifier().toString();
        boolean disabled = HarpyModLoaderConfig.HANDLER.instance().disabled.contains(roleId);
        Component roleText = Harpymodloader.getRoleName(role).withColor(role.color()).withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(roleId))));

        if (disabled && enabled) {
            HarpyModLoaderConfig.HANDLER.instance().disabled.remove(roleId);
            context.getSource().sendSuccess(() -> Component.translatable("commands.setenabledrole.enable.success", roleText), true);
        } else if (!disabled && !enabled) {
            HarpyModLoaderConfig.HANDLER.instance().disabled.add(roleId);
            context.getSource().sendSuccess(() -> Component.translatable("commands.setenabledrole.disable.success", roleText), true);
        } else throw ROLE_UNCHANGED_EXCEPTION.create();

        HarpyModLoaderConfig.HANDLER.save();
        return 1;
    }
}
