package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.data.WaypointManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Random;

public class CreateWaypointCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:createpoint")
            .then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("path", StringArgumentType.greedyString())
                    .executes(context -> createWaypoint(
                        context, 
                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                        StringArgumentType.getString(context, "path")
                    ))
                )
            )
        );
    }

    private static int createWaypoint(CommandContext<CommandSourceStack> context, BlockPos pos, String pathName) {
        CommandSourceStack source = context.getSource();
        
        try {
            // 解析路径和名称
            String[] parts = pathName.split("/");
            if (parts.length < 2) {
                source.sendFailure(Component.literal("路径格式错误，请使用格式: path/name 或 更深层级的路径"));
                return 0;
            }
            
            // 最后一部分作为名称，其余部分作为路径
            String name = parts[parts.length - 1];
            String path = String.join("/", Arrays.copyOf(parts, parts.length - 1));
            
            // 生成随机颜色
            int color = generateRandomColor();
            
            // 获取路径点管理器并添加路径点
            WaypointManager manager = WaypointManager.get(source.getServer());
            manager.addWaypoint(path, name, pos, color);
            
            // 向玩家发送成功消息
            source.sendSuccess(() -> Component.literal("成功创建路径点: " + path + "/" + name + " 在位置 " + pos), false);
            
            // 同步到所有玩家
            SRE.NETWORKING.sendToAllPlayers(new io.wifi.starrailexpress.network.packet.SyncWaypointsPacket(manager.getAllWaypointsMap()));
            
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("创建路径点时出错: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int generateRandomColor() {
        Random random = new Random();
        return 0xFF000000 | random.nextInt(0xFFFFFF); // ARGB格式，确保不透明度为FF
    }
}