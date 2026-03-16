package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.command.argument.SkinArgumentType;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.item.SkinableItem;
import io.wifi.starrailexpress.util.SkinManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static io.wifi.starrailexpress.SRE.LOGGER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ManageSkinsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {

        dispatcher.register(Commands.literal("tmm:manageskins")
                .requires(source -> source.hasPermission(2)) // 需要权限等级2
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .suggests(ManageSkinsCommand::suggestSkinableItems)
                                        .then(Commands.argument("skin", SkinArgumentType.string())
                                                .suggests(ManageSkinsCommand::suggestSkins)

                                                .executes(ManageSkinsCommand::giveSkin)))))
                .then(Commands.literal("take")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .suggests(ManageSkinsCommand::suggestSkinableItems)
                                        .then(Commands.argument("skin", SkinArgumentType.string())
                                                .suggests(ManageSkinsCommand::suggestSkins)

                                                .executes(ManageSkinsCommand::takeSkin)))))
                .then(Commands.literal("setequipped")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .suggests(ManageSkinsCommand::suggestSkinableItems)
                                        .then(Commands.argument("skin", SkinArgumentType.string()).suggests(null)
                                                .suggests(ManageSkinsCommand::suggestSkins)

                                                .executes(ManageSkinsCommand::setEquippedSkin)))))
                .then(Commands.literal("list")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ManageSkinsCommand::listSkins))));
    }

    private static int giveSkin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {

            Collection<? extends Player> players = EntityArgument.getPlayers(context, "targets");
            var item = ItemArgument.getItem(context, "item").getItem();
            String skinArgu = SkinArgumentType.getString(context, "skin");
            String itemId = null;
            ArrayList<String> skinArray = new ArrayList<>();
            if (item instanceof SkinableItem it) {
                itemId = it.getItemSkinType();
            }
            if (skinArgu == "*") {
                skinArray.addAll(SkinManager.getSkins(itemId).keySet());
            } else {
                skinArray.add(skinArgu);
            }
            if (itemId == null) {
                context.getSource().sendFailure(
                        Component.translatable("Not a supported Skinable Item!").withStyle(ChatFormatting.RED));
                return 0;
            }
            int successes = 0;
            for (var skin : skinArray) {
                for (Player player : players) {
                    // 解锁指定物品类型的皮肤
                    SkinManager.unlockSkinForItemType(player, itemId,
                            skin);
                    context.getSource().sendSystemMessage(Component.translatable(
                            "Gave skin %s for item type %s to %s", skin, item.getDescription(), player.getName()));

                    successes++;
                }
            }
            context.getSource().sendSuccess(
                    () -> Component.translatable("Give %s skins to %s players!", skinArray.size(), players.size()),
                    true);
            return successes;
        } catch (Exception ig) {
            LOGGER.error("Error occurred while executing manageskins command", ig);
            return 0;
        }
    }

    private static int takeSkin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Player> players = EntityArgument.getPlayers(context, "targets");
        var item = ItemArgument.getItem(context, "item").getItem();
        String skinArgu = SkinArgumentType.getString(context, "skin");
        String itemId = null;
        ArrayList<String> skinArray = new ArrayList<>();
        if (item instanceof SkinableItem it) {
            itemId = it.getItemSkinType();
        }
        if (skinArgu == "*") {
            skinArray.addAll(SkinManager.getSkins(itemId).keySet());
        } else {
            skinArray.add(skinArgu);
        }
        if (itemId == null) {
            context.getSource().sendFailure(
                    Component.translatable("Not a supported Skinable Item!").withStyle(ChatFormatting.RED));
            return 0;
        }
        int successes = 0;
        for (var skin : skinArray) {
            for (Player player : players) {
                // 锁定（移除）指定物品类型的皮肤
                SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
                skinsComponent.lockSkinForItemType(itemId, skin);
                context.getSource().sendSystemMessage(Component.translatable(
                        "Removed skin %s for item type %s to %s", skin, item.getDescription(),
                        player.getName()));
                skinsComponent.syncSkinsToClient();
                // skinsComponent.syncSkinsToNetwork();
                successes++;
            }
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("Take %s skins from %s players!", skinArray.size(), players.size()), true);
        return successes;
    }

    private static int setEquippedSkin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Player> players = EntityArgument.getPlayers(context, "targets");
        var item = ItemArgument.getItem(context, "item");
        var skin = SkinArgumentType.getString(context, "skin");
        if (skin == "*") {
            context.getSource().sendFailure(Component.translatable("Not support *."));
            return 0;
        }
        int successes = 0;
        for (Player player : players) {
            // 设置指定物品类型的装备皮肤
            SkinManager.setEquippedSkinForItemType(player, BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                    skin);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "Set equipped skin to %s for item type %s to %s", skin, item.getItem().getDescription(),
                    player.getName()), true);
            successes++;
        }

        return successes;
    }

    private static CompletableFuture<Suggestions> suggestSkins(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        var item = ItemArgument.getItem(context, "item").getItem();
        String itemId;
        if (item instanceof SkinableItem it) {
            itemId = it.getItemSkinType();
        } else {
            itemId = SkinManager.getResourceLocationOfItem(item).getPath();
        }
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        Set<String> suggestions = new HashSet<>();
        // 添加自定义 ID 到 Set
        if (itemId == null) {
            return builder.buildFuture();
        }
        if ("*".contains(remaining)) {
            builder.suggest("*",
                    Component.translatable("All Skins"));
        }
        var skins = SkinManager.getSkins(itemId);
        skins.keySet().stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(suggestions::add);
        // 最后批量建议
        suggestions.forEach((s) -> {
            builder.suggest(s,
                    Component.translatableWithFallback("screen.sre.skins." + itemId + "." + s + ".name", s));
        });

        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestSkinableItems(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        Set<String> suggestions = new HashSet<>();
        // 添加自定义 ID 到 Set

        TMMItems.SkinnableItem.stream()
                .filter(it -> it instanceof SkinableItem)
                .map(it -> ((SkinableItem) it).getItemSkinType())
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(suggestions::add);
        // 最后批量建议
        suggestions.forEach((s) -> {
            var t = ResourceLocation.tryParse(s);
            if (t != null) {
                builder.suggest(s, Component.translatableWithFallback("screen.sre.skins." + s, s));
            }
        });

        return builder.buildFuture();
    }

    private static int listSkins(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Player> players = EntityArgument.getPlayers(context, "targets");

        for (Player player : players) {
            SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
            StringBuilder result = new StringBuilder("Skins for " + player.getName().getString() + ":\n");

            // 显示已装备的皮肤
            result.append("Equipped skins:\n");
            skinsComponent.getEquippedSkins()
                    .forEach((item, skin) -> result.append("  ").append(item).append(" -> ").append(skin).append("\n"));

            // 显示解锁的皮肤
            result.append("Unlocked skins:\n");
            skinsComponent.getUnlockedSkins().forEach((item, skins) -> {
                result.append("  ").append(item).append(":\n");
                skins.forEach((skin, unlocked) -> {
                    if (unlocked) {
                        result.append("    - ").append(skin).append("\n");
                    }
                });
            });

            context.getSource().sendSuccess(() -> Component.literal(result.toString()), false);
        }

        return players.size();
    }
}