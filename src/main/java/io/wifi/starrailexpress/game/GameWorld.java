/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.FirecrackerEntity;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.tag.TMMBlockTags;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.utils.EntityClearUtils;

import java.util.ArrayList;
import java.util.List;

/** 地图位置、边界、实体清理、命令及世界时间查询。 */
final class GameWorld {
    private GameWorld() {
    }

    static void teleportBackToRoom(Player player) {
        if (player == null || GameUtils.roomToPlayer.isEmpty()) return;
        player.stopRiding();
        player.stopSleeping();
        var areas = AreasWorldComponent.KEY.get(player.level());
        Vec3 pos = GameUtils.getSpawnPos(areas, GameUtils.roomToPlayer.getOrDefault(player.getUUID(), 0));
        if (pos != null) {
            player.teleportTo(pos.x(), pos.y() + 1, pos.z());
        } else if (player instanceof ServerPlayer sp) {
            sp.setGameMode(GameType.SPECTATOR);
        }
    }

    /** 多个可用房间时排除自己的房间；没有配置锚点时仍回退旁观。 */
    static void teleportToRandomRoom(Player player) {
        if (player == null) return;
        var areas = AreasWorldComponent.KEY.get(player.level());
        if (areas == null) return;
        List<Integer> rooms = new ArrayList<>();
        for (int room = 1; room <= areas.getRoomCount(); room++) {
            if (GameUtils.getSpawnPos(areas, room) != null) rooms.add(room);
        }
        if (rooms.isEmpty()) {
            if (player instanceof ServerPlayer sp) sp.setGameMode(GameType.SPECTATOR);
            return;
        }
        if (rooms.size() > 1) rooms.remove((Integer) GameUtils.roomToPlayer.getOrDefault(player.getUUID(), 0));
        int picked = rooms.get(player.getRandom().nextInt(rooms.size()));
        Vec3 pos = GameUtils.getSpawnPos(areas, picked);
        if (pos != null) player.teleportTo(pos.x(), pos.y() + 1, pos.z());
    }

    static Vec3 getSpawnPos(AreasWorldComponent areas, int room) {
        // 未配置锚点仍返回 null，不恢复早已停用的硬编码坐标。
        return areas.getRoomPosition(room);
    }

    static void limitPlayerToBox(ServerPlayer player, AABB box) {
        Vec3 playerPos = player.position();
        Vec3 teleportPos = playerPos;
        if (!box.contains(playerPos)) {
            double x = playerPos.x();
            double y = playerPos.y();
            double z = playerPos.z();
            double bounceFactor = 0.2;
            // 保留 Z、Y、X 的反弹处理顺序和原有容差。
            if (z < box.minZ - 50) {
                z = box.minZ - 50;
                player.setDeltaMovement(player.getDeltaMovement().add(0, 0, bounceFactor));
            }
            if (z > box.maxZ + 50) {
                z = box.maxZ + 50;
                player.setDeltaMovement(player.getDeltaMovement().add(0, 0, -bounceFactor));
            }
            if (y < box.minY - 20) {
                y = box.minY - 20;
                player.setDeltaMovement(player.getDeltaMovement().add(0, bounceFactor, 0));
            }
            if (y > box.maxY + 20) {
                y = box.maxY + 20;
                player.setDeltaMovement(player.getDeltaMovement().add(0, -bounceFactor, 0));
            }
            if (x < box.minX - 50) {
                x = box.minX - 50;
                player.setDeltaMovement(player.getDeltaMovement().add(bounceFactor, 0, 0));
            }
            if (x > box.maxX + 50) {
                x = box.maxX + 50;
                player.setDeltaMovement(player.getDeltaMovement().add(-bounceFactor, 0, 0));
            }
            teleportPos = new Vec3(x, y, z);
        }
        if (!teleportPos.equals(playerPos)) player.teleportTo(teleportPos.x(), teleportPos.y(), teleportPos.z());
    }

    static void resetEntities(ServerLevel world) {
        for (PlayerBodyEntity body : world.getEntities(TMMEntities.PLAYER_BODY, entity -> true)) body.discard();
        for (ItemEntity item : world.getEntities(EntityType.ITEM, entity -> true)) item.discard();
        for (FirecrackerEntity entity : world.getEntities(TMMEntities.FIRECRACKER, item -> true)) entity.discard();
        for (NoteEntity entity : world.getEntities(TMMEntities.NOTE, item -> true)) entity.discard();
        EntityClearUtils.clearAllEntities(world);
    }

    static boolean isTmmDoorBlock(Block block) {
        return block == TMMBlocks.SMALL_GLASS_DOOR || block == TMMBlocks.SMALL_WOOD_DOOR
                || block == TMMBlocks.ANTHRACITE_STEEL_DOOR || block == TMMBlocks.KHAKI_STEEL_DOOR
                || block == TMMBlocks.MAROON_STEEL_DOOR || block == TMMBlocks.MUNTZ_STEEL_DOOR
                || block == TMMBlocks.NAVY_STEEL_DOOR || block == TMMBlocks.METAL_SHEET_DOOR
                || block == TMMBlocks.COCKPIT_DOOR;
    }

    static void executeCommand(CommandSourceStack source, String command) {
        try {
            source.getServer().getCommands().performPrefixedCommand(source, command);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute: " + command + ", error: " + e.getMessage());
        }
    }

    static void executeFunction(MinecraftServer server, int permission, String function) {
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(permission),
                    "function " + function);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute function: " + function + ", error: " + e.getMessage());
        }
    }

    static void executeFunction(CommandSourceStack source, String function) {
        try {
            source.getServer().getCommands().performPrefixedCommand(source, "function " + function);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute function: " + function + ", error: " + e.getMessage());
        }
    }

    static boolean isGameRunning(Player player) {
        return player != null && isGameRunning(player.level());
    }

    static boolean isGameRunning(Level level) {
        return level != null && SREGameWorldComponent.KEY.get(level).isRunning();
    }

    static boolean isTimeFrozen(MinecraftServer server) {
        return SREGameTimeComponent.KEY.get(server.overworld()).isTimeFrozen();
    }

    static boolean isTimeFrozen(Level world) {
        return SREGameTimeComponent.KEY.get(world).isTimeFrozen();
    }

    static long getTicksFromGameStart(Level world) {
        return SREGameTimeComponent.KEY.get(world).getTicksFromGameStart();
    }

    static boolean isPlayerInLobby(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        Vec3 pos = player.position();
        BlockPos spawn = world.getSharedSpawnPos();
        return spawn.getX() - 200 < pos.x && pos.x < spawn.getX() + 200
                && spawn.getZ() - 200 < pos.z && pos.z < spawn.getZ() + 200;
    }

    static boolean isUnderSprinklers(Player player) {
        for (int y = 0; y < 4; y++) {
            if (player.level().getBlockState(player.blockPosition().above(y)).is(TMMBlockTags.SPRINKLERS)) return true;
        }
        return false;
    }
}
