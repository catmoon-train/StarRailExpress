package net.exmo.sre.nametag;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.SRE;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class NameTagCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /nametag add <nameTag> [target] - 添加名片
        dispatcher.register(Commands.literal("nametag:add").requires(source -> source.hasPermission(2))
                .then(Commands.argument("nameTag", StringArgumentType.string())
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(
                                        context -> addNametag(context, EntityArgument.getPlayer(context, "target"))))));

        // /nametag remove <nameTag> [target] - 移除名片
        dispatcher.register(Commands.literal("nametag:remove").requires(source -> source.hasPermission(2))
                .then(Commands.argument("nameTag", StringArgumentType.string())
                        .executes(context -> removeNametag(context, EntityArgument.getPlayer(context, "target")))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> removeNametag(context,
                                        EntityArgument.getPlayer(context, "target"))))));

        // /nametag set <nameTag> [target] - 设置当前名片
        dispatcher.register(Commands.literal("nametag:set").requires(source -> source.hasPermission(2))
                .then(Commands.argument("nameTag", StringArgumentType.string())
                        .executes(context -> setNametag(context, EntityArgument.getPlayer(context, "target")))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(
                                        context -> setNametag(context, EntityArgument.getPlayer(context, "target"))))));

        // /nametag get [target] - 获取当前名片
        dispatcher.register(Commands.literal("nametag:get").requires(source -> source.hasPermission(2))
                .executes(context -> getNametag(context, EntityArgument.getPlayer(context, "target")))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> getNametag(context, EntityArgument.getPlayer(context, "target")))));

        // /nametag list [target] - 列出所有名片
        dispatcher.register(Commands.literal("nametag:list").requires(source -> source.hasPermission(2))
                .executes(context -> listNametags(context, context.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> listNametags(context, EntityArgument.getPlayer(context, "target")))));

        // /nametag clear [target] - 清空所有名片
        dispatcher.register(Commands.literal("nametag:clear").requires(source -> source.hasPermission(2))
                .executes(context -> clearNametags(context, EntityArgument.getPlayer(context, "target")))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> clearNametags(context, EntityArgument.getPlayer(context, "target")))));

        // /nametag sync <target> - 从服务器同步名片数据
        dispatcher.register(Commands.literal("nametag:sync").requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> syncFromServer(context, EntityArgument.getPlayer(context, "target")))));
    }

    /**
     * 添加名片
     */
    private static int addNametag(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        try {

            String nameTag = StringArgumentType.getString(context, "nameTag");
            NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

            component.addNameTag(nameTag);

            context.getSource().sendSuccess(() -> Component.literal("已为玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 添加名片: "))
                    .append(Component.literal(nameTag)), true);

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("添加名片失败"));
            SRE.LOGGER.error("添加名片失败", e);
        }
        return 1;
    }

    /**
     * 移除名片
     */
    private static int removeNametag(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        String nameTag = StringArgumentType.getString(context, "nameTag");
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

        if (component.nameTags.contains(nameTag)) {
            component.removeNameTag(nameTag);
            context.getSource().sendSuccess(() -> Component.literal("已从玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 移除名片: "))
                    .append(Component.literal(nameTag)), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 没有该名片: "))
                    .append(Component.literal(nameTag)));
            return 0;
        }
    }

    /**
     * 设置当前名片
     */
    private static int setNametag(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        String nameTag = StringArgumentType.getString(context, "nameTag");
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

        if (component.nameTags.contains(nameTag)) {
            component.setCurrentNameTag(nameTag);
            context.getSource().sendSuccess(() -> Component.literal("已将玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 的当前名片设置为: "))
                    .append(Component.literal(nameTag)), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 没有该名片，无法设置: "))
                    .append(Component.literal(nameTag)));
            return 0;
        }
    }

    /**
     * 获取当前名片
     */
    private static int getNametag(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);
        String currentNametag = component.getCurrentNameTag();

        if (currentNametag.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 当前未装备名片")), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 的当前名片为: "))
                    .append(Component.literal(currentNametag)), false);
        }

        return 1;
    }

    /**
     * 列出所有名片
     */
    private static int listNametags(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

        if (component.nameTags.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 没有任何名片")), false);
            return 1;
        }

        var message = Component.literal("玩家 ")
                .append(target.getDisplayName())
                .append(Component.literal(" 的名片列表 ("))
                .append(Component.literal(String.valueOf(component.nameTags.size())))
                .append(Component.literal("):"));

        for (String nameTag : component.nameTags) {
            String marker = nameTag.equals(component.CurrentNameTag) ? " §e[当前]§r" : "";
            message.append(Component.literal("\n - ").append(Component.literal(nameTag + marker)));
        }

        context.getSource().sendSuccess(() -> message, false);

        return 1;
    }

    /**
     * 清空所有名片
     */
    private static int clearNametags(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);
        int count = component.nameTags.size();

        if (count > 0) {
            component.clear();
            context.getSource().sendSuccess(() -> Component.literal("已清空玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 的所有名片 ("))
                    .append(Component.literal(String.valueOf(count)))
                    .append(Component.literal(" 个)")), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 本来就没有名片")), false);
        }

        return 1;
    }

    /**
     * 从服务器同步名片数据
     */
    private static int syncFromServer(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        NameTagInventoryComponent component = NameTagInventoryComponent.KEY.get(target);

        if (component.isNetworkSyncEnabled()) {
            component.syncFromLinkedServer();
            context.getSource().sendSuccess(() -> Component.literal("正在从服务器同步玩家 ")
                    .append(target.getDisplayName())
                    .append(Component.literal(" 的名片数据...")), true);
        } else {
            context.getSource().sendFailure(Component.literal("网络同步未启用，无法同步数据"));
            return 0;
        }

        return 1;
    }
}
