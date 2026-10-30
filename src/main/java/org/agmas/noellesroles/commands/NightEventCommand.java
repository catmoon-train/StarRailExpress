package org.agmas.noellesroles.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.night.NightEvent;

/** 管理员将黑夜事件排入下一局；进行中的对局不受命令即时影响。 */
public final class NightEventCommand {
    private NightEventCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("sre:night")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("next").executes(context -> {
                            if (!NightEvent.forceNextRound(context.getSource().getLevel())) {
                                context.getSource().sendFailure(Component.translatable("command.noellesroles.night.already_queued"));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.translatable("command.noellesroles.night.next_forced"), true);
                            return 1;
                        }))));
    }
}
