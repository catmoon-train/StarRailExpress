package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.screen.GameManagementScreen;
import org.agmas.noellesroles.client.screen.GuessRoleScreen;
import org.agmas.noellesroles.client.screen.LootInfoScreen;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoCheckC2SPacket;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.util.ArrayList;
import java.util.function.Consumer;

public class GameMenuEntries {
    public static final int menuButtonHeight = 20;
    public static final int menuButtonWidth = 100;

    public static ArrayList<Button> register(int width, int height, Minecraft minecraft, Screen parent,
            Consumer<Boolean> toggleViewMenu) {
        ArrayList<Button> menuSelections = new ArrayList<>();
        menuSelections.clear();
        {
            int startY = height - menuButtonHeight;
            // 添加菜单按钮
            {
                // 职业介绍
                var btn1 =Button
                        .builder(Component.translatable("screen.limited_inventory.menu.introduction"), (btn) -> {
                            var role = SREGameWorldComponent.KEY.get(minecraft.level)
                                    .getRole(minecraft.player);
                            var screen = new RoleIntroduceScreen(parent, role);
                            minecraft.setScreen(screen);
                            toggleViewMenu.accept(false);
                        }).bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                menuSelections.add(btn1);
                startY -= menuButtonHeight;
            }
            {
                // 抽卡页面
                var btn1 = Button
                        .builder(Component.translatable("screen.limited_inventory.menu.loot_screen"), (btn) -> {
                            if (LotteryManager.getInstance().getLotteryPools().isEmpty())
                                ClientPlayNetworking.send(new LootPoolsInfoCheckC2SPacket());
                            minecraft.setScreen(new LootInfoScreen(0, 0, 0, parent));
                            toggleViewMenu.accept(false);
                        }).bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                menuSelections.add(btn1);
                startY -= menuButtonHeight;
            }
            {
                // 职业猜测
                var btn1 = Button
                        .builder(Component.translatable("screen.limited_inventory.menu.role_guess"), (btn) -> {
                            var screen = new GuessRoleScreen(parent);
                            minecraft.setScreen(screen);
                            toggleViewMenu.accept(false);
                        }).bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                menuSelections.add(btn1);
                startY -= menuButtonHeight;
            }
            if (minecraft.player.hasPermissions(2)) {
                // mod_settings
                var btn1 = Button
                        .builder(Component.translatable("screen.limited_inventory.menu.mod_settings")
                                .withStyle(ChatFormatting.RED), (btn) -> {
                                    var screen = new SettingMenuScreen(parent);
                                    minecraft.setScreen(screen);
                                    toggleViewMenu.accept(false);
                                })
                        .bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                menuSelections.add(btn1);
                startY -= menuButtonHeight;
            } else {
                // mod client settings
                var btn1 = Button
                        .builder(Component.translatable("screen.limited_inventory.menu.mod_settings_client")
                                .withStyle(ChatFormatting.WHITE), (btn) -> {
                                    var screen = SREClientConfig.HANDLER.generateGui().generateScreen(parent);
                                    minecraft.setScreen(screen);
                                    toggleViewMenu.accept(false);
                                })
                        .bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                menuSelections.add(btn1);
                startY -= menuButtonHeight;
            }
            if (minecraft.player.hasPermissions(2)) {
                // game_menu
                var btn1 = Button
                        .builder(Component.translatable("screen.limited_inventory.menu.game_menu")
                                .withStyle(ChatFormatting.RED), (btn) -> {
                                    var screen = new GameManagementScreen(parent);
                                    minecraft.setScreen(screen);
                                    toggleViewMenu.accept(false);
                                })
                        .bounds(width - menuButtonWidth, startY - menuButtonHeight, menuButtonWidth, menuButtonHeight)
                        .build();
                menuSelections.add(btn1);
                startY -= menuButtonHeight;
            }
        }
        return menuSelections;
    }
}
