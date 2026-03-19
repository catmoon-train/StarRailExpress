package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.ExtraSlotComponent;

public class ExtraItemsManagerCommand {
  public static void register() {
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess, environment) -> {
          dispatcher.register(Commands.literal("item")
              .requires(source -> source.hasPermission(2))
              .then(Commands.literal("extra")
                  .then(Commands.argument("player", EntityArgument.player())
                      .then(Commands.literal("set")
                          .then(Commands.argument("slot", ResourceLocationArgument.id())
                              .suggests(ExtraItemsManagerCommand::suggestsSlots)
                              .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                  .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                      .executes(ExtraItemsManagerCommand::executesSetItem)))))
                      .then(Commands.literal("list")).executes(ExtraItemsManagerCommand::executeList)
                      .then(Commands.literal("clear")).executes(ExtraItemsManagerCommand::executeClear))));
        });

  }

  public static CompletableFuture<Suggestions> suggestsSlots(CommandContext<CommandSourceStack> context,
      SuggestionsBuilder builder) {
    String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
      Set<String> suggestions = new HashSet<>();
      // 添加自定义 ID 到 Set
      ServerPlayer sp;
      try {
        sp = EntityArgument.getPlayer(context,"player");
      } catch (CommandSyntaxException e) {
        return builder.buildFuture();
      }
      var esc = ExtraSlotComponent.KEY.get(sp);
      esc.SLOTS.keySet().stream()
          .map(ResourceLocation::toString)
          .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
          .forEach(suggestions::add);
      // 最后批量建议
      suggestions.forEach((t) -> {
        if (t != null) {
          builder.suggest(t);
        }
      });

      return builder.buildFuture();
  }

  // suggestsSlots
  private static int executesSetItem(CommandContext<CommandSourceStack> context) {
    try {
      // 获取参数
      ServerPlayer player = EntityArgument.getPlayer(context, "player");
      int itemCount = IntegerArgumentType.getInteger(context, "count");
      ItemStack itemStack = ItemArgument.getItem(context, "item").createItemStack(itemCount, true);
      ResourceLocation slot = ResourceLocationArgument.getId(context, "slot");
      var esc = ExtraSlotComponent.KEY.get(player);
      itemStack.setCount(itemCount);
      // 获取物品
      if (itemStack == null || itemStack.isEmpty() || itemStack.is(Items.AIR)) {
        esc.removeSlot(slot);
        context.getSource().sendSuccess(
            () -> Component.translatable("Successfully remove the slot %s of player %s", slot.toString(),
                player.getDisplayName()),
            true);
        return 1;
      } else {
        esc.setItem(slot, itemStack);
      }

      context.getSource().sendSuccess(
          () -> Component.translatable("Successfully set the slot %s of player %s to %s", slot.toString(),
              player.getDisplayName(), GameReplayUtils.getItemStackDisplayNameWithCounts(itemStack)),
          true);
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("Error while setting items: " + e.getMessage()));
      return 0;
    }
  }

  private static int executeList(CommandContext<CommandSourceStack> context) {
    try {
      ServerPlayer player = EntityArgument.getPlayer(context, "player");
      var esc = ExtraSlotComponent.KEY.get(player);
      context.getSource().sendSuccess(
          () -> Component.translatable("The slots of player %s:",
              player.getDisplayName()).withStyle(ChatFormatting.GOLD),
          false);
      for (Entry<ResourceLocation, ItemStack> entry : esc.SLOTS.entrySet()) {
        var slot =entry.getKey();
        var it = entry.getValue();
        context.getSource().sendSystemMessage(Component.translatable("%s: %s",slot.toString(),GameReplayUtils.getItemStackDisplayNameWithCounts(it)).withStyle(ChatFormatting.WHITE));
      }
      ;
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("Runtime Error: " + e.getMessage()));
      return 0;
    }
  }

  private static int executeClear(CommandContext<CommandSourceStack> context) {
    try {
      ServerPlayer player = EntityArgument.getPlayer(context, "player");
      var esc = ExtraSlotComponent.KEY.get(player);
      esc.clear();
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      context.getSource().sendFailure(Component.literal("Runtime Error: " + e.getMessage()));
      return 0;
    }
  }
}