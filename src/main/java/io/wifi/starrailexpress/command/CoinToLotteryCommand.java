package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.util.SkinManager;

/**
 * 金币兑换抽奖次数指令
 * 玩家可以花费 100 金币兑换 1 次抽奖机会
 */
public class CoinToLotteryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher1, registryAccess, environment) -> {
                    dispatcher1.register(Commands.literal("coin2lottery")
                            .executes(CoinToLotteryCommand::exchange));
                });
    }

    /**
     * 执行兑换：花费 100 金币兑换 1 次抽奖机会
     */
    private static int exchange(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null) {
                context.getSource().sendFailure(Component.translatable("commands.coin2lottery.error.no_player"));
                return 0;
            }

            SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
            int currentCoins = skinsComponent.getCoinNum();
            int currentLootChance = skinsComponent.getLootChance();

            // 检查是否有足够的金币
            if (currentCoins < 100) {
                context.getSource().sendFailure(
                        Component.translatable("commands.coin2lottery.error.not_enough_coins", currentCoins));
                return 0;
            }

            // 扣除 100 金币
            skinsComponent.addCoinNum(-100);
            // 增加 1 次抽奖机会
            skinsComponent.addLootChance(1);

            // 同步到客户端
            skinsComponent.sync();

            // 发送成功消息
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.coin2lottery.success", currentCoins - 100, currentLootChance + 1),
                    true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.translatable("commands.coin2lottery.error.failed", e.getMessage()));
            return 0;
        }
    }
}
