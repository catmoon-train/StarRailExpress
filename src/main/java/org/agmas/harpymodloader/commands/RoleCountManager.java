package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

public class RoleCountManager {

    // 用于存储手动设置的杀手和侦探数量
    public static int forcedKillerCount = -1; // -1表示使用自动计算
    public static int forcedVigilanteCount = -1; // -1表示使用自动计算
    public static int forcedNeutralCount = -1; // -1表示使用自动计算

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setRoleCount")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.literal("killer")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(RoleCountManager::setKillerCount)))
                .then(Commands.literal("vigilante")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(RoleCountManager::setVigilanteCount)))
                .then(Commands.literal("neutral")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(RoleCountManager::setNeutralCount)))
                .then(Commands.literal("reset")
                        .executes(RoleCountManager::resetCounts))
                .executes(RoleCountManager::resetCounts));
    }

    private static int setNeutralCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Harpymodloader.officialVerify) {
            return 1;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        forcedNeutralCount = count;
        if (count == -1) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.auto",
                    Component.translatable("display.type.role.neutral_all")), false);
        } else {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setrolecount.set",
                            Component.translatable("display.type.role.neutral_all"), count),
                    false);
        }
        return 1;
    }

    private static int setKillerCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(context, "count");
        forcedKillerCount = count;
        if (count == -1) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.auto",
                    Component.translatable("display.type.role.killer")), false);
        } else {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setrolecount.set",
                            Component.translatable("display.type.role.killer"), count),
                    false);
        }
        return 1;
    }

    private static int setVigilanteCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(context, "count");
        forcedVigilanteCount = count;
        if (count == -1) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.auto",
                    Component.translatable("display.type.role.vigilante")), false);
        } else {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.setrolecount.set",
                            Component.translatable("display.type.role.vigilante"), count),
                    false);
        }
        return 1;
    }

    private static int resetCounts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        forcedKillerCount = -1;
        forcedVigilanteCount = -1;
        forcedNeutralCount = -1;
        context.getSource().sendSuccess(() -> Component.translatable(""), false);
        return 1;
    }

    // 获取实际使用的杀手数量（考虑强制设置或自动计算）
    public static int getKillerCount(int playerCount) {
        if (forcedKillerCount >= 0) {
            return Math.min(forcedKillerCount, playerCount); // 确保不超过玩家总数
        } else {
            return playerCount / 6;
        }
    }

    // 获取实际使用的侦探数量（考虑强制设置或自动计算）
    public static int getVigilanteCount(int playerCount) {
        if (forcedVigilanteCount >= 0) {
            return Math.min(forcedVigilanteCount, playerCount); // 确保不超过玩家总数
        } else {
            return playerCount / 6;

        }
    }

    public static int getNeutralCount(int playerCount) {
        HarpyModLoaderConfig config = HarpyModLoaderConfig.HANDLER.instance();
        int minPlayer = config.neutralMinPlayerCount;

        if (forcedNeutralCount >= 0) {
            return Math.min(forcedNeutralCount, playerCount);
        } else {
            if (playerCount <= minPlayer)
                return 0;
            return Math.max(1, playerCount / 6);
        }
    }
}