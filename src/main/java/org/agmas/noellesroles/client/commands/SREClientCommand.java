package org.agmas.noellesroles.client.commands;

import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import io.wifi.ConfigCompact.ui.TestScreen;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.NewspaperScreen;
import io.wifi.starrailexpress.client.util.ClientScheduler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.screen.GameManagementScreen;

import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

public class SREClientCommand {
  public static void register() {
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess) -> {
          dispatcher.register(ClientCommandManager.literal("sre:client")
              .then(
                  ClientCommandManager.literal("resource")
                      .then(ClientCommandManager.literal("reload")
                          .executes((ctx) -> {
                            ctx.getSource().getClient().reloadResourcePacks();
                            return 1;
                          })))
              .then(
                  ClientCommandManager.literal("chat")
                      .then(ClientCommandManager.literal("clear")
                          .executes((ctx) -> {
                            ctx.getSource().getClient().gui.getChat().clearMessages(false);
                            return 1;
                          })))
              .then(
                  ClientCommandManager.literal("chat")
                      .then(ClientCommandManager.literal("clear")
                          .executes((ctx) -> {
                            ctx.getSource().getClient().gui.getChat().clearMessages(false);
                            return 1;
                          })))
              .then(ClientCommandManager.literal("debug")
                  .requires(ctx -> ctx.hasPermission(2))
                  .then(ClientCommandManager.literal("client_area_config")
                      .executes((ctx) -> {
                        var key = AreasWorldComponent.KEY.get(ctx.getSource().getWorld());
                        final var GSON = new GsonBuilder().setPrettyPrinting().create();
                        String result = GSON.toJson(key.areasSettings);
                        ctx.getSource()
                            .sendFeedback(Component.literal(result)
                                .withStyle(ChatFormatting.GREEN));
                        return 1;
                      })))
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
                  .then(ClientCommandManager.literal("settings")
                      .executes(context -> {
                        ClientScheduler.schedule(() -> {
                          context.getSource().getClient()
                              .setScreen(new SettingMenuScreen(null));
                        }, 1);
                        return 1;
                      }))
                  .then(ClientCommandManager.literal("test")
                      .executes(context -> {
                        ClientScheduler.schedule(() -> {
                          context.getSource().getClient()
                              .setScreen(new TestScreen(null));
                        }, 1);
                        return 1;
                      }))

                  .then(ClientCommandManager.literal("newspaper_test")
                      .then(ClientCommandManager.literal("editing").executes(context -> {
                        ClientScheduler.schedule(() -> {
                          context.getSource().getClient()
                              .setScreen(new NewspaperScreen(new ArrayList<>(List.of("这是报纸测试页面"))));
                        }, 1);
                        return 1;
                      })).then(ClientCommandManager.literal("view").executes(context -> {
                        ClientScheduler.schedule(() -> {
                          var msg = new ArrayList<Component>();
                          msg.add(Component.translatable("Hello %s from %s", 1, 2).withStyle(ChatFormatting.RED));
                          context.getSource().getClient()
                              .setScreen(
                                  new NewspaperScreen(msg, Component.literal("hello"), Component.literal("author")));
                        }, 1);
                        return 1;
                      })))));
        });
  }
}
