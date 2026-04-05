package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoCheckS2CPacket;
import org.agmas.noellesroles.packet.OpenIntroPayload;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.util.Collection;

/**
 * 抽奖命令
 * 玩家调用抽奖命令，服务器进行抽奖并向玩家返回结果
 * 玩家根据结果包播放相关动画
 */
public class LootCommand {
  public static void register() {
    // 注册管理员命令
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("tmm:screen")
              .requires((t) -> Harpymodloader.isMojangVerify) // 支持正版
              .then(Commands.literal("intro").executes(LootCommand::openIntroUi)));
          dispatcher.register(Commands.literal("sre:loot")
              .requires((t) -> Harpymodloader.isMojangVerify) // 支持正版
              .executes(LootCommand::openLootScreen)
              .then(Commands.literal("screen")
                  .executes(LootCommand::openLootScreen))
              .then(Commands.literal("data")
                  .requires(source -> source.hasPermission(2))
                  .then(Commands.literal("chance")
                      .then(Commands.literal("add").then(Commands.argument("targets", EntityArgument.players())
                          .then(Commands.argument("chance", IntegerArgumentType.integer())
                              .executes(LootCommand::addOrDegreeChance)))))
                  .then(Commands.literal("coin")
                      .requires(source -> source.hasPermission(2))
                      .then(Commands.literal("add")
                          .then(Commands.argument("targets", EntityArgument.players())
                              .then(Commands.argument("number", IntegerArgumentType.integer())
                                  .executes(LootCommand::addOrDegreeCoin))))))
              .then(Commands.literal("coin2lottery")
                  .executes(ctx -> exchange(ctx, -1))
                  .then(Commands.argument("max_count", IntegerArgumentType.integer(1))
                      .executes(ctx -> exchange(ctx, IntegerArgumentType.getInteger(ctx, "max_count"))))));
        });
  }

  protected static int openIntroUi(CommandContext<CommandSourceStack> context) {
    try {
      ServerPlayer player = context.getSource().getPlayer();
      if (player == null)
        return 0;
      ServerPlayNetworking.send(player, new OpenIntroPayload(

      ));
      return 1;
    } catch (Exception e) {
      Noellesroles.LOGGER.error("[LootSys] Failed to send checkPacket\n", e);
      context.getSource()
          .sendFailure(Component.translatable("commands.loot.error.execute_fail", e.getMessage()));
      return 0;
    }
  }

  /** 打开抽奖界面 */
  protected static int openLootScreen(CommandContext<CommandSourceStack> context) {
    try {
      ServerPlayer player = context.getSource().getPlayer();
      if (player == null)
        return 0;
      ServerPlayNetworking.send(player, new LootPoolsInfoCheckS2CPacket(
          LotteryManager.getInstance().getPoolIDs()));
      return 1;
    } catch (Exception e) {
      Noellesroles.LOGGER.error("[LootSys] Failed to send checkPacket\n", e);
      context.getSource()
          .sendFailure(Component.translatable("commands.loot.error.execute_fail", e.getMessage()));
      return 0;
    }
  }

  protected static int addOrDegreeChance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    try {
      int count = IntegerArgumentType.getInteger(context, "chance");
      Collection<? extends ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
      for (ServerPlayer player : players) {
        LotteryManager.getInstance().addOrDegreeLotteryChance(player, count);
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        int currentLootChance = skinsComponent.getLootChance();
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.loot.success.add_or_degree_chance",
                player.getName(), count, currentLootChance),
            true);
      }
      return 1;
    } catch (Exception e) {
      Noellesroles.LOGGER.error("[LootSys] Failed to modify chance\n", e);
      context.getSource()
          .sendFailure(Component.translatable("commands.loot.error.execute_fail", e.getMessage()));
      return 0;
    }
  }

  /**
   * 执行兑换：花费 cost * chance 金币兑换 chance 次抽奖机会
   */
  private static int exchange(CommandContext<CommandSourceStack> context, int arg) {
    try {
      ServerPlayer player = context.getSource().getPlayer();
      if (player == null) {
        context.getSource().sendFailure(Component.translatable("commands.coin2lottery.error.no_player"));
        return 0;
      }
      SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
      int currentCoins = skinsComponent.getCoinNum();
      int currentLootChance = skinsComponent.getLootChance();
      int count = arg;
      if (arg == -1) {
        count = currentCoins / LotteryManager.baseLootConsumeCoin;
      }
      if (count <= 0) {
        // 不接受 0 以下的次数
        context.getSource().sendFailure(
            Component.translatable("commands.coin2lottery.error.unenble_change_the_count", count));
        return 0;
      }
      // 最大抽奖次数
      else if (count > 999) {
        context.getSource().sendFailure(
            Component.translatable("commands.coin2lottery.error.unenble_change_the_count", count));
        return 0;
      }
      // 价格
      final int cost = LotteryManager.baseLootConsumeCoin * count;
      // 检查是否有足够的金币
      if (currentCoins < cost) {
        context.getSource().sendFailure(
            Component.translatable("commands.coin2lottery.error.not_enough_coins", currentCoins, cost));
        return 0;
      }
      // 扣除 cost 金币
      skinsComponent.addCoinNum(-cost);
      // 增加 1 次抽奖机会
      skinsComponent.addLootChance(count);

      // 同步到客户端
      skinsComponent.sync();

      // 发送成功消息
      Component message = Component.translatable("commands.coin2lottery.success", cost, count, currentCoins - cost,
          currentLootChance + count);
      context.getSource().sendSuccess(
          () -> message,
          true);

      return 1;
    } catch (Exception e) {
      context.getSource()
          .sendFailure(Component.translatable("commands.coin2lottery.error.failed", e.getMessage()));
      return 0;
    }
  }

  protected static int addOrDegreeCoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    try {
      int count = IntegerArgumentType.getInteger(context, "number");
      Collection<? extends ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
      for (ServerPlayer player : players) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.addCoinNum(count);
        int currentCoins = skinsComponent.getCoinNum();
        context.getSource().sendSuccess(
            () -> Component.translatable("commands.coin.modifier.success.add",
                player.getName(), count, currentCoins),
            true);
      }
      return 1;
    } catch (Exception e) {
      context.getSource()
          .sendFailure(Component.translatable("commands.coin.modifier.error.failed", e.getMessage()));
      return 0;
    }
  }
}