package io.wifi.starrailexpress.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.network.NetworkStatistics;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class NetworkStatsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:netstats")
            .requires(source -> source.hasPermission(2))
            .executes(context -> showGlobalStats(context))
            .then(Commands.literal("global")
                .executes(context -> showGlobalStats(context)))
            .then(Commands.literal("player")
                .executes(context -> showPlayerStats(context, context.getSource().getPlayer()))
                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                    .executes(context -> showPlayerStats(context, net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "target")))))
            // 显示按玩家的统计
            .then(Commands.literal("byplayer")
                .executes(context -> showStatsByPlayer(context)))
            // 显示包类型排行
            .then(Commands.literal("rankings")
                .executes(context -> showRankings(context, 10)) // 默认显示前10个
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                    .executes(context -> showRankings(context, IntegerArgumentType.getInteger(context, "limit")))))
            // 显示服务器端包排行
            .then(Commands.literal("server_rankings")
                .executes(context -> showServerRankings(context, 10))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                    .executes(context -> showServerRankings(context, IntegerArgumentType.getInteger(context, "limit")))))
            // 显示客户端包排行
            .then(Commands.literal("client_rankings")
                .executes(context -> showClientRankings(context, 10))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                    .executes(context -> showClientRankings(context, IntegerArgumentType.getInteger(context, "limit")))))
                .then(Commands.literal("export")
                    .executes(context -> exportNetworkStatsToJson(context, 20)) // 默认导出前10个
                    .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                        .executes(context -> exportNetworkStatsToJson(context, IntegerArgumentType.getInteger(context, "limit")))))
        );
    }

    // 新增：导出网络统计信息到JSON文件
    private static int exportNetworkStatsToJson(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();
        
        try {
            NetworkStatistics stats = NetworkStatistics.getInstance();
            
            // 创建JSON对象
            JsonObject rootObject = new JsonObject();
            
            // 添加时间戳
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            rootObject.addProperty("timestamp", timestamp);
            rootObject.addProperty("limit", limit);
            
            // 添加全局统计信息
            JsonObject globalStats = new JsonObject();
            globalStats.addProperty("total_packets_sent", stats.getTotalPacketsSent());
            globalStats.addProperty("total_packets_received", stats.getTotalPacketsReceived());
            globalStats.addProperty("total_bytes_sent", stats.getTotalBytesSent());
            globalStats.addProperty("total_bytes_received", stats.getTotalBytesReceived());
            globalStats.addProperty("average_packet_size", stats.getAveragePacketSize());
            rootObject.add("global_stats", globalStats);
            
            // 添加服务器端包排行
            JsonArray serverRankings = new JsonArray();
            List<String> serverCountRankings = stats.getTopPacketsByCount(limit, true);
            for (int i = 0; i < Math.min(limit, serverCountRankings.size()); i++) {
                String packetId = serverCountRankings.get(i);
                var packetStats = stats.getServerPacketStats(packetId);
                JsonObject packetInfo = new JsonObject();
                packetInfo.addProperty("rank", i + 1);
                packetInfo.addProperty("packet_id", packetId);
                packetInfo.addProperty("count", packetStats.count);
                packetInfo.addProperty("total_size", packetStats.totalSize);
                packetInfo.addProperty("avg_size", packetStats.getAverageSize());
                packetInfo.addProperty("max_size", packetStats.maxSize);
                packetInfo.addProperty("min_size", packetStats.minSize == Long.MAX_VALUE ? 0 : packetStats.minSize);
                serverRankings.add(packetInfo);
            }
            rootObject.add("server_rankings_by_count", serverRankings);
            
            JsonArray serverBytesRankings = new JsonArray();
            List<String> serverBytesRankingsList = stats.getTopPacketsByBytes(limit, true);
            for (int i = 0; i < Math.min(limit, serverBytesRankingsList.size()); i++) {
                String packetId = serverBytesRankingsList.get(i);
                var packetStats = stats.getServerPacketStats(packetId);
                JsonObject packetInfo = new JsonObject();
                packetInfo.addProperty("rank", i + 1);
                packetInfo.addProperty("packet_id", packetId);
                packetInfo.addProperty("count", packetStats.count);
                packetInfo.addProperty("total_size", packetStats.totalSize);
                packetInfo.addProperty("avg_size", packetStats.getAverageSize());
                packetInfo.addProperty("max_size", packetStats.maxSize);
                packetInfo.addProperty("min_size", packetStats.minSize == Long.MAX_VALUE ? 0 : packetStats.minSize);
                serverBytesRankings.add(packetInfo);
            }
            rootObject.add("server_rankings_by_bytes", serverBytesRankings);
            
            JsonArray serverAvgSizeRankings = new JsonArray();
            List<String> serverAvgSizeRankingsList = stats.getTopPacketsByAvgSize(limit, true);
            for (int i = 0; i < Math.min(limit, serverAvgSizeRankingsList.size()); i++) {
                String packetId = serverAvgSizeRankingsList.get(i);
                var packetStats = stats.getServerPacketStats(packetId);
                JsonObject packetInfo = new JsonObject();
                packetInfo.addProperty("rank", i + 1);
                packetInfo.addProperty("packet_id", packetId);
                packetInfo.addProperty("count", packetStats.count);
                packetInfo.addProperty("total_size", packetStats.totalSize);
                packetInfo.addProperty("avg_size", packetStats.getAverageSize());
                packetInfo.addProperty("max_size", packetStats.maxSize);
                packetInfo.addProperty("min_size", packetStats.minSize == Long.MAX_VALUE ? 0 : packetStats.minSize);
                serverAvgSizeRankings.add(packetInfo);
            }
            rootObject.add("server_rankings_by_avg_size", serverAvgSizeRankings);
            
            // 添加客户端包排行
            JsonArray clientRankings = new JsonArray();
            List<String> clientCountRankings = stats.getTopPacketsByCount(limit, false);
            for (int i = 0; i < Math.min(limit, clientCountRankings.size()); i++) {
                String packetId = clientCountRankings.get(i);
                var packetStats = stats.getClientPacketStats(packetId);
                JsonObject packetInfo = new JsonObject();
                packetInfo.addProperty("rank", i + 1);
                packetInfo.addProperty("packet_id", packetId);
                packetInfo.addProperty("count", packetStats.count);
                packetInfo.addProperty("total_size", packetStats.totalSize);
                packetInfo.addProperty("avg_size", packetStats.getAverageSize());
                packetInfo.addProperty("max_size", packetStats.maxSize);
                packetInfo.addProperty("min_size", packetStats.minSize == Long.MAX_VALUE ? 0 : packetStats.minSize);
                clientRankings.add(packetInfo);
            }
            rootObject.add("client_rankings_by_count", clientRankings);
            
            JsonArray clientBytesRankings = new JsonArray();
            List<String> clientBytesRankingsList = stats.getTopPacketsByBytes(limit, false);
            for (int i = 0; i < Math.min(limit, clientBytesRankingsList.size()); i++) {
                String packetId = clientBytesRankingsList.get(i);
                var packetStats = stats.getClientPacketStats(packetId);
                JsonObject packetInfo = new JsonObject();
                packetInfo.addProperty("rank", i + 1);
                packetInfo.addProperty("packet_id", packetId);
                packetInfo.addProperty("count", packetStats.count);
                packetInfo.addProperty("total_size", packetStats.totalSize);
                packetInfo.addProperty("avg_size", packetStats.getAverageSize());
                packetInfo.addProperty("max_size", packetStats.maxSize);
                packetInfo.addProperty("min_size", packetStats.minSize == Long.MAX_VALUE ? 0 : packetStats.minSize);
                clientBytesRankings.add(packetInfo);
            }
            rootObject.add("client_rankings_by_bytes", clientBytesRankings);
            
            JsonArray clientAvgSizeRankings = new JsonArray();
            List<String> clientAvgSizeRankingsList = stats.getTopPacketsByAvgSize(limit, false);
            for (int i = 0; i < Math.min(limit, clientAvgSizeRankingsList.size()); i++) {
                String packetId = clientAvgSizeRankingsList.get(i);
                var packetStats = stats.getClientPacketStats(packetId);
                JsonObject packetInfo = new JsonObject();
                packetInfo.addProperty("rank", i + 1);
                packetInfo.addProperty("packet_id", packetId);
                packetInfo.addProperty("count", packetStats.count);
                packetInfo.addProperty("total_size", packetStats.totalSize);
                packetInfo.addProperty("avg_size", packetStats.getAverageSize());
                packetInfo.addProperty("max_size", packetStats.maxSize);
                packetInfo.addProperty("min_size", packetStats.minSize == Long.MAX_VALUE ? 0 : packetStats.minSize);
                clientAvgSizeRankings.add(packetInfo);
            }
            rootObject.add("client_rankings_by_avg_size", clientAvgSizeRankings);
            
            // 添加玩家统计信息
            JsonArray playerStatsArray = new JsonArray();
            var allPlayerStats = stats.getAllPlayerPacketStats();
            for (var entry : allPlayerStats.entrySet()) {
                String playerName = entry.getKey();
                var playerStats = entry.getValue();
                
                JsonObject playerInfo = new JsonObject();
                playerInfo.addProperty("player_name", playerName);
                playerInfo.addProperty("packets_sent", playerStats.packetsSent);
                playerInfo.addProperty("bytes_sent", playerStats.bytesSent);
                playerInfo.addProperty("packets_received", playerStats.packetsReceived);
                playerInfo.addProperty("bytes_received", playerStats.bytesReceived);
                
                JsonArray packetTypeStats = new JsonArray();
                for (var typeEntry : playerStats.packetTypeStats.entrySet()) {
                    var packetTypeStatsObj = typeEntry.getValue();
                    JsonObject typeInfo = new JsonObject();
                    typeInfo.addProperty("packet_type", typeEntry.getKey());
                    typeInfo.addProperty("count", packetTypeStatsObj.count);
                    typeInfo.addProperty("total_size", packetTypeStatsObj.totalSize);
                    typeInfo.addProperty("avg_size", packetTypeStatsObj.getAverageSize());
                    typeInfo.addProperty("max_size", packetTypeStatsObj.maxSize);
                    typeInfo.addProperty("min_size", packetTypeStatsObj.minSize == Long.MAX_VALUE ? 0 : packetTypeStatsObj.minSize);
                    packetTypeStats.add(typeInfo);
                }
                playerInfo.add("packet_type_stats", packetTypeStats);
                
                playerStatsArray.add(playerInfo);
            }
            rootObject.add("player_stats", playerStatsArray);
            
            // 将JSON写入文件
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonOutput = gson.toJson(rootObject);
            
            // 确定输出文件路径
            Path dataDir = Paths.get("data");
            if (!java.nio.file.Files.exists(dataDir)) {
                java.nio.file.Files.createDirectories(dataDir);
            }
            
            String fileName = "network_stats_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
            Path outputPath = dataDir.resolve(fileName);
            
            try (FileWriter writer = new FileWriter(outputPath.toFile())) {
                writer.write(jsonOutput);
            }
            
            source.sendSuccess(() -> Component.literal("网络统计信息已成功导出到: " + outputPath.toString()), false);
            
        } catch (IOException e) {
            source.sendFailure(Component.literal("导出网络统计信息时发生错误: " + e.getMessage()));
            return 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("导出网络统计信息时发生未知错误: " + e.getMessage()));
            return 0;
        }
        
        return 1;
    }
    
    private static int showGlobalStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        NetworkStatistics stats = NetworkStatistics.getInstance();
        
        source.sendSuccess(() -> Component.literal("=== 全局网络统计 ===").withStyle(ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("发送的包总数: " + stats.getTotalPacketsSent()), false);
        source.sendSuccess(() -> Component.literal("接收的包总数: " + stats.getTotalPacketsReceived()), false);
        source.sendSuccess(() -> Component.literal("发送的字节数: " + stats.getTotalBytesSent()), false);
        source.sendSuccess(() -> Component.literal("接收的字节数: " + stats.getTotalBytesReceived()), false);
        source.sendSuccess(() -> Component.literal("平均包大小: " + String.format("%.2f", stats.getAveragePacketSize())), false);
        source.sendSuccess(() -> Component.literal("=================="), false);
        
        return 1;
    }

    private static int showPlayerStats(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        if (target == null) {
            context.getSource().sendFailure(Component.literal("找不到目标玩家"));
            return 0;
        }
        
        CommandSourceStack source = context.getSource();
        
        try {

            
//            source.sendSuccess(() -> Component.literal("=== 玩家 " + target.getName().getString() + " 的网络统计 ===").withStyle(ChatFormatting.BOLD), false);
//            source.sendSuccess(() -> Component.literal("发送的包数量: " + playerStats.getTotalPacketsSent()), false);
//            source.sendSuccess(() -> Component.literal("接收的包数量: " + playerStats.getTotalPacketsReceived()), false);
//            source.sendSuccess(() -> Component.literal("发送的字节数: " + playerStats.getTotalBytesSent()), false);
//            source.sendSuccess(() -> Component.literal("接收的字节数: " + playerStats.getTotalBytesReceived()), false);
//            source.sendSuccess(() -> Component.literal("平均包大小: " + String.format("%.2f", playerStats.getAveragePacketSize())), false);
            source.sendSuccess(() -> Component.literal("=================="), false);
            
        } catch (Exception e) {
            source.sendFailure(Component.literal("无法获取玩家的网络统计信息"));
            return 0;
        }
        
        return 1;
    }
    
    // 显示按玩家统计信息
    private static int showStatsByPlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        NetworkStatistics stats = NetworkStatistics.getInstance();
        var allPlayerStats = stats.getAllPlayerPacketStats();
        
        source.sendSuccess(() -> Component.literal("=== 按玩家网络统计 ===").withStyle(ChatFormatting.BOLD), false);
        
        if (allPlayerStats.isEmpty()) {
            source.sendSuccess(() -> Component.literal("暂无按玩家统计信息"), false);
            return 1;
        }
        
        for (var entry : allPlayerStats.entrySet()) {
            String playerName = entry.getKey();
            var playerStats = entry.getValue();
            
            source.sendSuccess(() -> Component.literal("玩家: " + playerName).withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal("  发送包数: " + playerStats.packetsSent + ", 发送字节数: " + playerStats.bytesSent), false);
            source.sendSuccess(() -> Component.literal("  接收包数: " + playerStats.packetsReceived + ", 接收字节数: " + playerStats.bytesReceived), false);
            
            // 显示最常发送的包类型
            if (!playerStats.packetTypeStats.isEmpty()) {
                source.sendSuccess(() -> Component.literal("  包类型统计:"), false);
                for (var typeEntry : playerStats.packetTypeStats.entrySet()) {
                    var packetStats = typeEntry.getValue();
                    source.sendSuccess(() -> Component.literal("    " + typeEntry.getKey() + 
                            ": " + packetStats.count + " 包, " + packetStats.totalSize + " 字节"), false);
                }
            }
        }
        
        source.sendSuccess(() -> Component.literal("=================="), false);
        
        return 1;
    }
    
    // 显示所有包类型的排行
    private static int showRankings(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();
        
        NetworkStatistics stats = NetworkStatistics.getInstance();
        
        source.sendSuccess(() -> Component.literal("=== 按发送包数量排行 (前" + limit + "名) ===").withStyle(ChatFormatting.BOLD), false);
        
        // 显示服务器端发送的包排行
        List<String> serverCountRankings = stats.getTopPacketsByCount(limit, true);
        source.sendSuccess(() -> Component.literal("服务器发送包排行 (按数量):"), false);
        for (int i = 0; i < Math.min(limit, serverCountRankings.size()); i++) {
            String packetId = serverCountRankings.get(i);
            var packetStats = stats.getServerPacketStats(packetId);
            int finalI = i;
            source.sendSuccess(() -> Component.literal((finalI + 1) + ". " + packetId + ": " + packetStats.count + " 包"), false);
        }
        
        // 显示客户端发送的包排行
        List<String> clientCountRankings = stats.getTopPacketsByCount(limit, false);
        source.sendSuccess(() -> Component.literal("客户端发送包排行 (按数量):"), false);
        for (int i = 0; i < Math.min(limit, clientCountRankings.size()); i++) {
            String packetId = clientCountRankings.get(i);
            var packetStats = stats.getClientPacketStats(packetId);
            int finalI = i;
            source.sendSuccess(() -> Component.literal((finalI + 1) + ". " + packetId + ": " + packetStats.count + " 包"), false);
        }
        
        source.sendSuccess(() -> Component.literal("=================="), false);
        
        return 1;
    }
    
    // 显示服务器端包排行
    private static int showServerRankings(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();
        
        NetworkStatistics stats = NetworkStatistics.getInstance();
        
        source.sendSuccess(() -> Component.literal("=== 服务器包发送排行 (前" + limit + "名) ===").withStyle(ChatFormatting.BOLD), false);
        
        // 按数量排行
        List<String> countRankings = stats.getTopPacketsByCount(limit, true);
        source.sendSuccess(() -> Component.literal("按发送包数量排行:"), false);
        for (int i = 0; i < Math.min(limit, countRankings.size()); i++) {
            String packetId = countRankings.get(i);
            var packetStats = stats.getServerPacketStats(packetId);
            int finalI = i;
            source.sendSuccess(() -> Component.literal((finalI + 1) + ". " + packetId + ": " + packetStats.count + " 包"), false);
        }
        
        // 按字节数排行
        List<String> bytesRankings = stats.getTopPacketsByBytes(limit, true);
        source.sendSuccess(() -> Component.literal("按发送字节数排行:"), false);
        for (int i = 0; i < Math.min(limit, bytesRankings.size()); i++) {
            String packetId = bytesRankings.get(i);
            var packetStats = stats.getServerPacketStats(packetId);
            int finalI = i;
            source.sendSuccess(() -> Component.literal((finalI + 1) + ". " + packetId + ": " + packetStats.totalSize + " 字节"), false);
        }
        
        // 按平均包大小排行
        List<String> avgSizeRankings = stats.getTopPacketsByAvgSize(limit, true);
        source.sendSuccess(() -> Component.literal("按平均包大小排行:"), false);
        for (int i = 0; i < Math.min(limit, avgSizeRankings.size()); i++) {
            String packetId = avgSizeRankings.get(i);
            var packetStats = stats.getServerPacketStats(packetId);
            int finalI = i;
            source.sendSuccess(() -> Component.literal((finalI + 1) + ". " + packetId + ": " + String.format("%.2f", packetStats.getAverageSize()) + " 字节/包"), false);
        }
        
        source.sendSuccess(() -> Component.literal("=================="), false);
        
        return 1;
    }
    
    // 显示客户端包排行
    private static int showClientRankings(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();
        
        NetworkStatistics stats = NetworkStatistics.getInstance();
        
        source.sendSuccess(() -> Component.literal("=== 客户端包发送排行 (前" + limit + "名) ===").withStyle(ChatFormatting.BOLD), false);
        
        // 按数量排行
        List<String> countRankings = stats.getTopPacketsByCount(limit, false);
        source.sendSuccess(() -> Component.literal("按发送包数量排行:"), false);
        for (int i = 0; i < Math.min(limit, countRankings.size()); i++) {
            String packetId = countRankings.get(i);
            var packetStats = stats.getClientPacketStats(packetId);
            int finalI = i;
            source.sendSuccess(() -> Component.literal((finalI + 1) + ". " + packetId + ": " + packetStats.count + " 包"), false);
        }
        
        // 按字节数排行
        List<String> bytesRankings = stats.getTopPacketsByBytes(limit, false);
        source.sendSuccess(() -> Component.literal("按发送字节数排行:"), false);
        for (int i = 0; i < Math.min(limit, bytesRankings.size()); i++) {
            String packetId = bytesRankings.get(i);
            var packetStats = stats.getClientPacketStats(packetId);
            int finalI = i;
            source.sendSuccess(() -> Component.literal((finalI + 1) + ". " + packetId + ": " + packetStats.totalSize + " 字节"), false);
        }
        
        // 按平均包大小排行
        List<String> avgSizeRankings = stats.getTopPacketsByAvgSize(limit, false);
        source.sendSuccess(() -> Component.literal("按平均包大小排行:"), false);
        for (int i = 0; i < Math.min(limit, avgSizeRankings.size()); i++) {
            String packetId = avgSizeRankings.get(i);
            var packetStats = stats.getClientPacketStats(packetId);
            int finalI = i;
            source.sendSuccess(() -> Component.literal((finalI + 1) + ". " + packetId + ": " + String.format("%.2f", packetStats.getAverageSize()) + " 字节/包"), false);
        }
        
        source.sendSuccess(() -> Component.literal("=================="), false);
        
        return 1;
    }
}