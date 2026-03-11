package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.ScoreboardRoleSelectorComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class EnableWeightsCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:enableWeights")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    GameWorldComponent.KEY.get(context.getSource().getLevel()).setWeightsEnabled(enabled);
                                    ScoreboardRoleSelectorComponent.KEY.get(context.getSource()).reset();
                                    
                                    if (enabled) {
                                        context.getSource().sendSuccess(
                                            () -> Component.translatable("commands.sre.enableweights.enabled")
                                                .withStyle(style -> style.withColor(0x00FF00)),
                                            true
                                        );
                                    } else {
                                        context.getSource().sendSuccess(
                                            () -> Component.translatable("commands.sre.enableweights.disabled")
                                                .withStyle(style -> style.withColor(0x00FF00)),
                                            true
                                        );
                                    }
                                    return 1;
                                }))
        );
    }
}
