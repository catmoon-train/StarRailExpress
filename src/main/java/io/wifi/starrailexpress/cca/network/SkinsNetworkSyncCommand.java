package io.wifi.starrailexpress.cca.network;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import net.exmo.sre.sync.MysqlPlayerDataStore;
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
                            SREPlayerSkinsComponent.disableGlobalNetworkSync();
                            return 1;
                        }))
                        .then(Commands.argument("host", StringArgumentType.word())
                                .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                        .then(Commands.argument("database", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String host = StringArgumentType.getString(ctx, "host");
                                                    int port = IntegerArgumentType.getInteger(ctx, "port");
                                                    String database = StringArgumentType.getString(ctx, "database");
                                                    return configureServer(ctx.getSource(), host, port, database);
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
     * 配置 MySQL 地址
     */
    private static int configureServer(CommandSourceStack source, String host, int port, String database) {
        try {
            SREConfig.instance().mysqlPlayerSyncEnabled = true;
            SREConfig.instance().mysqlSyncHost = host;
            SREConfig.instance().mysqlSyncPort = port;
            SREConfig.instance().mysqlSyncDatabase = database;
            SkinsNetworkSyncInitializer.isEnabled = true;
            SkinsNetworkSyncInitializer.setNetworkServer(host, port, database);
            MysqlPlayerDataStore.initializeFromConfig();
            source.sendSuccess(() -> Component.literal("§a皮肤 MySQL 同步已配置: " + host + ":" + port + "/" + database), true);
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
        String database = SkinsNetworkSyncInitializer.getNetworkKey();
        source.sendSuccess(() -> Component.literal("§6当前皮肤 MySQL 配置: " + host + ":" + port + "/" + database), false);
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
            SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            if (!component.isNetworkSyncEnabled()) {
                source.sendFailure(Component.literal("§c皮肤 MySQL 同步未启用"));
                return 0;
            }
            component.syncSkinsToNetwork();
            component.sync();

            source.sendSuccess(() -> Component.literal("§e正在同步皮肤数据到 MySQL..."), true);
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
            SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            if (!component.isNetworkSyncEnabled()) {
                source.sendFailure(Component.literal("§c皮肤 MySQL 同步未启用"));
                return 0;
            }

            component.pullSkinsFromNetwork();
            source.sendSuccess(() -> Component.literal("§a皮肤数据已从 MySQL 拉取"), true);
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
            SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            boolean enabled = component.isNetworkSyncEnabled();
            String status = enabled ? "§a已启用" : "§c已禁用";

            String host = SkinsNetworkSyncInitializer.getNetworkHost();
            int port = SkinsNetworkSyncInitializer.getNetworkPort();
                String database = SkinsNetworkSyncInitializer.getNetworkKey();
                String backend = MysqlPlayerDataStore.isAvailable() ? "§a连接可用" : "§c连接不可用";

            source.sendSuccess(() -> Component.literal(
                    "§6皮肤 MySQL 同步状态: " + status + "\n" +
                        "§6数据库连接: " + backend + "\n" +
                        "§6数据库地址: " + host + ":" + port + "/" + database),
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
            SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            component.initializeNetworkSync(
                    SkinsNetworkSyncInitializer.getNetworkHost(),
                    SkinsNetworkSyncInitializer.getNetworkPort(),
                    SkinsNetworkSyncInitializer.getNetworkKey());
            component.sync();

            source.sendSuccess(() -> Component.literal("§a皮肤 MySQL 同步已启用"), true);
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
            SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY.get(player);
            if (component == null) {
                source.sendFailure(Component.literal("§c无法获取玩家皮肤组件"));
                return 0;
            }

            component.disableNetworkSync();
            source.sendSuccess(() -> Component.literal("§a皮肤 MySQL 同步已禁用"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c禁用失败: " + e.getMessage()));
            return 0;
        }
    }
}
