package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.cca.ScoreboardRoleSelectorComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jetbrains.annotations.NotNull;

public class CheckWeightsCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:checkWeights")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ScoreboardRoleSelectorComponent.KEY.get(context.getSource().getLevel().getScoreboard()).checkWeights(context.getSource());
                            return 1;
                        })
        );
    }
}
