package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.Loot.LootPoolsInfoCheckS2CPacket;
import org.agmas.noellesroles.utils.lottery.LotteryManager;

import java.util.Collection;

/**
 * 抽奖命令
 * 玩家调用抽奖命令，服务器进行抽奖并向玩家返回结果
 * 玩家根据结果包播放相关动画
 */
public class LootCommand {
    public static void register() {
        // 注册管理员命令
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    dispatcher.register(Commands.literal("Loot:LootUI")
                            .executes(LootCommand::openLootScreen)
                    );
        });
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    dispatcher.register(Commands.literal("Loot:SetData")
                            .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("AddOrDegreeChance")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("chance", IntegerArgumentType.integer())
                                                        .executes(LootCommand::addOrDegreeChance))))
                    );
                });
    }

    /** 打开抽奖界面*/
    protected static int openLootScreen(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null)
                return 0;
            ServerPlayNetworking.send(player, new LootPoolsInfoCheckS2CPacket(
                    LotteryManager.getInstance().getPoolIDs()
            ));
            return 1;
        }
        catch (Exception e) {
            Noellesroles.LOGGER.error("[LootSys] Failed to send checkPacket\n", e);
            return 0;
        }
    }
    protected static int addOrDegreeChance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        for(ServerPlayer player : players)
        {
            LotteryManager.getInstance().addOrDegreeLotteryChance(player, IntegerArgumentType.getInteger(context, "chance"));
        }
        return 1;
    }
}