package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SetRoleCountCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:setrolecount")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("killer")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(SetRoleCountCommand::setKillerCount)))
                        .then(Commands.literal("vigilante")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(SetRoleCountCommand::setVigilanteCount))));
    }

    private static int setKillerCount(CommandContext<CommandSourceStack> context) {

        int count = IntegerArgumentType.getInteger(context, "count");
        int playerCount = context.getSource().getServer().getPlayerCount();

        if (count > playerCount) {
            context.getSource().sendFailure(
                    Component.translatable("commands.sre.setrolecount.error.too_many_killers", count, playerCount));
            return 0;
        }

        GameConstants.RoleConfig.killerCount = count;

        context.getSource().sendSuccess(
                () -> Component.translatable("commands.sre.setrolecount.killer", count)
                        .withStyle(style -> style.withColor(0x00FF00)),
                true);
        return 1;
    }

    private static int setVigilanteCount(CommandContext<CommandSourceStack> context) {

        int count = IntegerArgumentType.getInteger(context, "count");
        int playerCount = context.getSource().getServer().getPlayerCount();

        if (count > playerCount) {
            context.getSource().sendFailure(
                    Component.translatable("commands.sre.setrolecount.too_many_vigilantes", count, playerCount));
            return 0;
        }

        GameConstants.RoleConfig.vigilanteCount = count;

        context.getSource().sendSuccess(
                () -> Component.translatable("commands.sre.setrolecount.vigilante", count)
                        .withStyle(style -> style.withColor(0x00FF00)),
                true);
        return 1;
    }
}