package org.agmas.noellesroles.commands;

import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.GameStatus;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.Noellesroles;

public class StuckCommand {
    public static void register() {
        // 注册管理员命令
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    dispatcher.register(Commands.literal("stuck")
                            .executes(StuckCommand::stuckDeal));
                });
    }

    /** 打开抽奖界面 */
    public static boolean checkBlock(ServerLevel level, BlockState blockState, BlockPos blockPos) {
        return blockState.isSolidRender(level, blockPos) &&
                blockState.isCollisionShapeFullBlock(level, blockPos);
    }

    protected static int stuckDeal(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayer();
            ServerLevel level = context.getSource().getLevel();
            if (SREGameWorldComponent.KEY.get(level).getGameStatus() == GameStatus.INACTIVE) {
                BlockPos spawn = level.getSharedSpawnPos();
                float angle = level.getSharedSpawnAngle();
                player.teleportTo(level, spawn.getX(), spawn.getY(),
                        spawn.getZ(), angle, 0);
                if (!player.isCreative())
                    player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.commands.stuck.success")
                                .withStyle(ChatFormatting.GREEN),
                        true);

                return 1;
            }
            if (player == null)
                return 0;
            var playerInBlockPos = player.blockPosition();
            var blockState = level.getBlockState(playerInBlockPos);
            if (checkBlock(level, blockState, playerInBlockPos)) {

                // var playerInBlockPos2 = player.blockPosition();
                // var blockState2 = level.getBlockState(playerInBlockPos2);
                {
                    player.teleportTo(player.getX(), player.getY() + 1, player.getZ());

                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.commands.stuck.success")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }
            } else {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.commands.stuck.failed_no_stuck")
                                .withStyle(ChatFormatting.RED),
                        true);
                return 0;
            }
            return 1;
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[LootSys] Failed to send checkPacket\n", e);
            return 0;
        }
    }
}