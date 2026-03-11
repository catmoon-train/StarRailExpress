package io.wifi.starrailexpress.cca.network;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.wifi.starrailexpress.cca.PlayerSkinsComponent;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 皮肤网络同步命令
 * 用于管理和调试皮肤网络同步
 */
public class SkinsNetworkSyncCommand {

    /**
     * 注册命令
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:skinsync")
                .requires((p) -> p.hasPermission(2))
                .then(Commands.literal("config")
                        .then(Commands.literal("stop").executes((ctx) -> {
                            SkinsNetworkSyncInitializer.isEnabled = false;
                            PlayerSkinsComponent.disableGlobalNetworkSync();
                            SREConfig.itemSkinSyncServerEnabled = false;
                            return 1;
                        }))
                        .then(Commands.argument("host", StringArgumentType.string())
                                .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                        .then(Commands.argument("host", StringArgumentType.string())
                                                .executes(ctx -> {
                                                    String host = StringArgumentType.getString(ctx, "host");
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    int port = IntegerArgumentType.getInteger(ctx, "port");
                                                    return configureServer(ctx.getSource(), host, port, key);
                                                })))
                                .executes(ctx -> {
                                    return showCurrentConfig(ctx.getSource());
                                })))
                .then(Commands.literal("sync")
                        .executes(ctx -> {
                            return syncNow(ctx.getSource());
                        }))
                .then(Commands.literal("pull")
                        .executes(ctx -> {
                            return pullNow(ctx.getSource());
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            return showStatus(ctx.getSource());
                        }))
                .then(Commands.literal("enable")
                        .executes(ctx -> {
                            return enableSync(ctx.getSource());
                        }))
                .then(Commands.literal("disable")
                        .executes(ctx -> {
                            return disableSync(ctx.getSource());
                        })));
    }

    /**
     * 配置服务器地址
     */
    private static int configureServer(CommandSourceStack source, String host, int port, String key) {
        try {
            SkinsNetworkSyncInitializer.isEnabled = true;
            SkinsNetworkSyncInitializer.setNetworkServer(host, port, key);
            source.sendSuccess(() -> Component.literal("§a皮肤网络同步服务器已配置: " + host + ":" + port), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c配置失败: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * 显示当前配置
     */
    private static int showCurrentConfig(CommandSourceStack source) {
        String host = SkinsNetworkSyncInitializer.getNetworkHost();
        int port = SkinsNetworkSyncInitializer.getNetworkPort();
        source.sendSuccess(() -> Component.literal("§6当前皮肤网络同步服务器: " + host + ":" + port), false);
        return 1;
    }

    /**
     * 立即同步
     */
    private static int syncNow(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c此命令只能由玩家执行"));
            return 0;
        }

        try {
            PlayerSkinsComponent component = PlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            if (!component.isNetworkSyncEnabled()) {
                source.sendFailure(Component.literal("§c皮肤网络同步未启用"));
                return 0;
            }
            component.syncSkinsToNetwork();
            component.sync();

            source.sendSuccess(() -> Component.literal("§e正在同步皮肤数据到服务器..."), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c同步失败: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * 立即拉取
     */
    private static int pullNow(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c此命令只能由玩家执行"));
            return 0;
        }

        try {
            PlayerSkinsComponent component = PlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            if (!component.isNetworkSyncEnabled()) {
                source.sendFailure(Component.literal("§c皮肤网络同步未启用"));
                return 0;
            }

            component.pullSkinsFromNetwork();
            source.sendSuccess(() -> Component.literal("§a皮肤数据已从服务器拉取"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c拉取失败: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * 显示同步状态
     */
    private static int showStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c此命令只能由玩家执行"));
            return 0;
        }

        try {
            PlayerSkinsComponent component = PlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            boolean enabled = component.isNetworkSyncEnabled();
            String status = enabled ? "§a已启用" : "§c已禁用";

            String host = SkinsNetworkSyncInitializer.getNetworkHost();
            int port = SkinsNetworkSyncInitializer.getNetworkPort();

            source.sendSuccess(() -> Component.literal(
                    "§6皮肤网络同步状态: " + status + "\n" +
                            "§6服务器地址: " + host + ":" + port),
                    false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c获取状态失败: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * 启用同步
     */
    private static int enableSync(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c此命令只能由玩家执行"));
            return 0;
        }

        try {
            PlayerSkinsComponent component = PlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            String host = SkinsNetworkSyncInitializer.getNetworkHost();
            int port = SkinsNetworkSyncInitializer.getNetworkPort();
            String key = SkinsNetworkSyncInitializer.getNetworkKey();
            component.initializeNetworkSync(host, port, key);
            component.sync();

            source.sendSuccess(() -> Component.literal("§a皮肤网络同步已启用"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c启用失败: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * 禁用同步
     */
    private static int disableSync(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§c此命令只能由玩家执行"));
            return 0;
        }

        try {
            PlayerSkinsComponent component = PlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            component.disableNetworkSync();
            source.sendSuccess(() -> Component.literal("§a皮肤网络同步已禁用"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c禁用失败: " + e.getMessage()));
            return 0;
        }
    }
}
