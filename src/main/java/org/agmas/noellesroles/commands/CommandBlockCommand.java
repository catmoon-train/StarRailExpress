package org.agmas.noellesroles.commands;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.agmas.noellesroles.game.command_block.CommandBlockDirector;

/** 祸患的终端：下一局强制刷新命令方块事件。 */
public final class CommandBlockCommand {
    private CommandBlockCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("sre:command_block")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("next").executes(CommandBlockCommand::forceNext))));
    }

    private static int forceNext(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        if (!CommandBlockDirector.forceNextRound(level)) {
            context.getSource().sendFailure(Component.translatable("command.noellesroles.command_block.next_rejected"));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("command.noellesroles.command_block.next_forced"), true);
        return 1;
    }
}
