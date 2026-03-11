package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.cca.ScoreboardRoleSelectorComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ResetWeightsCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:resetWeights").requires(source -> source.hasPermission(2)).executes(context -> {
            ScoreboardRoleSelectorComponent scoreboardRoleSelectorComponent = ScoreboardRoleSelectorComponent.KEY.get(context.getSource().getServer().getScoreboard());
            scoreboardRoleSelectorComponent.reset();
            context.getSource().sendSuccess(
                () -> Component.translatable("commands.sre.resetweights")
                    .withStyle(style -> style.withColor(0x00FF00)),
                true
            );
            return 1;
        }));
    }
}
