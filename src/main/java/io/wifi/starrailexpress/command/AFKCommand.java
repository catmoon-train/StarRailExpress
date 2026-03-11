package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.PlayerAFKComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AFKCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:afk").requires(source -> source.hasPermission(2))
            .then(Commands.literal("reset")
                .executes(AFKCommand::resetAFK))
            .then(Commands.literal("status")
                .executes(AFKCommand::checkAFKStatus))
            .then(Commands.literal("setTime")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                    .executes(AFKCommand::setAFKTime))
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                        .executes(AFKCommand::setAFKTimeForPlayers)))));
    }

    private static int resetAFK(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        PlayerAFKComponent afkComponent = PlayerAFKComponent.KEY.maybeGet(player).orElse(null);
        if (afkComponent != null) {
            afkComponent.resetAFKTimer();
            context.getSource().sendSuccess(() -> Component.literal("AFK计时器已重置"), false);
        } else {
            context.getSource().sendFailure(Component.literal("无法获取AFK组件"));
        }

        return 1;
    }

    private static int checkAFKStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        PlayerAFKComponent afkComponent = PlayerAFKComponent.KEY.maybeGet(player).orElse(null);
        if (afkComponent != null) {
            float afkProgress = afkComponent.getAFKProgress();
            int afkTime = afkComponent.getAFKTime();
            boolean isAFK = afkComponent.isAFK();
            boolean isSleepy = afkComponent.isSleepy();
            boolean isWarning = afkComponent.isWarning();

            context.getSource().sendSuccess(() -> Component.literal(
                String.format("AFK状态:\n" +
                    "进度: %.2f%%\n" +
                    "时间: %d ticks (%.1f 秒)\n" +
                    "是否AFK: %s\n" +
                    "困倦状态: %s\n" +
                    "警告状态: %s",
                    afkProgress * 100,
                    afkTime,
                    afkTime / 20.0, // 转换为秒
                    isAFK ? "是" : "否",
                    isSleepy ? "是" : "否",
                    isWarning ? "是" : "否")), false);
        } else {
            context.getSource().sendFailure(Component.literal("无法获取AFK组件"));
        }

        return 1;
    }

    private static int setAFKTime(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        int ticks = seconds * 20; // 转换为游戏刻度

        PlayerAFKComponent afkComponent = PlayerAFKComponent.KEY.maybeGet(player).orElse(null);
        if (afkComponent != null) {
            afkComponent.setAFKTime(ticks);
            context.getSource().sendSuccess(() -> Component.literal("AFK时间已设置为 " + seconds + " 秒 (" + ticks + " ticks)"), false);
        } else {
            context.getSource().sendFailure(Component.literal("无法获取AFK组件"));
        }

        return 1;
    }

    private static int setAFKTimeForPlayers(CommandContext<CommandSourceStack> context) {
        try {
            var targets = EntityArgument.getPlayers(context, "targets");
            int seconds = IntegerArgumentType.getInteger(context, "seconds");
            int ticks = seconds * 20; // 转换为游戏刻度

            for (ServerPlayer target : targets) {
                PlayerAFKComponent afkComponent = PlayerAFKComponent.KEY.maybeGet(target).orElse(null);
                if (afkComponent != null) {
                    afkComponent.setAFKTime(ticks);
                }
            }

            if (targets.size() == 1) {
                context.getSource().sendSuccess(() -> Component.literal("已为 " + targets.iterator().next().getName().getString() + 
                    " 设置AFK时间为 " + seconds + " 秒 (" + ticks + " ticks)"), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("已为 " + targets.size() + 
                    " 名玩家设置AFK时间为 " + seconds + " 秒 (" + ticks + " ticks)"), false);
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("设置AFK时间时出错: " + e.getMessage()));
        }

        return 1;
    }
}