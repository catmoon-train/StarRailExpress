package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.HoverEvent.ItemStackInfo;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.content.block_entity.GoodsBindingStorage;
import org.agmas.noellesroles.content.block_entity.GoodsContainer;
import org.agmas.noellesroles.content.block_entity.LotteryMachineBlockEntity;
import org.agmas.noellesroles.content.block_entity.VendingMachinesBlockEntity;

public class GoodsManagerCommand {
  private static final SuggestionProvider<CommandSourceStack> CURRENCY_SUGGESTIONS =
      (context, builder) -> SharedSuggestionProvider.suggest(ShopEntry.Currency.serializedNames(), builder);
  private static final SuggestionProvider<CommandSourceStack> BINDING_FILE_SUGGESTIONS =
      (context, builder) -> SharedSuggestionProvider.suggest(
          GoodsBindingStorage.listNames(context.getSource().getServer()), builder);

  public static void register() {
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("goods:add")
              .requires(source -> source.hasPermission(2))
              .then(Commands.argument("pos", BlockPosArgument.blockPos())
                  .then(Commands.literal("player")
                      .then(Commands.argument("player", EntityArgument.player())
                          .then(Commands.argument("price", IntegerArgumentType.integer(0))
                              .executes(GoodsManagerCommand::execute)
                              .then(Commands.argument("currency", StringArgumentType.word())
                                  .suggests(CURRENCY_SUGGESTIONS)
                                  .executes(GoodsManagerCommand::execute)))))
                  .then(Commands.literal("item")
                      .then(Commands.argument("item", ItemArgument.item(registryAccess)).then(Commands.argument("count", IntegerArgumentType.integer(0))
                          .then(Commands.argument("price", IntegerArgumentType.integer(0))
                              .executes(GoodsManagerCommand::executesAddItem)
                              .then(Commands.argument("currency", StringArgumentType.word())
                                  .suggests(CURRENCY_SUGGESTIONS)
                                  .executes(GoodsManagerCommand::executesAddItem))))))));
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("goods:remove")
              .requires(source -> source.hasPermission(2))
              .then(Commands.argument("pos", BlockPosArgument.blockPos())
                  .then(Commands.literal("player")
                      .then(Commands.argument("player", EntityArgument.player())
                          .executes(GoodsManagerCommand::executeRemove)))
                  .then(Commands.literal("stack")
                      .then(Commands.argument("stack", IntegerArgumentType.integer(-1))
                          .executes(GoodsManagerCommand::executeRemoveStack)))));
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("goods:list")
              .requires(source -> source.hasPermission(2))
              .then(Commands.argument("pos", BlockPosArgument.blockPos())
                  .executes(GoodsManagerCommand::executeList)));
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("goods:cost")
              .requires(source -> source.hasPermission(2))
              .then(Commands.argument("pos", BlockPosArgument.blockPos())
                  .then(Commands.argument("price", IntegerArgumentType.integer(0))
                      .executes(GoodsManagerCommand::executeSetCost)
                      .then(Commands.argument("currency", StringArgumentType.word())
                          .suggests(CURRENCY_SUGGESTIONS)
                          .executes(GoodsManagerCommand::executeSetCost)))));
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("goods:lottery")
              .requires(source -> source.hasPermission(2))
              .then(Commands.literal("add")
                  .then(Commands.argument("pos", BlockPosArgument.blockPos())
                      .then(Commands.literal("player")
                          .then(Commands.argument("player", EntityArgument.player())
                              .then(Commands.argument("weight", IntegerArgumentType.integer(1))
                                  .executes(GoodsManagerCommand::executeLotteryAddPlayer))))
                      .then(Commands.literal("item")
                          .then(Commands.argument("item", ItemArgument.item(registryAccess))
                              .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                  .then(Commands.argument("weight", IntegerArgumentType.integer(1))
                                      .executes(GoodsManagerCommand::executeLotteryAddItem))))))));
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("goods:export")
              .requires(source -> source.hasPermission(2))
              .then(Commands.argument("pos", BlockPosArgument.blockPos())
                  .then(Commands.argument("name", StringArgumentType.string())
                      .suggests(BINDING_FILE_SUGGESTIONS)
                      .executes(GoodsManagerCommand::executeExport))));
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("goods:import")
              .requires(source -> source.hasPermission(2))
              .then(Commands.argument("pos", BlockPosArgument.blockPos())
                  .then(Commands.argument("name", StringArgumentType.string())
                      .suggests(BINDING_FILE_SUGGESTIONS)
                      .executes(GoodsManagerCommand::executeImport))));
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("goods:unbind")
              .requires(source -> source.hasPermission(2))
              .then(Commands.argument("pos", BlockPosArgument.blockPos())
                  .executes(GoodsManagerCommand::executeUnbind)));
        });

  }

  private static int execute(CommandContext<CommandSourceStack> context) {
    try {
      // 获取参数
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      ServerPlayer player = EntityArgument.getPlayer(context, "player");
      int price = IntegerArgumentType.getInteger(context, "price");
      ShopEntry.Currency currency = getCurrency(context);

      // 获取方块实体
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);

      GoodsContainer goodsContainer = asGoodsContainer(blockEntity);
      if (goodsContainer == null) {
        context.getSource().sendFailure(Component.literal("指定位置不是售货机或抽奖机方块"));
        return 0;
      }

      // 获取玩家主手物品
      ItemStack itemStack = player.getMainHandItem();
      if (itemStack.isEmpty()) {
        context.getSource().sendFailure(Component.literal("玩家主手没有物品"));
        return 0;
      }

      // 验证物品有效性
      if (itemStack.getItem() == null) {
        context.getSource().sendFailure(Component.literal("物品无效"));
        return 0;
      }

      // 创建商店条目
      ShopEntry shopEntry = new ShopEntry(itemStack.copy(), price, ShopEntry.Type.TOOL, currency);

      // 添加到自动售货机
      goodsContainer.addItem(shopEntry);

      // 发送成功消息
      context.getSource().sendSuccess(() -> Component.literal("成功添加商品: ")
          .append(itemStack.getDisplayName())
          .append(Component.literal(" 价格: " + price + " " + currency.serializedName()))
          .append(Component.literal(" 到位置: " + pos.toShortString())),
          true);

      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("添加商品时发生错误: " + e.getMessage()));
      return 0;
    }
  }
private static int executesAddItem(CommandContext<CommandSourceStack> context) {
    try {
      // 获取参数
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      int itemCount = IntegerArgumentType.getInteger(context, "count");
      ItemStack itemStack = ItemArgument.getItem(context, "item").createItemStack(itemCount, true);
      int price = IntegerArgumentType.getInteger(context, "price");
      ShopEntry.Currency currency = getCurrency(context);

      // 获取方块实体
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);

      GoodsContainer goodsContainer = asGoodsContainer(blockEntity);
      if (goodsContainer == null) {
        context.getSource().sendFailure(Component.literal("指定位置不是售货机或抽奖机方块"));
        return 0;
      }

      // 获取物品
      if (itemStack.isEmpty()) {
        context.getSource().sendFailure(Component.literal("无效的物品"));
        return 0;
      }

      // 验证物品有效性
      if (itemStack.getItem() == null) {
        context.getSource().sendFailure(Component.literal("无效的物品"));
        return 0;
      }

      // 创建商店条目
      ShopEntry shopEntry = new ShopEntry(itemStack.copy(), price, ShopEntry.Type.TOOL, currency);

      // 添加到自动售货机
      goodsContainer.addItem(shopEntry);

      // 发送成功消息
      context.getSource().sendSuccess(() -> Component.literal("成功添加商品: ")
          .append(itemStack.getDisplayName())
          .append(Component.literal(" 价格: " + price + " " + currency.serializedName()))
          .append(Component.literal(" 到位置: " + pos.toShortString())),
          true);
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("添加商品时发生错误: " + e.getMessage()));
      return 0;
    }
  }
  private static int executeRemove(CommandContext<CommandSourceStack> context) {
    try {
      // 获取参数
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      ServerPlayer player = EntityArgument.getPlayer(context, "player");

      // 获取方块实体
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);

      GoodsContainer goodsContainer = asGoodsContainer(blockEntity);
      if (goodsContainer == null) {
        context.getSource().sendFailure(Component.literal("指定位置不是售货机或抽奖机方块"));
        return 0;
      }

      // 获取玩家主手物品
      ItemStack itemStack = player.getMainHandItem();
      if (itemStack.isEmpty()) {
        context.getSource().sendFailure(Component.literal("玩家主手没有物品"));
        return 0;
      }

      // 验证物品有效性
      if (itemStack.getItem() == null) {
        context.getSource().sendFailure(Component.literal("物品无效"));
        return 0;
      }

      // 创建商店条目

      // 添加到自动售货机
      goodsContainer.removeItem(itemStack);

      // 发送成功消息
      context.getSource().sendSuccess(() -> Component.literal("成功删除商品: ")
          .append(itemStack.getDisplayName())
          .append(Component.literal(" 到位置: " + pos.toShortString())),
          true);

      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("删除商品时发生错误: " + e.getMessage()));
      return 0;
    }
  }

  private static int executeList(CommandContext<CommandSourceStack> context) {
    try {
      // 获取参数
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");

      // 获取方块实体
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);

      GoodsContainer goodsContainer = asGoodsContainer(blockEntity);
      if (goodsContainer == null) {
        context.getSource().sendFailure(Component.literal("指定位置不是售货机或抽奖机方块"));
        return 0;
      }
      var items = goodsContainer.getShops();
      MutableComponent result = Component.translatable("The Shop List of [%s]", pos.toShortString())
          .withStyle(ChatFormatting.GOLD);
      if (goodsContainer.getBoundFile() != null) {
        result.append(Component.literal("\n已绑定文件: " + goodsContainer.getBoundFile() + GoodsBindingStorage.EXTENSION)
            .withStyle(ChatFormatting.LIGHT_PURPLE));
      }
      if (blockEntity instanceof LotteryMachineBlockEntity lotteryMachine) {
        result.append(Component.literal("\n抽奖费用: " + lotteryMachine.getDrawCost() + " "
            + lotteryMachine.getDrawCurrency().serializedName()).withStyle(ChatFormatting.YELLOW));
      }
      int totalWeight = blockEntity instanceof LotteryMachineBlockEntity
          ? items.stream().mapToInt(ShopEntry::weight).sum()
          : 0;
      for (var it : items) {
        Style itemHoverStyle = Style.EMPTY
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new ItemStackInfo(it.stack())))
            .withColor(ChatFormatting.GREEN);
        Component detail = Component.literal(it.price() + " " + it.currency().serializedName())
            .withStyle(ChatFormatting.YELLOW);
        if (blockEntity instanceof LotteryMachineBlockEntity) {
          double chance = totalWeight <= 0 ? 0.0 : (double) it.weight() * 100.0 / totalWeight;
          detail = Component.literal(String.format(java.util.Locale.ROOT, "权重 %d / %.2f%%", it.weight(), chance))
              .withStyle(ChatFormatting.YELLOW);
        }
        result.append(
            Component
                .translatable("\n%s(%s): %s",
                    Component.literal("").append(it.stack().getDisplayName())
                        .withStyle(itemHoverStyle),
                    it.stack().getCount(),
                    detail)
                .withStyle(ChatFormatting.AQUA));
      }
      // 发送成功消息
      context.getSource().sendSuccess(() -> result,
          true);

      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("删除商品时发生错误: " + e.getMessage()));
      return 0;
    }
  }

  private static int executeRemoveStack(CommandContext<CommandSourceStack> context) {
    try {
      // 获取参数
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      int stack = IntegerArgumentType.getInteger(context, "stack");

      // 获取方块实体
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);

      GoodsContainer goodsContainer = asGoodsContainer(blockEntity);
      if (goodsContainer == null) {
        context.getSource().sendFailure(Component.literal("指定位置不是售货机或抽奖机方块"));
        return 0;
      }
      var items = goodsContainer.getShops();
      if (stack < 0) {
        context.getSource().sendFailure(Component.literal("Value too small."));
        return 0;
      }
      if (stack >= items.size()) {
        context.getSource().sendFailure(Component.literal("Value too big."));
        return 0;
      }

      // 添加到自动售货机
      boolean result = goodsContainer.removeItemStack(stack);

      // 发送成功消息
      if (result) {
        context.getSource().sendSuccess(() -> Component.literal("成功删除商品: ")
            .append(String.valueOf(stack) + "(")
            .append(items.get(stack).stack().getDisplayName()).append(")")
            .append(Component.literal(" 到位置: " + pos.toShortString())),
            true);
      }

      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("删除商品时发生错误: " + e.getMessage()));
      return 0;
    }
  }

  private static int executeLotteryAddPlayer(CommandContext<CommandSourceStack> context) {
    try {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      ServerPlayer player = EntityArgument.getPlayer(context, "player");
      int weight = IntegerArgumentType.getInteger(context, "weight");
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);
      if (!(blockEntity instanceof LotteryMachineBlockEntity lotteryMachine)) {
        context.getSource().sendFailure(Component.literal("指定位置不是抽奖机方块"));
        return 0;
      }
      ItemStack itemStack = player.getMainHandItem();
      if (itemStack.isEmpty()) {
        context.getSource().sendFailure(Component.literal("玩家主手没有物品"));
        return 0;
      }
      addLotteryPrize(context, lotteryMachine, pos, itemStack.copy(), weight);
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("添加抽奖奖品时发生错误: " + e.getMessage()));
      return 0;
    }
  }

  private static int executeLotteryAddItem(CommandContext<CommandSourceStack> context) {
    try {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      int itemCount = IntegerArgumentType.getInteger(context, "count");
      int weight = IntegerArgumentType.getInteger(context, "weight");
      ItemStack itemStack = ItemArgument.getItem(context, "item").createItemStack(itemCount, true);
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);
      if (!(blockEntity instanceof LotteryMachineBlockEntity lotteryMachine)) {
        context.getSource().sendFailure(Component.literal("指定位置不是抽奖机方块"));
        return 0;
      }
      if (itemStack.isEmpty()) {
        context.getSource().sendFailure(Component.literal("无效的物品"));
        return 0;
      }
      addLotteryPrize(context, lotteryMachine, pos, itemStack.copy(), weight);
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("添加抽奖奖品时发生错误: " + e.getMessage()));
      return 0;
    }
  }

  private static void addLotteryPrize(CommandContext<CommandSourceStack> context,
      LotteryMachineBlockEntity lotteryMachine, BlockPos pos, ItemStack itemStack, int weight) {
    ShopEntry shopEntry = new ShopEntry(itemStack.copy(), 0, ShopEntry.Type.TOOL, ShopEntry.Currency.MONEY, weight);
    lotteryMachine.addItem(shopEntry);
    int totalWeight = lotteryMachine.getShops().stream().mapToInt(ShopEntry::weight).sum();
    double chance = totalWeight <= 0 ? 0.0 : (double) shopEntry.weight() * 100.0 / totalWeight;
    context.getSource().sendSuccess(() -> Component.literal("成功添加抽奖奖品: ")
        .append(itemStack.getDisplayName())
        .append(Component.literal(" 权重: " + shopEntry.weight()))
        .append(Component.literal(String.format(java.util.Locale.ROOT, " 当前概率: %.2f%%", chance)))
        .append(Component.literal(" 到位置: " + pos.toShortString())), true);
  }

  private static ShopEntry.Currency getCurrency(CommandContext<CommandSourceStack> context) {
    try {
      return ShopEntry.Currency.fromSerializedName(StringArgumentType.getString(context, "currency"));
    } catch (IllegalArgumentException ignored) {
      return ShopEntry.Currency.MONEY;
    }
  }

  private static int executeSetCost(CommandContext<CommandSourceStack> context) {
    try {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      int price = IntegerArgumentType.getInteger(context, "price");
      ShopEntry.Currency currency = getCurrency(context);
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);
      if (!(blockEntity instanceof LotteryMachineBlockEntity lotteryMachine)) {
        context.getSource().sendFailure(Component.literal("指定位置不是抽奖机方块"));
        return 0;
      }
      lotteryMachine.setDrawCost(price, currency);
      context.getSource().sendSuccess(() -> Component.literal("成功设置抽奖费用: ")
          .append(Component.literal(price + " " + currency.serializedName()))
          .append(Component.literal(" 到位置: " + pos.toShortString())), true);
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("设置抽奖费用时发生错误: " + e.getMessage()));
      return 0;
    }
  }

  private static int executeExport(CommandContext<CommandSourceStack> context) {
    try {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      String name = GoodsBindingStorage.sanitize(StringArgumentType.getString(context, "name"));
      if (name == null) {
        context.getSource().sendFailure(Component.literal("无效的文件名（仅允许字母、数字、下划线、连字符）"));
        return 0;
      }
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);
      GoodsContainer goodsContainer = asGoodsContainer(blockEntity);
      if (goodsContainer == null) {
        context.getSource().sendFailure(Component.literal("指定位置不是售货机或抽奖机方块"));
        return 0;
      }
      MinecraftServer server = context.getSource().getServer();
      int count = goodsContainer.getShops().size();
      GoodsBindingStorage.write(server, name, goodsContainer.toBindingTag(context.getSource().registryAccess()));
      context.getSource().sendSuccess(() -> Component.literal("已导出 " + count + " 件商品到 ")
          .append(Component.literal(GoodsBindingStorage.DIR_NAME + "/" + name + GoodsBindingStorage.EXTENSION)
              .withStyle(ChatFormatting.GOLD)), true);
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("导出商品时发生错误: " + e.getMessage()));
      return 0;
    }
  }

  private static int executeImport(CommandContext<CommandSourceStack> context) {
    try {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      String name = GoodsBindingStorage.sanitize(StringArgumentType.getString(context, "name"));
      if (name == null) {
        context.getSource().sendFailure(Component.literal("无效的文件名（仅允许字母、数字、下划线、连字符）"));
        return 0;
      }
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);
      GoodsContainer goodsContainer = asGoodsContainer(blockEntity);
      if (goodsContainer == null) {
        context.getSource().sendFailure(Component.literal("指定位置不是售货机或抽奖机方块"));
        return 0;
      }
      MinecraftServer server = context.getSource().getServer();
      if (!GoodsBindingStorage.exists(server, name)) {
        context.getSource().sendFailure(Component.literal("绑定文件不存在: "
            + GoodsBindingStorage.DIR_NAME + "/" + name + GoodsBindingStorage.EXTENSION));
        return 0;
      }
      goodsContainer.setBoundFile(name);
      int count = goodsContainer.getShops().size();
      context.getSource().sendSuccess(() -> Component.literal("已绑定到文件 ")
          .append(Component.literal(GoodsBindingStorage.DIR_NAME + "/" + name + GoodsBindingStorage.EXTENSION)
              .withStyle(ChatFormatting.GOLD))
          .append(Component.literal("，当前 " + count + " 件商品（编辑该文件后将自动同步）")), true);
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("绑定文件时发生错误: " + e.getMessage()));
      return 0;
    }
  }

  private static int executeUnbind(CommandContext<CommandSourceStack> context) {
    try {
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);
      GoodsContainer goodsContainer = asGoodsContainer(blockEntity);
      if (goodsContainer == null) {
        context.getSource().sendFailure(Component.literal("指定位置不是售货机或抽奖机方块"));
        return 0;
      }
      String previous = goodsContainer.getBoundFile();
      if (previous == null) {
        context.getSource().sendFailure(Component.literal("该机器未绑定任何文件"));
        return 0;
      }
      goodsContainer.setBoundFile(null);
      context.getSource().sendSuccess(() -> Component.literal("已解绑文件 ")
          .append(Component.literal(previous + GoodsBindingStorage.EXTENSION).withStyle(ChatFormatting.GOLD))
          .append(Component.literal("，当前商品保留为本地副本")), true);
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("解绑文件时发生错误: " + e.getMessage()));
      return 0;
    }
  }

  private static GoodsContainer asGoodsContainer(BlockEntity blockEntity) {
    if (blockEntity instanceof GoodsContainer goodsContainer) {
      return goodsContainer;
    }
    return null;
  }
}
