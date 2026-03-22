package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.HoverEvent.ItemStackInfo;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.block_entity.VendingMachinesBlockEntity;

public class GoodsManagerCommand {
  public static void register() {
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("goods:add")
              .requires(source -> source.hasPermission(2))
              .then(Commands.argument("pos", BlockPosArgument.blockPos())
                  .then(Commands.literal("player")
                      .then(Commands.argument("player", EntityArgument.player())
                          .then(Commands.argument("price", IntegerArgumentType.integer(0))
                              .executes(GoodsManagerCommand::execute))))
                  .then(Commands.literal("item")
                      .then(Commands.argument("item", ItemArgument.item(registryAccess)).then(Commands.argument("count", IntegerArgumentType.integer(0))
                          .then(Commands.argument("price", IntegerArgumentType.integer(0))
                              .executes(GoodsManagerCommand::executesAddItem)))))));
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

  }

  private static int execute(CommandContext<CommandSourceStack> context) {
    try {
      // 获取参数
      BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
      ServerPlayer player = EntityArgument.getPlayer(context, "player");
      int price = IntegerArgumentType.getInteger(context, "price");

      // 获取方块实体
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);

      if (!(blockEntity instanceof VendingMachinesBlockEntity vendingEntity)) {
        context.getSource().sendFailure(Component.literal("指定位置不是自动售货机方块"));
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
      ShopEntry shopEntry = new ShopEntry(itemStack.copy(), price, ShopEntry.Type.TOOL);

      // 添加到自动售货机
      vendingEntity.addItem(shopEntry);

      // 发送成功消息
      context.getSource().sendSuccess(() -> Component.literal("成功添加商品: ")
          .append(itemStack.getDisplayName())
          .append(Component.literal(" 价格: $" + price))
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

      // 获取方块实体
      BlockEntity blockEntity = context.getSource().getLevel().getBlockEntity(pos);

      if (!(blockEntity instanceof VendingMachinesBlockEntity vendingEntity)) {
        context.getSource().sendFailure(Component.literal("指定位置不是自动售货机方块"));
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
      ShopEntry shopEntry = new ShopEntry(itemStack.copy(), price, ShopEntry.Type.TOOL);

      // 添加到自动售货机
      vendingEntity.addItem(shopEntry);

      // 发送成功消息
      context.getSource().sendSuccess(() -> Component.literal("成功添加商品: ")
          .append(itemStack.getDisplayName())
          .append(Component.literal(" 价格: $" + price))
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

      if (!(blockEntity instanceof VendingMachinesBlockEntity vendingEntity)) {
        context.getSource().sendFailure(Component.literal("指定位置不是自动售货机方块"));
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
      vendingEntity.removeItem(itemStack);

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

      if (!(blockEntity instanceof VendingMachinesBlockEntity vendingEntity)) {
        context.getSource().sendFailure(Component.literal("指定位置不是自动售货机方块"));
        return 0;
      }
      var items = vendingEntity.getShops();
      MutableComponent result = Component.translatable("The Shop List of [%s]", pos.toShortString())
          .withStyle(ChatFormatting.GOLD);
      for (var it : items) {
        Style itemHoverStyle = Style.EMPTY
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new ItemStackInfo(it.stack())))
            .withColor(ChatFormatting.GREEN);
        result.append(
            Component
                .translatable("\n%s(%s): %s",
                    Component.literal("").append(it.stack().getDisplayName())
                        .withStyle(itemHoverStyle),
                    it.stack().getCount(),
                    Component.literal(it.price() + "\uE781").withStyle(ChatFormatting.YELLOW))
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

      if (!(blockEntity instanceof VendingMachinesBlockEntity vendingEntity)) {
        context.getSource().sendFailure(Component.literal("指定位置不是自动售货机方块"));
        return 0;
      }
      var items = vendingEntity.getShops();
      if (stack < 0) {
        context.getSource().sendFailure(Component.literal("Value too small."));
        return 0;
      }
      if (stack >= items.size()) {
        context.getSource().sendFailure(Component.literal("Value too big."));
        return 0;
      }

      // 添加到自动售货机
      boolean result = vendingEntity.removeItemStack(stack);

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
}