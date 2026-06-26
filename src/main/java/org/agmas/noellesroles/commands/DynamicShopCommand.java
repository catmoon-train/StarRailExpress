package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.Collection;

/**
 * 动态商店指令 / Dynamic shop command.
 *
 * <p>用于在运行时为玩家设置局内商店商品的动态价格（折扣 / 固定减价 / 乘数），底层写入每位玩家的
 * {@link DynamicShopComponent}。 / Configures the dynamic price (percentage discount / flat reduction /
 * multiplier) of in-game shop items per player at runtime, backed by each player's
 * {@link DynamicShopComponent}.
 *
 * <pre>
 * /dynamicshop discount   &lt;players&gt; &lt;item&gt; &lt;percent 0-100&gt;
 * /dynamicshop flat       &lt;players&gt; &lt;item&gt; &lt;amount&gt;
 * /dynamicshop multiplier &lt;players&gt; &lt;item&gt; &lt;multiplier&gt;
 * /dynamicshop clear      &lt;players&gt; &lt;item&gt;
 * /dynamicshop clearall   &lt;players&gt;
 * /dynamicshop query      &lt;players&gt; &lt;item&gt;
 * </pre>
 */
public final class DynamicShopCommand {
    private DynamicShopCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("dynamicshop")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("discount")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                                        .executes(DynamicShopCommand::executeDiscount)))))
                        .then(Commands.literal("flat")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(DynamicShopCommand::executeFlat)))))
                        .then(Commands.literal("multiplier")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(DynamicShopCommand::executeMultiplier)))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                                .executes(DynamicShopCommand::executeClear))))
                        .then(Commands.literal("clearall")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(DynamicShopCommand::executeClearAll)))
                        .then(Commands.literal("query")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                                .executes(DynamicShopCommand::executeQuery))))));
    }

    private static ResourceLocation getItemId(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Item item = ItemArgument.getItem(context, "item").getItem();
        return BuiltInRegistries.ITEM.getKey(item);
    }

    private static int executeDiscount(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        ResourceLocation itemId = getItemId(context);
        int percent = IntegerArgumentType.getInteger(context, "percent");
        for (ServerPlayer player : players) {
            DynamicShopComponent.KEY.get(player).setPercentDiscount(itemId, percent);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "已为 " + players.size() + " 名玩家设置 " + itemId + " 折扣 -" + percent + "%")
                .withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    private static int executeFlat(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        ResourceLocation itemId = getItemId(context);
        int amount = IntegerArgumentType.getInteger(context, "amount");
        for (ServerPlayer player : players) {
            DynamicShopComponent.KEY.get(player).setFlatReduction(itemId, amount);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "已为 " + players.size() + " 名玩家设置 " + itemId + " 固定减价 -" + amount)
                .withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    private static int executeMultiplier(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        ResourceLocation itemId = getItemId(context);
        double multiplier = DoubleArgumentType.getDouble(context, "multiplier");
        for (ServerPlayer player : players) {
            DynamicShopComponent.KEY.get(player).setMultiplier(itemId, multiplier);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "已为 " + players.size() + " 名玩家设置 " + itemId + " 价格乘数 x" + multiplier)
                .withStyle(ChatFormatting.GREEN), true);
        return players.size();
    }

    private static int executeClear(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        ResourceLocation itemId = getItemId(context);
        for (ServerPlayer player : players) {
            DynamicShopComponent.KEY.get(player).clearModifier(itemId);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "已清除 " + players.size() + " 名玩家的 " + itemId + " 价格修正")
                .withStyle(ChatFormatting.YELLOW), true);
        return players.size();
    }

    private static int executeClearAll(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        for (ServerPlayer player : players) {
            DynamicShopComponent.KEY.get(player).clearAllModifiers();
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "已清除 " + players.size() + " 名玩家的全部价格修正")
                .withStyle(ChatFormatting.YELLOW), true);
        return players.size();
    }

    private static int executeQuery(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        ResourceLocation itemId = getItemId(context);
        for (ServerPlayer player : players) {
            DynamicShopComponent component = DynamicShopComponent.KEY.get(player);
            boolean hasModifier = component.hasModifier(itemId);
            int sample = component.effectivePrice(itemId, 100);
            int purchases = component.getPurchaseCount(itemId);
            context.getSource().sendSuccess(() -> Component.literal(
                    player.getName().getString() + " · " + itemId
                            + " | 修正: " + (hasModifier ? "有" : "无")
                            + " | 基础100→" + sample
                            + " | 已购: " + purchases)
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return players.size();
    }
}
