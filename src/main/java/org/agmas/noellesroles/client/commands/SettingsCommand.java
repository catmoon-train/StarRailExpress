package org.agmas.noellesroles.client.commands;


import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.agmas.noellesroles.client.screen.MathSolverScreen;
import org.agmas.noellesroles.client.screen.SettingsScreen;

public class SettingsCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess)-> {
                    dispatcher.register(ClientCommandManager.literal("Settings")
                            .executes(context-> {
                                var source = context.getSource();
                                source.getPlayer().sendSystemMessage(Component.literal("Settings"));
                                
                                source.getClient().setScreen(new MathSolverScreen());
                                return 1;
                            })
                    );
                }
        );
    }
}
