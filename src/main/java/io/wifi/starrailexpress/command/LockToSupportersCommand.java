package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class LockToSupportersCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:lockToSupporters")
                .requires(source -> source.getPlayer() != null && source.getPlayer().getUUID().equals(UUID.fromString("1b44461a-f605-4b29-a7a9-04e649d1981c")))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> execute(context.getSource(), BoolArgumentType.getBool(context, "enabled"))))
        );
    }

    private static int execute(CommandSourceStack source, boolean value) {
        GameWorldComponent.KEY.get(source.getLevel()).setLockedToSupporters(value);
        
        if (value) {
            source.sendSuccess(
                () -> Component.translatable("commands.sre.locktosupporters.enabled")
                    .withStyle(style -> style.withColor(0x00FF00)),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.sre.locktosupporters.disabled")
                    .withStyle(style -> style.withColor(0x00FF00)),
                true
            );
        }
        return 1;
    }

}
