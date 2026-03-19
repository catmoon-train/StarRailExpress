package org.agmas.noellesroles.client.commands;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import org.agmas.noellesroles.client.screen.GameManagementScreen;

import io.wifi.starrailexpress.client.util.ClientScheduler;

public class SettingsCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> {
                    dispatcher.register(ClientCommandManager.literal("tmm:gameManagePanel")
                            .executes(context -> {
                                if (context.getSource().getPlayer().hasPermissions(2)) {
                                    ClientScheduler.schedule(() -> {
                                        context.getSource().getClient().setScreen(new GameManagementScreen());
                                    }, 1);
                                } else {
                                    context.getSource()
                                            .sendError(Component.literal("You do not have permission to do that!")
                                                    .withStyle(ChatFormatting.RED));
                                }
                                return 1;
                            }));
                });
    }
}
