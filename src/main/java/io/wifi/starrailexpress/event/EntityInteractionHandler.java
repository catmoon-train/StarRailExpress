package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.command.EntityDataCommand;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class EntityInteractionHandler {
    /**
     * 替换占位符
     */
    private static String replacePlaceholders(String customData, Player player, Entity entity) {
        // %target - 目标实体UUID
        customData = customData.replaceAll("%target", entity.getUUID().toString());
        
        // %player - 玩家UUID
        customData = customData.replaceAll("%player", player.getUUID().toString());
        
        // %player_name - 玩家名称
        customData = customData.replaceAll("%name_player", player.getName().getString());
        
        // %x, %y, %z - 目标实体坐标
        customData = customData.replaceAll("%x", String.valueOf((int) entity.getX()));
        customData = customData.replaceAll("%y", String.valueOf((int) entity.getY()));
        customData = customData.replaceAll("%z", String.valueOf((int) entity.getZ()));
        
        // %player_x, %player_y, %player_z - 玩家坐标
        customData = customData.replaceAll("%player_x", String.valueOf((int) player.getX()));
        customData = customData.replaceAll("%player_y", String.valueOf((int) player.getY()));
        customData = customData.replaceAll("%player_z", String.valueOf((int) player.getZ()));
        
        // %world - 世界名称
        customData = customData.replaceAll("%world", entity.level().dimension().location().toString());
        
        // %distance - 玩家与目标实体之间的距离
        double distance = player.distanceTo(entity);
        customData = customData.replaceAll("%distance", String.valueOf((int) distance));
        
        return customData;
    }
    
    public static void register() {
        // 注册右键点击实体的事件
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hitResult==null){
                return InteractionResult.PASS;
            }
                if (world.isClientSide) {
                return InteractionResult.PASS;
            }
            if (!hand.equals(InteractionHand.OFF_HAND) && hitResult != null) {
                if (hitResult.getEntity() != null) {

                    // 调用角色的右键点击实体方法
                    io.wifi.starrailexpress.api.RoleMethodDispatcher.callRightClickEntity(player, hitResult.getEntity());

                // 获取实体上的自定义数据
                String customData = entity.getAttached(EntityDataCommand.ENTITY_CUSTOM_DATA_COMMAND);
                    if (customData != null) {
                        customData = replacePlaceholders(customData, player, entity);
                    }

                    if (customData != null && !customData.isEmpty()) {
                    // 如果实体有自定义数据，执行指定的函数
                    executeCustomFunction(player, entity, customData);
                    return InteractionResult.SUCCESS;
                }
            }
                }
                return InteractionResult.PASS;

        });
    }

    /**
     * 根据自定义数据执行相应的函数
     */
    private static void executeCustomFunction(Player player, Entity entity, String customData) {
        if (customData.isEmpty())return;
        // 这里可以根据自定义数据执行不同的功能
        // 例如，解析数据并执行相应操作
        if (player.isCreative()) {
            player.sendSystemMessage(Component.literal("执行自定义指令: " + customData));
        }
        executeCommand(player, entity, customData);
//        // 示例：根据数据内容执行不同操作
//        if (customData.startsWith("command:")) {
//            // 执行命令
//            String command = customData.substring(8); // 移除 "command:" 前缀
//
//        } else if (customData.startsWith("message:")) {
//            // 发送消息
//            String message = customData.substring(8); // 移除 "message:" 前缀
//            player.sendSystemMessage(Component.literal(message));
//        } else if (customData.startsWith("teleport:")) {
//            // 执行传送
//            handleTeleport(player, entity, customData.substring(11)); // 移除 "teleport:" 前缀
//        } else {
//            // 默认行为：显示自定义数据
//            player.sendSystemMessage(Component.literal("实体数据: " + customData));
//        }
    }

    /**
     * 执行命令
     */
    private static void executeCommand(Player player, Entity entity, String command) {
        // 在这里执行命令
        // 注意：出于安全考虑，我们不直接执行任意命令
        // 可以实现特定的安全命令执行逻辑
//        player.sendSystemMessage(Component.literal("执行命令: " + command));
        player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
        

    }

    /**
     * 处理传送逻辑
     */
    private static void handleTeleport(Player player, Entity entity, String teleportData) {
        // 解析传送坐标或其他传送参数
        player.sendSystemMessage(Component.literal("传送功能: " + teleportData));
        // 实际传送逻辑可以根据teleportData参数实现
    }
}