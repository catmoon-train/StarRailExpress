package io.wifi.starrailexpress.client.gui.screen.ingame;

import io.wifi.ConfigCompact.ui.SettingMenuScreen;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.BackpackScreen;
import io.wifi.starrailexpress.client.gui.screen.MapIntroduceScreen;
import io.wifi.starrailexpress.client.gui.screen.SkinManagementScreen;
import io.wifi.starrailexpress.client.gui.screen.roster.RoleRosterEditScreen;
import io.wifi.starrailexpress.client.gui.screen.roster.RoleRosterViewScreen;
import net.exmo.sre.record.client.MatchRecordsScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.screen.GameManagementScreen;
import org.agmas.noellesroles.client.screen.GuessRoleScreen;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import java.util.ArrayList;
import java.util.function.Consumer;

public class GameMenuEntries {
    public static final int menuButtonHeight = 20;
    public static final int menuButtonWidth = 100;

    /** 一条便捷菜单项：标题 + 点击动作。用于在不同布局（右侧竖列 / 等待面板格子）中复用同一组动作。 */
    public record MenuEntry(Component label, Button.OnPress action) {
    }

    /**
     * 按权限构建便捷菜单的逻辑条目（不含位置）。右侧竖列与等待面板都基于这份列表生成按钮。
     */
    public static ArrayList<MenuEntry> entries(Minecraft minecraft, Screen parent, Consumer<Boolean> toggleViewMenu) {
        ArrayList<MenuEntry> entries = new ArrayList<>();
        // 职业介绍
        entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.introduction"), (btn) -> {
            var role = SREGameWorldComponent.KEY.get(minecraft.level).getRole(minecraft.player);
            minecraft.setScreen(new RoleIntroduceScreen(parent, role));
            toggleViewMenu.accept(false);
        }));
        // 地图介绍
        entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.map_introduction"), (btn) -> {

            minecraft.setScreen(new MapIntroduceScreen(parent));
            toggleViewMenu.accept(false);
        }));
        // 抽卡页面 —— 已禁用
        // entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.loot_screen"), (btn) -> {
        //     if (LotteryManager.getInstance().getLotteryPools().isEmpty())
        //         ClientPlayNetworking.send(new LootPoolsInfoCheckC2SPacket());
        //     minecraft.setScreen(new LootInfoScreen(0, 0, 0, parent));
        //     toggleViewMenu.accept(false);
        // }));
        // 职业猜测
        entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.role_guess"), (btn) -> {
            minecraft.setScreen(new GuessRoleScreen(parent));
            toggleViewMenu.accept(false);
        }));
        if (minecraft.player.hasPermissions(2)) {
            // mod_settings
            entries.add(new MenuEntry(
                    Component.translatable("screen.limited_inventory.menu.mod_settings").withStyle(ChatFormatting.RED),
                    (btn) -> {
                        minecraft.setScreen(new SettingMenuScreen(parent));
                        toggleViewMenu.accept(false);
                    }));
        } else {
            // mod client settings
            entries.add(new MenuEntry(
                    Component.translatable("screen.limited_inventory.menu.mod_settings_client")
                            .withStyle(ChatFormatting.WHITE),
                    (btn) -> {
                        minecraft.setScreen(SREClientConfig.HANDLER.generateGui().generateScreen(parent));
                        toggleViewMenu.accept(false);
                    }));
        }
        if (minecraft.player.hasPermissions(2)) {
            // game_menu
            entries.add(new MenuEntry(
                    Component.translatable("screen.limited_inventory.menu.game_menu").withStyle(ChatFormatting.RED),
                    (btn) -> {
                        minecraft.setScreen(new GameManagementScreen(parent));
                        toggleViewMenu.accept(false);
                    }));
        }
        return entries;
    }
    public static ArrayList<MenuEntry> entries_hub(Minecraft minecraft, Screen parent, Consumer<Boolean> toggleViewMenu) {
        ArrayList<MenuEntry> entries = new ArrayList<>();
        // 职业介绍
        entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.introduction"), (btn) -> {
            var role = SREGameWorldComponent.KEY.get(minecraft.level).getRole(minecraft.player);
            minecraft.setScreen(new RoleIntroduceScreen(parent, role));
            toggleViewMenu.accept(false);
        }));
        entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.map_introduction"), (btn) -> {

            minecraft.setScreen(new MapIntroduceScreen(parent));
            toggleViewMenu.accept(false);
        }));
        // 地图轮换：同一个界面，管理员多出启用/停用按钮
        entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.map_rotation"), (btn) -> {
            minecraft.setScreen(new io.wifi.starrailexpress.client.gui.screen.maprotation.MapRotationScreen());
            toggleViewMenu.accept(false);
        }));
        // 战绩页面
        entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.records"), (btn) -> {

            minecraft.setScreen(new MatchRecordsScreen(parent));
            toggleViewMenu.accept(false);
        }));
        // 皮肤管理
        entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.skin_manage"), (btn) -> {
            minecraft.setScreen(new SkinManagementScreen(parent));
            toggleViewMenu.accept(false);
        }));
        // 库存管理
        entries.add(new MenuEntry(Component.translatable("screen.limited_inventory.menu.backpack"), (btn) -> {
            minecraft.setScreen(new BackpackScreen(parent));
            toggleViewMenu.accept(false);
        }));
        // 邮箱管理已移除：邮箱系统迁移至网站端。
        if (minecraft.player.hasPermissions(2)) {
            // mod_settings
            entries.add(new MenuEntry(
                    Component.translatable("screen.limited_inventory.menu.mod_settings").withStyle(ChatFormatting.RED),
                    (btn) -> {
                        minecraft.setScreen(new SettingMenuScreen(parent));
                        toggleViewMenu.accept(false);
                    }));
        } else {
            // mod client settings
            entries.add(new MenuEntry(
                    Component.translatable("screen.limited_inventory.menu.mod_settings_client")
                            .withStyle(ChatFormatting.WHITE),
                    (btn) -> {
                        minecraft.setScreen(SREClientConfig.HANDLER.generateGui().generateScreen(parent));
                        toggleViewMenu.accept(false);
                    }));
        }
        if (minecraft.player.hasPermissions(2)) {
            // game_menu
            entries.add(new MenuEntry(
                    Component.translatable("screen.limited_inventory.menu.game_menu").withStyle(ChatFormatting.RED),
                    (btn) -> {
                        minecraft.setScreen(new GameManagementScreen(parent));
                        toggleViewMenu.accept(false);
                    }));

        }
        if (minecraft.player.hasPermissions(2)) {
            //职业轮换
            entries.add(new MenuEntry(
                    Component.translatable("screen.limited_inventory.menu.roster").withStyle(ChatFormatting.RED),
                    (btn) -> {
                        minecraft.setScreen(new RoleRosterEditScreen());
                        toggleViewMenu.accept(false);
                    }));

        }else {
            entries.add(new MenuEntry(
                    Component.translatable("screen.limited_inventory.menu.roster").withStyle(ChatFormatting.WHITE),
                    (btn) -> {
                        minecraft.setScreen(new RoleRosterViewScreen());
                        toggleViewMenu.accept(false);
                    }));

        }
        return entries;
    }

    public static ArrayList<Button> register(int width, int height, Minecraft minecraft, Screen parent,
            Consumer<Boolean> toggleViewMenu) {
        ArrayList<Button> menuSelections = new ArrayList<>();
        int startY = height - menuButtonHeight;
        for (MenuEntry entry : entries(minecraft, parent, toggleViewMenu)) {
            startY -= menuButtonHeight;
            menuSelections.add(Button.builder(entry.label(), entry.action())
                    .bounds(width - menuButtonWidth, startY, menuButtonWidth, menuButtonHeight)
                    .build());
        }
        return menuSelections;
    }
}
