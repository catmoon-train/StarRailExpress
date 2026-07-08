package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * 小游戏任务系统的运行时开关指令 /sre:minigamequest on|off|query。
 * 与地图配置项 {@code minigameQuestEnabled} 共享同一真源（{@link AreasWorldComponent}），
 * 因此运行时切换与地图持久化两种方式都生效。
 */
public final class MinigameTaskCommand {
    private MinigameTaskCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:minigamequest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx, false)))
                .then(Commands.literal("query").executes(MinigameTaskCommand::query)));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean value) {
        ServerLevel level = ctx.getSource().getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        areas.areasSettings.minigameQuestEnabled = value;
        areas.sync();
        ctx.getSource().sendSuccess(
                () -> Component.translatable(value
                        ? "commands.sre.minigamequest.on"
                        : "commands.sre.minigamequest.off")
                        .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                true);
        return 1;
    }

    private static int query(CommandContext<CommandSourceStack> ctx) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
        boolean value = areas.areasSettings.minigameQuestEnabled;
        ctx.getSource().sendSuccess(
                () -> Component.translatable("commands.sre.minigamequest.query", value)
                        .withStyle(ChatFormatting.AQUA),
                false);
        return 1;
    }
}
