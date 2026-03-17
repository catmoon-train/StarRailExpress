package io.wifi.starrailexpress.command;

import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.RoleShopHandler;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;

public class ConfigCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:config")
                .requires(source -> source.hasPermission(2))
                .executes(ConfigCommand::showConfig)
                .then(Commands.literal("reload")
                        .executes(ConfigCommand::reloadConfig))
                .then(Commands.literal("reset")
                        .executes(ConfigCommand::resetConfig)));
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            SREConfig.instance().reload();
            SREConfig.HANDLER.syncToClient(source.getServer());
            HarpyModLoaderConfig.HANDLER.load();
            NoellesRolesConfig.HANDLER.load();
            StupidExpressConfig.HANDLER.load();
            StupidExpressConfig.HANDLER.syncToClient(source.getServer());
            RoleShopHandler.shopRegister();
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.config.reload")
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
            SRE.LOGGER.info("Reloaded config by {}", source.getTextName());
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("commands.sre.config.reload.fail", e.getMessage()));
            SRE.LOGGER.error("配置重载失败", e);
            return 0;
        }
    }

    private static int resetConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            SREConfig.instance().reset();
            source.sendSuccess(
                    () -> Component.translatable("commands.sre.config.reset")
                            .withStyle(style -> style.withColor(0x00FF00)),
                    true);
            SRE.LOGGER.info("配置文件已由 {} 重置为默认值", source.getTextName());
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("commands.sre.config.reset.fail", e.getMessage()));
            SRE.LOGGER.error("配置重置失败", e);
            return 0;
        }
    }

    private static int showConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.header"), false);

        // 商店价格
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.shop_prices.header"), false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.shop_prices.knife", SREConfig.instance().knifePrice),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.shop_prices.revolver", SREConfig.instance().revolverPrice),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.shop_prices.grenade", SREConfig.instance().grenadePrice),
                false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.shop_prices.psycho_mode",
                SREConfig.instance().psychoModePrice), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.shop_prices.poison_vial",
                SREConfig.instance().poisonVialPrice), false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.shop_prices.scorpion", SREConfig.instance().scorpionPrice),
                false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.shop_prices.firecracker",
                SREConfig.instance().firecrackerPrice), false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.shop_prices.lockpick", SREConfig.instance().lockpickPrice),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.shop_prices.crowbar", SREConfig.instance().crowbarPrice),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.shop_prices.body_bag", SREConfig.instance().bodyBagPrice),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.shop_prices.blackout", SREConfig.instance().blackoutPrice),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.shop_prices.note", SREConfig.instance().notePrice), false);

        // 物品冷却时间
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.cooldowns.header"), false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.cooldowns.knife", SREConfig.instance().knifeCooldown),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.cooldowns.revolver", SREConfig.instance().revolverCooldown),
                false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.cooldowns.derringer",
                SREConfig.instance().derringerCooldown), false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.cooldowns.grenade", SREConfig.instance().grenadeCooldown),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.cooldowns.lockpick", SREConfig.instance().lockpickCooldown),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.cooldowns.crowbar", SREConfig.instance().crowbarCooldown),
                false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.cooldowns.body_bag", SREConfig.instance().bodyBagCooldown),
                false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.cooldowns.psycho_mode",
                SREConfig.instance().psychoModeCooldown), false);
        source.sendSuccess(
                () -> Component.translatable("commands.sre.config.show.cooldowns.blackout", SREConfig.instance().blackoutCooldown),
                false);

        // 游戏设置
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.header"), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.starting_money",
                SREConfig.instance().startingMoney), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.passive_money_amount",
                SREConfig.instance().passiveMoneyAmount), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.passive_money_interval",
                SREConfig.instance().passiveMoneyInterval), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.money_per_kill",
                SREConfig.instance().moneyPerKill), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.psycho_mode_armor",
                SREConfig.instance().psychoModeArmor), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.psycho_mode_duration",
                SREConfig.instance().psychoModeDuration), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.firecracker_duration",
                SREConfig.instance().firecrackerDuration), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.blackout_min_duration",
                SREConfig.instance().blackoutMinDuration), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.blackout_max_duration",
                SREConfig.instance().blackoutMaxDuration), false);

        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.footer"), false);
        source.sendSuccess(() -> Component.translatable("commands.sre.config.show.hint"), false);

        return 1;
    }
}