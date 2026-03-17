package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;

import io.wifi.starrailexpress.SREConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SetAutoTrainResetCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:setAutoTrainReset")
                        .requires(source -> source.hasPermission(2))
                        .then(
                                Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> execute(context.getSource(), BoolArgumentType.getBool(context, "enabled")))
                        )
        );
    }

    private static int execute(CommandSourceStack source, boolean enabled) {
        SREConfig.instance().enableAutoTrainReset = enabled;
        SREConfig.instance().HANDLER.save();

        source.sendSuccess(
                () -> Component.translatable("commands.sre.setautotrainreset", enabled)
                        .withStyle(style -> style.withColor(0x00FF00)),
                true
        );
        return 1;
    }
}