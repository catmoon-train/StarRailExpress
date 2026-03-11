package org.agmas.noellesroles.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.ModComponents;

public class AdminFreeCamCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("nr_free_cam")
                    .requires(source -> source.hasPermission(2))
                    .executes(AdminFreeCamCommand::execute));
        });
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            DeathPenaltyComponent component = ModComponents.DEATH_PENALTY.get(player);
            component.reset();
            context.getSource().sendSuccess(() -> Component.translatable("message.noellesroles.penalty.unlimit"), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            return 0;
        }
    }
}