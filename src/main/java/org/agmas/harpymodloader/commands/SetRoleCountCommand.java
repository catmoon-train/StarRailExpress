package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.Harpymodloader;

public class SetRoleCountCommand {

    // 用于存储手动设置的杀手和侦探数量
    public static int forcedKillerCount = 0; // 0表示使用自动计算
    public static int forcedVigilanteCount = 0; // 0表示使用自动计算
    public static int forcedNatureCount = 0; // 0表示使用自动计算

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setRoleCount")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.literal("killer")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(SetRoleCountCommand::setKillerCount)))
                .then(Commands.literal("detective")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(SetRoleCountCommand::setVigilanteCount)))
                .then(Commands.literal("nature")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(SetRoleCountCommand::setNatureCount)))
                .then(Commands.literal("reset")
                        .executes(SetRoleCountCommand::resetCounts))
                .executes(SetRoleCountCommand::resetCounts));
    }

    private static int setNatureCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!Harpymodloader.isMojangVerify) {
            return 1;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        forcedNatureCount = count;
        if (count == 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.nature.auto"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.nature.set", count),
                    false);
        }
        return 1;
    }

    private static int setKillerCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(context, "count");
        forcedKillerCount = count;
        if (count == 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.killer.auto"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.killer.set", count), false);
        }
        return 1;
    }

    private static int setVigilanteCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(context, "count");
        forcedVigilanteCount = count;
        if (count == 0) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.detective.auto"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("commands.setrolecount.detective.set", count),
                    false);
        }
        return 1;
    }

    private static int resetCounts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        forcedKillerCount = 0;
        forcedVigilanteCount = 0;
        forcedNatureCount = 0;
        return 1;
    }

    // 获取实际使用的杀手数量（考虑强制设置或自动计算）
    public static int getKillerCount(int playerCount) {
        if (forcedKillerCount > 0) {
            return Math.min(forcedKillerCount, playerCount); // 确保不超过玩家总数
        } else {
            return playerCount / 6;
        }
    }

    // 获取实际使用的侦探数量（考虑强制设置或自动计算）
    public static int getVigilanteCount(int playerCount) {
        if (forcedVigilanteCount > 0) {
            return Math.min(forcedVigilanteCount, playerCount); // 确保不超过玩家总数
        } else {
            return playerCount / 6;

        }
    }

    public static int getNatureCount(int playerCount) {
        if (playerCount <= 10)
            return 0;
        if (forcedNatureCount > 0) {
            return Math.min(forcedNatureCount, playerCount); // 确保不超过玩家总数
        } else {
            return Math.max(1, playerCount / 6);
        }
    }
}