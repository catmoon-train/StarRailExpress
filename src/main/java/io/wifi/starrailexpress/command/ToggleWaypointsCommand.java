package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.data.WaypointManager;
import io.wifi.starrailexpress.data.WaypointVisibilityManager;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.network.packet.SyncSpecificWaypointVisibilityPacket;
import io.wifi.starrailexpress.network.packet.SyncWaypointVisibilityPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class ToggleWaypointsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:togglewaypoints")
            .executes(context -> toggleWaypoints(context, null, null, null, null)) // 默认为切换操作，对所有玩家
            .then(Commands.argument("target", EntityArgument.players())
                .executes(context -> toggleWaypoints(context, EntityArgument.getPlayers(context, "target"), null, null, null))
                .then(Commands.argument("visible", BoolArgumentType.bool())
                    .executes(context -> toggleWaypoints(context, EntityArgument.getPlayers(context, "target"), 
                                                       BoolArgumentType.getBool(context, "visible"), null, null))
                    .then(Commands.argument("path", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                            return SharedSuggestionProvider.suggest(manager.getAllPaths().stream().map(String::valueOf), builder);
                        })
                        .then(Commands.argument("name", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                String path = StringArgumentType.getString(context, "path");
                                WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                                return SharedSuggestionProvider.suggest(manager.getWaypointsByPath(path).stream().map(wp -> wp.getName()), builder);
                            })
                            .executes(context -> toggleWaypoints(context, EntityArgument.getPlayers(context, "target"), 
                                                               BoolArgumentType.getBool(context, "visible"), 
                                                               StringArgumentType.getString(context, "path"),
                                                               StringArgumentType.getString(context, "name")))
                        )
                    )
                )
            )
            .then(Commands.argument("visible", BoolArgumentType.bool())
                .executes(context -> toggleWaypoints(context, null, BoolArgumentType.getBool(context, "visible"), null, null))
                .then(Commands.argument("target", EntityArgument.players())
                    .executes(context -> toggleWaypoints(context, EntityArgument.getPlayers(context, "target"), 
                                                       BoolArgumentType.getBool(context, "visible"), null, null))
                    .then(Commands.argument("path", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                            return SharedSuggestionProvider.suggest(manager.getAllPaths().stream().map(String::valueOf), builder);
                        })
                        .then(Commands.argument("name", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                String path = StringArgumentType.getString(context, "path");
                                WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                                return SharedSuggestionProvider.suggest(manager.getWaypointsByPath(path).stream().map(wp -> wp.getName()), builder);
                            })
                            .executes(context -> toggleWaypoints(context, EntityArgument.getPlayers(context, "target"), 
                                                               BoolArgumentType.getBool(context, "visible"), 
                                                               StringArgumentType.getString(context, "path"),
                                                               StringArgumentType.getString(context, "name")))
                        )
                    )
                )
                .then(Commands.argument("path", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                        return SharedSuggestionProvider.suggest(manager.getAllPaths().stream().map(String::valueOf), builder);
                    })
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            String path = StringArgumentType.getString(context, "path");
                            WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                            return SharedSuggestionProvider.suggest(manager.getWaypointsByPath(path).stream().map(wp -> wp.getName()), builder);
                        })
                        .executes(context -> toggleWaypoints(context, null, 
                                                           BoolArgumentType.getBool(context, "visible"), 
                                                           StringArgumentType.getString(context, "path"),
                                                           StringArgumentType.getString(context, "name")))
                    )
                )
            )
            .then(Commands.argument("path", StringArgumentType.string())
                .suggests((context, builder) -> {
                    WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                    return SharedSuggestionProvider.suggest(manager.getAllPaths().stream().map(String::valueOf), builder);
                })
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        String path = StringArgumentType.getString(context, "path");
                        WaypointManager manager = WaypointManager.get(context.getSource().getServer());
                        return SharedSuggestionProvider.suggest(manager.getWaypointsByPath(path).stream().map(wp -> wp.getName()), builder);
                    })
                    .executes(context -> toggleWaypoints(context, null, null, 
                                                       StringArgumentType.getString(context, "path"),
                                                       StringArgumentType.getString(context, "name")))
                )
            )
        );
    }

    private static int toggleWaypoints(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets, Boolean visibilityState, String path, String name) throws CommandSyntaxException {
        // CommandSourceStack source = context.getSource();
        
        if (path != null && name != null) {
            // 操作特定路径点
            return toggleSpecificWaypoint(context, targets, visibilityState, path, name);
        } else {
            // 操作所有路径点
            return toggleAllWaypoints(context, targets, visibilityState);
        }
    }

    private static int toggleSpecificWaypoint(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets, Boolean visibilityState, String path, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        // 确定要发送的目标玩家
        Collection<ServerPlayer> playersToSendTo;
        if (targets != null && !targets.isEmpty()) {
            playersToSendTo = targets;
        } else {
            // 如果没有指定目标，则发送给所有玩家
            playersToSendTo = source.getServer().getPlayerList().getPlayers();
        }
        
        boolean newVisibilityState;
        if (visibilityState != null) {
            // 如果提供了明确的可见性状态，则使用该状态
            newVisibilityState = visibilityState;
        } else {
            // 否则默认显示特定路径点
            newVisibilityState = true;
        }
        
        // 发送特定路径点的网络包到指定玩家
        for (ServerPlayer player : playersToSendTo) {
            PacketTracker.sendToClient(player, new SyncSpecificWaypointVisibilityPacket(newVisibilityState, path, name));
        }
        
        // 发送反馈消息
        String targetDesc = targets != null ? "指定玩家" : "所有玩家";
        String visibilityDesc = newVisibilityState ? "显示" : "隐藏";
        
        source.sendSuccess(() -> Component.literal("路径点 '" + path + "/" + name + "' 已" + visibilityDesc + " - 已同步到" + targetDesc), false);
        
        // 如果不是对所有玩家执行，也要通知执行者
        if (targets != null) {
            for (ServerPlayer player : targets) {
                if (player != source.getEntity()) {
                    player.sendSystemMessage(Component.literal("服务器已将路径点 '" + path + "/" + name + "' 设置为" + visibilityDesc));
                }
            }
        }
        
        return 1;
    }

    private static int toggleAllWaypoints(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets, Boolean visibilityState) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        
        // 获取可见性管理器
        WaypointVisibilityManager visibilityManager = WaypointVisibilityManager.get(source.getServer());
        
        boolean newVisibilityState;
        if (visibilityState != null) {
            // 如果提供了明确的可见性状态，则使用该状态
            newVisibilityState = visibilityState;
            visibilityManager.setWaypointsVisibility(newVisibilityState);
        } else {
            // 否则切换当前状态
            visibilityManager.toggleWaypointsVisibility();
            newVisibilityState = visibilityManager.getWaypointsVisibility();
        }
        
        // 确定要发送的目标玩家
        Collection<ServerPlayer> playersToSendTo;
        if (targets != null && !targets.isEmpty()) {
            playersToSendTo = targets;
        } else {
            // 如果没有指定目标，则发送给所有玩家
            playersToSendTo = source.getServer().getPlayerList().getPlayers();
        }
        
        // 发送网络包到指定玩家
        for (ServerPlayer player : playersToSendTo) {
            PacketTracker.sendToClient(player, new SyncWaypointVisibilityPacket(newVisibilityState));
        }
        
        // 发送反馈消息
        String targetDesc = targets != null ? "指定玩家" : "所有玩家";
        String visibilityDesc = newVisibilityState ? "显示" : "隐藏";
        
        source.sendSuccess(() -> Component.literal("所有路径点已" + visibilityDesc + " - 已同步到" + targetDesc), false);
        
        // 如果不是对所有玩家执行，也要通知执行者
        if (targets != null) {
            for (ServerPlayer player : targets) {
                if (player != source.getEntity()) {
                    player.sendSystemMessage(Component.literal("服务器已将您的所有路径点设置为" + visibilityDesc));
                }
            }
        }
        
        return 1;
    }
}