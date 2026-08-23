/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.agmas.noellesroles.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleDataPlayerComponent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.api.time.TimeRewind;
import org.agmas.noellesroles.api.time.TimeRewindAreaResult;
import org.agmas.noellesroles.api.time.TimeRewindAreaSnapshot;
import org.agmas.noellesroles.api.time.TimeRewindSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Operator-only capture, playback and verification controls for time rewind. */
public final class TimeRewindCommand {
    private static final int DEFAULT_DURATION = 50;
    private static final Map<UUID, TimeRewindSnapshot> PLAYER_SNAPSHOTS =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, TimeRewindAreaSnapshot> AREA_SNAPSHOTS =
            new ConcurrentHashMap<>();

    private TimeRewindCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "sre:rewind");
        register(dispatcher, "rewind");
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root)
                .requires(source -> source.hasPermission(2))
                .executes(TimeRewindCommand::help)
                .then(Commands.literal("capture")
                        .executes(context -> capture(context, java.util.List.of(
                                context.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> capture(context,
                                        EntityArgument.getPlayers(context, "targets")))))
                .then(Commands.literal("restore")
                        .executes(context -> restore(context, java.util.List.of(
                                context.getSource().getPlayerOrException()), DEFAULT_DURATION))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> restore(context,
                                        EntityArgument.getPlayers(context, "targets"), DEFAULT_DURATION))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 600))
                                        .executes(context -> restore(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                IntegerArgumentType.getInteger(context, "ticks"))))))
                .then(Commands.literal("cancel")
                        .executes(context -> cancel(context, java.util.List.of(
                                context.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> cancel(context,
                                        EntityArgument.getPlayers(context, "targets")))))
                .then(Commands.literal("visual")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> visual(context,
                                        EntityArgument.getPlayers(context, "targets"), DEFAULT_DURATION))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                                        .executes(context -> visual(context,
                                                EntityArgument.getPlayers(context, "targets"),
                                                IntegerArgumentType.getInteger(context, "ticks"))))))
                .then(Commands.literal("area")
                        .then(Commands.literal("capture").executes(TimeRewindCommand::captureArea))
                        .then(Commands.literal("restore").executes(TimeRewindCommand::restoreArea)))
                .then(Commands.literal("roledata")
                        .executes(context -> roleData(context, java.util.List.of(
                                context.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> roleData(context,
                                        EntityArgument.getPlayers(context, "targets")))))
                .then(Commands.literal("status").executes(TimeRewindCommand::status))
                .then(Commands.literal("clear").executes(TimeRewindCommand::clear)));
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "回溯指令: capture | restore | cancel | visual | area capture/restore | roledata | status | clear")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int capture(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> targets) {
        int captured = 0;
        int warnings = 0;
        for (ServerPlayer player : targets) {
            TimeRewindSnapshot snapshot = TimeRewind.capture(player);
            PLAYER_SNAPSHOTS.put(player.getUUID(), snapshot);
            warnings += snapshot.warnings().size();
            captured++;
        }
        int finalCaptured = captured;
        int finalWarnings = warnings;
        context.getSource().sendSuccess(() -> Component.literal("已捕获 " + finalCaptured
                + " 个玩家回溯节点（警告 " + finalWarnings + "，局外组件保持当前值）")
                .withStyle(ChatFormatting.AQUA), true);
        return captured;
    }

    private static int restore(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> targets, int ticks) {
        int started = 0;
        for (ServerPlayer player : targets) {
            TimeRewindSnapshot snapshot = PLAYER_SNAPSHOTS.get(player.getUUID());
            if (snapshot == null) {
                context.getSource().sendFailure(Component.literal("没有 "
                        + player.getScoreboardName() + " 的测试节点"));
                continue;
            }
            if (TimeRewind.restoreSmooth(player, snapshot, ticks, result -> {
                ChatFormatting color = result.isSuccess() ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
                context.getSource().sendSuccess(() -> Component.literal("回溯完成: "
                        + player.getScoreboardName() + "，组件 " + result.restoredComponents()
                        + "，问题 " + result.failures().size()).withStyle(color), true);
            })) {
                started++;
            } else {
                context.getSource().sendFailure(Component.literal(player.getScoreboardName()
                        + " 正在回溯或节点不匹配"));
            }
        }
        int finalStarted = started;
        context.getSource().sendSuccess(() -> Component.literal("已启动 " + finalStarted
                + " 个平滑回溯，时长 " + ticks + " tick").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return started;
    }

    private static int cancel(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> targets) {
        int cancelled = 0;
        for (ServerPlayer player : targets) {
            if (TimeRewind.cancelSmoothRestore(player)) {
                cancelled++;
            }
        }
        int finalCancelled = cancelled;
        context.getSource().sendSuccess(() -> Component.literal("已取消 " + finalCancelled
                + " 个回溯动画（不会应用节点）").withStyle(ChatFormatting.YELLOW), true);
        return cancelled;
    }

    private static int visual(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> targets, int ticks) {
        targets.forEach(player -> TimeRewind.playVisual(player, ticks));
        context.getSource().sendSuccess(() -> Component.literal("已向 " + targets.size()
                + " 名玩家预览回溯 shader，时长 " + ticks + " tick")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return targets.size();
    }

    private static int captureArea(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        AABB area = AreasWorldComponent.KEY.get(level).getPlayArea();
        if (area == null) {
            context.getSource().sendFailure(Component.literal("当前世界没有配置游戏区域"));
            return 0;
        }
        TimeRewindAreaSnapshot snapshot = TimeRewind.captureArea(level, area);
        AREA_SNAPSHOTS.put(level.dimension(), snapshot);
        context.getSource().sendSuccess(() -> Component.literal("已捕获区域节点：掉落物 "
                + snapshot.itemCount() + "，SmallDoor " + snapshot.doorCount()
                + "，警告 " + snapshot.warnings().size()).withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int restoreArea(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        TimeRewindAreaSnapshot snapshot = AREA_SNAPSHOTS.get(level.dimension());
        if (snapshot == null) {
            context.getSource().sendFailure(Component.literal("当前世界没有区域测试节点"));
            return 0;
        }
        TimeRewindAreaResult result = TimeRewind.restoreArea(level, snapshot);
        ChatFormatting color = result.isSuccess() ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        context.getSource().sendSuccess(() -> Component.literal("区域回溯完成：恢复掉落物 "
                + result.restoredItems() + "，移除新增掉落物 " + result.removedCurrentItems()
                + "，恢复门 " + result.restoredDoors() + "，问题 " + result.failures().size())
                .withStyle(color), true);
        return result.isSuccess() ? 1 : 0;
    }

    private static int roleData(CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> targets) {
        int found = 0;
        for (ServerPlayer player : targets) {
            TimeRewindSnapshot snapshot = PLAYER_SNAPSHOTS.get(player.getUUID());
            boolean included = snapshot != null
                    && snapshot.containsComponent(SRERoleDataPlayerComponent.KEY.getId());
            var current = SRERoleDataPlayerComponent.KEY.get(player).roleData;
            String roleDataClass = current == null ? "<none>" : current.getClass().getSimpleName();
            ChatFormatting color = included ? ChatFormatting.GREEN : ChatFormatting.RED;
            context.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName()
                    + ": RoleData=" + roleDataClass + ", 节点专用适配=" + included)
                    .withStyle(color), false);
            if (included) {
                found++;
            }
        }
        return found;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("回溯状态：玩家节点 "
                + PLAYER_SNAPSHOTS.size() + "，区域节点 " + AREA_SNAPSHOTS.size()
                + "，播放中 " + TimeRewind.activeSmoothRewinds())
                .withStyle(ChatFormatting.AQUA), false);
        return PLAYER_SNAPSHOTS.size();
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        int count = PLAYER_SNAPSHOTS.size() + AREA_SNAPSHOTS.size();
        PLAYER_SNAPSHOTS.clear();
        AREA_SNAPSHOTS.clear();
        context.getSource().sendSuccess(() -> Component.literal("已清空 " + count
                + " 个测试节点；进行中的动画不受影响").withStyle(ChatFormatting.YELLOW), true);
        return count;
    }
}
