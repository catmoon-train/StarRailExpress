package org.agmas.noellesroles.client.commands;

import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import io.wifi.starrailexpress.client.util.ClientScheduler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.screen.GameManagementScreen;

public class SREClientCommand {
  public static void register() {
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess) -> {
          dispatcher.register(ClientCommandManager.literal("sre:client")
              .then(ClientCommandManager.literal("screen")
                  .then(ClientCommandManager.literal("GameManagePanel")
                      .executes(context -> {
                        if (context.getSource().getPlayer().hasPermissions(2)) {
                          ClientScheduler.schedule(() -> {
                            context.getSource().getClient()
                                .setScreen(new GameManagementScreen());
                          }, 1);
                        } else {
                          context.getSource()
                              .sendError(
                                  Component.literal(
                                      "You do not have permission to do that!")
                                      .withStyle(ChatFormatting.RED));
                        }
                        return 1;
                      }))
                  .then(ClientCommandManager.literal("config")
                      .executes(context -> {
                        ClientScheduler.schedule(() -> {
                          context.getSource().getClient()
                              .setScreen(new SettingMenuScreen(null));
                        }, 1);
                        return 1;
                      }))));
        });
  }
}
