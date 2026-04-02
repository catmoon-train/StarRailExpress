package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

/**
 * 金币兑换抽奖次数指令
 * 玩家可以花费 100 金币兑换 1 次抽奖机会
 */
public class CoinToLotteryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("coin2lottery")
                .executes(CoinToLotteryCommand::exchange));
    }

    /**
     * 执行兑换：花费 cost 金币兑换 1 次抽奖机会
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
            // 价格
            final int cost = LotteryManager.baseLootConsumeCoin;
            // 检查是否有足够的金币
            if (currentCoins < cost) {
                context.getSource().sendFailure(
                        Component.translatable("commands.coin2lottery.error.not_enough_coins", currentCoins, cost));
                return 0;
            }
            int count = currentCoins / cost;
            // 扣除 cost 金币
            skinsComponent.addCoinNum(-cost * count);
            // 增加 1 次抽奖机会
            skinsComponent.addLootChance(count);

            // 同步到客户端
            skinsComponent.sync();

            // 发送成功消息
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.coin2lottery.success", currentCoins - cost * count,
                            currentLootChance + count),
                    true);

            return 1;
        } catch (Exception e) {
            context.getSource()
                    .sendFailure(Component.translatable("commands.coin2lottery.error.failed", e.getMessage()));
            return 0;
        }
    }
}
