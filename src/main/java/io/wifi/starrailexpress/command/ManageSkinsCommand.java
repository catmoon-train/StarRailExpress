package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.cca.PlayerSkinsComponent;
import io.wifi.starrailexpress.command.argument.SkinArgumentType;
import io.wifi.starrailexpress.util.SkinManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import static io.wifi.starrailexpress.SRE.LOGGER;

import java.util.Collection;

public class ManageSkinsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {

        dispatcher.register(Commands.literal("tmm:manageskins")
                .requires(source -> source.hasPermission(2)) // 需要权限等级2
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("skin", SkinArgumentType.string())
                                                .executes(ManageSkinsCommand::giveSkin)))))
                .then(Commands.literal("take")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("skin", SkinArgumentType.string())
                                                .executes(ManageSkinsCommand::takeSkin)))))
                .then(Commands.literal("setequipped")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("item", ItemArgument.item(context))
                                        .then(Commands.argument("skin", SkinArgumentType.string())
                                                .executes(ManageSkinsCommand::setEquippedSkin)))))
                .then(Commands.literal("list")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ManageSkinsCommand::listSkins))));
    }

    private static int giveSkin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {

            Collection<? extends Player> players = EntityArgument.getPlayers(context, "targets");
            var item = ItemArgument.getItem(context, "item");
            String skin = SkinArgumentType.getString(context, "skin");

            int successes = 0;
            for (Player player : players) {
                // 解锁指定物品类型的皮肤
                SkinManager.unlockSkinForItemType(player, BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                        skin);
                context.getSource().sendSuccess(() -> Component.literal(
                        "Gave skin '" + skin + "' for item type '" + item + "' to " + player.getName().getString()),
                        true);

                successes++;
            }

            return successes;
        } catch (Exception ig) {
            LOGGER.error("Error occurred while executing manageskins command", ig);
            return 0;
        }
    }

    private static int takeSkin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Player> players = EntityArgument.getPlayers(context, "targets");
        var item = ItemArgument.getItem(context, "item");
        String skin = SkinArgumentType.getString(context, "skin");

        int successes = 0;
        for (Player player : players) {
            // 锁定（移除）指定物品类型的皮肤
            PlayerSkinsComponent skinsComponent = PlayerSkinsComponent.KEY.get(player);
            skinsComponent.lockSkinForItemType(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(), skin);
            context.getSource().sendSuccess(() -> Component.literal(
                    "Removed skin '" + skin + "' for item type '" + item + "' from " + player.getName().getString()),
                    true);
            skinsComponent.syncSkinsToClient();
            // skinsComponent.syncSkinsToNetwork();
            successes++;
        }

        return successes;
    }

    private static int setEquippedSkin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Player> players = EntityArgument.getPlayers(context, "targets");
        var item = ItemArgument.getItem(context, "item");
        var skin = SkinArgumentType.getString(context, "skin");

        int successes = 0;
        for (Player player : players) {
            // 设置指定物品类型的装备皮肤
            SkinManager.setEquippedSkinForItemType(player, BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                    skin);
            context.getSource().sendSuccess(() -> Component.literal("Set equipped skin to '" + skin
                    + "' for item type '" + item + "' for " + player.getName().getString()), true);
            successes++;
        }

        return successes;
    }

    private static int listSkins(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Player> players = EntityArgument.getPlayers(context, "targets");

        for (Player player : players) {
            PlayerSkinsComponent skinsComponent = PlayerSkinsComponent.KEY.get(player);
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