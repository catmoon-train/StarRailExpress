package org.agmas.noellesroles.commands;

import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.GameStatus;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.DeathPenaltyComponent;

public class StuckCommand {
    public static void register() {
        // 注册管理员命令
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    dispatcher.register(Commands.literal("stuck")
                            .executes(StuckCommand::stuckDeal));
                });
    }

    protected static int stuckDeal(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayer();
            if (player == null)
                return 0;
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
            if (player.isSpectator()) {
                if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                    if (DeathPenaltyComponent.hasPenalty(player)) {
                        return 0;
                    }
                    AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
                    AreasWorldComponent.PosWithOrientation spectatorSpawnPos = areas.getSpectatorSpawnPos();
                    player.teleportTo(level, spectatorSpawnPos.pos.x(), spectatorSpawnPos.pos.y(),
                            spectatorSpawnPos.pos.z(), spectatorSpawnPos.yaw, spectatorSpawnPos.pitch);
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.commands.stuck.success")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }
                return 0;
            }
            if (checkPos(level, player, player.getBoundingBox())) {

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

    private static boolean checkPos(ServerLevel level, ServerPlayer player, AABB area) {
        return (level.noBlockCollision(player, area));
    }
}