package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ReloadReadyAreaCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:reloadReadyArea")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reloadReadyArea(context.getSource())));
    }

    private static int reloadReadyArea(CommandSourceStack source) {

        AreasWorldComponent areasComponent = AreasWorldComponent.KEY.get(source.getLevel());
        areasComponent.reloadReadyArea();
        source.sendSuccess(
                () -> Component.translatable("commands.sre.reloadreadyarea.success")
                        .withStyle(style -> style.withColor(0x00FF00)),
                true);
        return 1;
    }
}