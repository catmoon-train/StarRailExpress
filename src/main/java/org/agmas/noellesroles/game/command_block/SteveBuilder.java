package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.agmas.noellesroles.game.roles.innocence.builder.BuilderWallPositions;
import org.agmas.noellesroles.packet.BuilderRemoveWallS2CPacket;
import org.agmas.noellesroles.packet.BuilderWallS2CPacket;
import org.agmas.noellesroles.role_data.innocence.BuilderRoleData;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Steve 的建造技能：和建筑师一样立一堵客户端墙，冷却 45 秒。
 */
public final class SteveBuilder {
    public static final int COOLDOWN = 45 * 20;
    public static final int DURATION = BuilderRoleData.WALL_DURATION;
    private static final List<Wall> WALLS = new ArrayList<>();

    private SteveBuilder() {
    }

    public static void clear() {
        for (Wall wall : WALLS) {
            BuilderWallPositions.removeWall(wall.positions);
        }
        WALLS.clear();
    }

    public static void tick(ServerLevel level) {
        if (WALLS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        WALLS.removeIf(wall -> {
            if (now < wall.until) {
                return false;
            }
            BuilderWallPositions.removeWall(wall.positions);
            BuilderRemoveWallS2CPacket packet = new BuilderRemoveWallS2CPacket(wall.id);
            for (ServerPlayer player : level.players()) {
                ServerPlayNetworking.send(player, packet);
            }
            return true;
        });
    }

    public static boolean use(Player player) {
        if (!(player instanceof ServerPlayer server) || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!SteveMiner.canAct(player)) {
            return false;
        }
        long now = level.getGameTime();
        List<BlockPos> wall = wall(server);
        int minY = wall.stream().mapToInt(BlockPos::getY).min().orElse(0);
        List<BlockPos> bricks = new ArrayList<>();
        List<BlockPos> webs = new ArrayList<>();
        for (BlockPos pos : wall) {
            if (pos.getY() == minY + 2) {
                webs.add(pos);
            } else {
                bricks.add(pos);
            }
        }
        Set<BlockPos> positions = new HashSet<>(wall);
        BuilderWallPositions.addWall(positions);
        UUID id = UUID.randomUUID();
        WALLS.add(new Wall(id, positions, now + DURATION));
        BuilderWallS2CPacket packet = new BuilderWallS2CPacket(id, bricks, webs, DURATION);
        for (ServerPlayer other : level.players()) {
            ServerPlayNetworking.send(other, packet);
        }
        level.playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS,
                1.0F, 1.0F);
        server.displayClientMessage(Component.translatable("hud.noellesroles.builder.wall_built")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    private static List<BlockPos> wall(Player player) {
        List<BlockPos> positions = new ArrayList<>();
        Direction facing = player.getDirection();
        BlockPos base = player.blockPosition().relative(facing);
        Direction extend = facing.getCounterClockWise();
        for (int length = 0; length < BuilderRoleData.WALL_LENGTH; length++) {
            for (int height = 0; height < BuilderRoleData.WALL_HEIGHT; height++) {
                positions.add(base.relative(extend, length - BuilderRoleData.WALL_LENGTH / 2).above(height));
            }
        }
        return positions;
    }

    private record Wall(UUID id, Set<BlockPos> positions, long until) {
    }
}
