package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SetBoundCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:enableBounds")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> execute(context.getSource(),
                                BoolArgumentType.getBool(context, "enabled")))));
    }

    private static int execute(CommandSourceStack source, boolean enabled) {

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(source.getLevel());
        gameWorldComponent.setBound(enabled);

        if (enabled) {
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.setbound.enabled")
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
        } else {
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.setbound.disabled")
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
        }
        return 1;
    }

}
