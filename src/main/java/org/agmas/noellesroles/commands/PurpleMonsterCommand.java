package org.agmas.noellesroles.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.role.bouns.roles.PurpleMonsterRole;

/** Test command: bypasses map, player count, mood and role-disable conditions. */
public final class PurpleMonsterCommand {
    private PurpleMonsterCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("sre:purple_monster")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("trigger")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(PurpleMonsterCommand::trigger)))));
    }

    private static int trigger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (!PurpleMonsterRole.forceStart(target)) {
            context.getSource().sendFailure(Component.literal("无法为该玩家触发紫怪事件"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("已为 " + target.getName().getString()
                + " 触发紫怪事件"), true);
        return 1;
    }
}
