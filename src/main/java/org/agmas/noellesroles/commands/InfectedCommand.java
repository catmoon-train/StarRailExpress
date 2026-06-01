package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 疫使测试指令
 * 用法: /sre:infected <player> <tick>
 * 使指定玩家进入感染状态
 */
public class InfectedCommand {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((commandDispatcher, registryAccess, environment) -> {
            commandDispatcher.register(
                Commands.literal("sre:infected")
                    .requires(source -> source.hasPermission(2)) // 需要管理员权限
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("tick", IntegerArgumentType.integer(0))
                            .executes(InfectedCommand::run)
                        )
                    )
            );
        });
    }
    
    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var target = EntityArgument.getPlayer(context, "player");
        int tick = IntegerArgumentType.getInteger(context, "tick");
        
        if (!source.getServer().isRunning()) {
            return 0;
        }
        
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(source.getLevel());
        
        // 检查游戏状态
        if (!gameWorld.gameStatus.equals(io.wifi.starrailexpress.cca.SREGameWorldComponent.GameStatus.ACTIVE)) {
            source.sendFailure(Component.literal("游戏未运行！"));
            return 0;
        }
        
        // 检查玩家是否存活
        if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(target)) {
            source.sendFailure(Component.literal("玩家已死亡！"));
            return 0;
        }
        
        // 检查是否是疫使自己
        if (gameWorld.isRole(target, ModRoles.INFECTED)) {
            source.sendFailure(Component.literal("疫使不能被感染！"));
            return 0;
        }
        
        // 设置感染状态
        InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(target);
        if (tick > 0) {
            infectedComponent.infectedTicks = tick;
            infectedComponent.infector = source.getPlayer() != null ? 
                source.getPlayer().getUUID() : target.getUUID();
            infectedComponent.sync_with_all();
            source.sendSuccess(() -> Component.literal(
                String.format("已将 %s 设置为感染状态（剩余 %d tick）", target.getName().getString(), tick)), true);
        } else {
            infectedComponent.cure();
            source.sendSuccess(() -> Component.literal(
                "已治愈 " + target.getName().getString() + " 的感染状态"), true);
        }
        
        return 1;
    }
}
