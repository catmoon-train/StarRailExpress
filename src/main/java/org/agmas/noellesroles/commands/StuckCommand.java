/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import net.minecraft.world.item.Items;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.utils.StuckHelperUtils;

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
            
            if (player.getCooldowns().isOnCooldown(Items.STRUCTURE_VOID)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.commands.stuck.cooldown","/stuck")
                                .withStyle(ChatFormatting.RED),
                        true);
                return 0;
            }
            if (StuckHelperUtils.isPlayerStuck(player)) {

                // var playerInBlockPos2 = player.blockPosition();
                // var blockState2 = level.getBlockState(playerInBlockPos2);
                {
                    player.teleportTo(player.getX(), player.getY() + 1, player.getZ());

                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.commands.stuck.success")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }
                
                if (!player.isCreative())
                    player.getCooldowns().addCooldown(Items.STRUCTURE_VOID, 100);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.commands.stuck.failed_no_stuck")
                                .withStyle(ChatFormatting.RED),
                        true);
                        
                if (!player.isCreative())
                    player.getCooldowns().addCooldown(Items.STRUCTURE_VOID, 100);
                return 0;
            }
            return 1;
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[LootSys] Failed to send checkPacket\n", e);
            return 0;
        }
    }
}