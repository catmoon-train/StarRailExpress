package org.agmas.noellesroles.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.fake_steve.FakeSteveDirector;
import org.agmas.noellesroles.game.fake_steve.ReplacementCause;

public final class FakeSteveCommand {
    private FakeSteveCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("sre:fake_steve")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("event").executes(FakeSteveCommand::event))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(FakeSteveCommand::spawn)))
                        .then(Commands.literal("replace")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(FakeSteveCommand::replace)))));
    }

    private static int event(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        if (!validate(context.getSource(), level)) {
            return 0;
        }
        if (!FakeSteveDirector.queueApparition(level)) {
            context.getSource().sendFailure(Component.translatable("command.noellesroles.fake_steve.invalid_round"));
            return 0;
        }
        int queued = FakeSteveDirector.pendingEvents(level);
        context.getSource().sendSuccess(
                () -> Component.translatable("command.noellesroles.fake_steve.event_queued", queued), true);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (!validate(context.getSource(), target.serverLevel())) {
            return 0;
        }
        if (!FakeSteveDirector.spawnApparition(target)) {
            context.getSource().sendFailure(Component.translatable(
                    "command.noellesroles.fake_steve.invalid_target", target.getDisplayName()));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("command.noellesroles.fake_steve.spawned", target.getDisplayName()), true);
        return 1;
    }

    private static int replace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (!validate(context.getSource(), target.serverLevel())) {
            return 0;
        }
        if (!FakeSteveDirector.replace(target, ReplacementCause.COMMAND)) {
            context.getSource().sendFailure(Component.translatable(
                    "command.noellesroles.fake_steve.invalid_target", target.getDisplayName()));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("command.noellesroles.fake_steve.replaced", target.getDisplayName()), true);
        return 1;
    }

    private static boolean validate(CommandSourceStack source, ServerLevel level) {
        if (!FakeSteveDirector.isEnabled()) {
            source.sendFailure(Component.translatable("command.noellesroles.fake_steve.disabled"));
            return false;
        }
        if (!FakeSteveDirector.canGenerate(level)) {
            source.sendFailure(Component.translatable("command.noellesroles.fake_steve.invalid_mode"));
            return false;
        }
        return true;
    }
}
