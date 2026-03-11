package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.api.Role;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;

public class ForceRoleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("forceRole")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ForceRoleCommand::query)
                        .then(Commands.argument("role", RoleArgumentType.skipVanilla())
                                .executes(ForceRoleCommand::execute))));
    }

    private static int query(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if(!Harpymodloader.isMojangVerify) {
            return 1;
        }
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        if (!Harpymodloader.FORCED_MODDED_ROLE_FLIP.containsKey(targetPlayer.getUUID())) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.query.none", targetPlayer.getDisplayName()), false);
            return 0;
        }
        Role role = Harpymodloader.FORCED_MODDED_ROLE_FLIP.get(targetPlayer.getUUID());
        Component roleText = Harpymodloader.getRoleName(role).withColor(role.color()).withStyle(style ->
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(role.identifier().toString())))
        );
        context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.query", targetPlayer.getDisplayName(), roleText), false);
        return 1;
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
        Role role = RoleArgumentType.getRole(context, "role");
        Harpymodloader.addToForcedRoles(role, targetPlayer);
        final MutableComponent roleText = Harpymodloader.getRoleName(role).withColor(role.color()).withStyle(style ->
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(role.identifier().toString()))));
        context.getSource().sendSuccess(() -> Component.translatable("commands.forcerole.success", roleText, targetPlayer.getDisplayName()), true);
        return 1;
    }
}